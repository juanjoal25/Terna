import { getApps } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { beforeAll, beforeEach, describe, expect, it } from "vitest";
import { availableActions, hiddenEndIndex, seededRng, type Action, type GameState, type Rng } from "../engine/index.js";
import { fixedGame } from "../engine/test-utils.js";
import { gameAction, PAUSE_MS, startMatch, timeoutTurn } from "../match/matchService.js";
import type { MatchNode, StoredMatch } from "../match/types.js";
import { createRoom, getActive, joinRoom, leaveRoom } from "../rooms/roomService.js";
import type { OpenRoomEntry, RoomData } from "../rooms/types.js";
import { createTestEnv, read, reasonOf, type TestEnv } from "./harness.js";

let env: TestEnv;
beforeAll(() => {
  env = createTestEnv();
});
beforeEach(async () => {
  await env.reset();
  Object.assign(env.names, { ana: "Ana", beto: "Beto", carla: "Carla", dani: "Dani", eli: "Eli", fer: "Fer" });
});

const USERS = ["ana", "beto", "carla", "dani", "eli", "fer"];

/** Crea una sala pública con `players` jugadores (el primero es anfitrión) y empieza la partida. */
async function startedMatch(players: number, isPrivate = false) {
  const uids = USERS.slice(0, players);
  const { roomId } = await createRoom(env.deps, uids[0]!, { maxPlayers: 6, isPrivate });
  for (const uid of uids.slice(1)) await joinRoom(env.deps, uid, { roomId });
  const { matchId } = await startMatch(env.deps, uids[0]!);
  return { uids, roomId, matchId };
}

const matchNode = (matchId: string) => read<MatchNode>(env.deps, `matches/${matchId}`);
const storedOf = async (matchId: string) => JSON.parse((await matchNode(matchId))!.secret.state) as StoredMatch;

/** Reemplaza la partida por una armada a mano, para probar reglas concretas con cartas conocidas. */
async function setGame(matchId: string, game: GameState) {
  const stored = await storedOf(matchId);
  const next: StoredMatch = { ...stored, game, timing: { startsAt: 0, deadline: 0 } };
  await env.deps.db.ref(`matches/${matchId}/secret/state`).set(JSON.stringify(next));
}

const act = (matchId: string, uid: string, expectedSeq: number, action: object) => gameAction(env.deps, uid, { matchId, expectedSeq, action });

describe("empezar partida", () => {
  it.each([
    [3, 9, 9],
    [4, 7, 8],
    [5, 6, 6],
    [6, 5, 6],
  ])("con %i jugadores reparte %i cartas a cada uno y deja %i en el centro", async (n, hand, center) => {
    const { uids, matchId } = await startedMatch(n);
    const node = (await matchNode(matchId))!;
    const stored = await storedOf(matchId);

    for (const uid of uids) {
      expect(node.players[uid]!.handCount).toBe(hand);
      expect(node.hands![uid]).toHaveLength(hand);
      expect(node.hands![uid]).toEqual([...node.hands![uid]!].sort((a, b) => a - b));
    }
    expect(node.live.center).toEqual(Array(center).fill(true));
    expect(stored.game.center).toHaveLength(center);

    // Se conservan las 36 cartas: tres copias de cada número.
    const all = [...uids.flatMap((u) => node.hands![u]!), ...stored.game.center.filter((c): c is number => c !== null)];
    for (let v = 1; v <= 12; v++) expect(all.filter((c) => c === v)).toHaveLength(3);
  });

  it("publica el estado inicial: turno, jugadores, sala en partida y punteros de reconexión", async () => {
    const { uids, roomId, matchId } = await startedMatch(3);
    const node = (await matchNode(matchId))!;

    expect(node.meta).toMatchObject({ roomId, ruleSet: "TERNA_CLASSIC", status: "PLAYING", turnSeconds: 20 });
    expect(uids).toContain((node.live.turn as { player: string }).player);
    expect((node.live.lastEvents as { type: string }[])[0]!.type).toBe("TURN_STARTED");
    expect(node.players.ana).toMatchObject({ seat: 0, name: "Ana", isBot: false, connected: true });
    expect((node.live.turn as { startsAt: number }).startsAt).toBe(env.clock.now + PAUSE_MS.gameIntro);

    const room = await read<RoomData>(env.deps, `rooms/${roomId}`);
    expect(room).toMatchObject({ status: "IN_MATCH", currentMatchId: matchId });
    expect(await read(env.deps, `openRooms/${roomId}`)).toBeNull();
    for (const uid of uids) expect(await getActive(env.deps.db, uid)).toEqual({ roomId, matchId });
  });

  it("las ramas públicas no contienen cartas ocultas: solo el conteo de manos y huecos del centro", async () => {
    const { matchId } = await startedMatch(4);
    const node = (await matchNode(matchId))!;
    const publicPart = JSON.stringify({ meta: node.meta, players: node.players, live: node.live });
    expect(publicPart).not.toContain("secret");
    expect(publicPart).not.toContain("hands");
    expect((node.live.center as unknown[]).every((c) => typeof c === "boolean")).toBe(true);
  });

  it("solo el anfitrión inicia, y con el mínimo de jugadores", async () => {
    const { roomId } = await createRoom(env.deps, "ana", { maxPlayers: 6, isPrivate: false });
    await joinRoom(env.deps, "beto", { roomId });
    expect(await reasonOf(startMatch(env.deps, "ana"))).toBe("NOT_ENOUGH_PLAYERS");
    await joinRoom(env.deps, "carla", { roomId });
    expect(await reasonOf(startMatch(env.deps, "beto"))).toBe("NOT_HOST");
    expect(await reasonOf(startMatch(env.deps, "dani"))).toBe("NOT_IN_ROOM");
    expect((await read<RoomData>(env.deps, `rooms/${roomId}`))?.status).toBe("WAITING");
    expect(await read(env.deps, "matches")).toBeNull();
  });

  it("pulsar 'Iniciar' dos veces a la vez crea una sola partida", async () => {
    const { roomId } = await createRoom(env.deps, "ana", { maxPlayers: 6, isPrivate: false });
    for (const uid of ["beto", "carla"]) await joinRoom(env.deps, uid, { roomId });

    const results = await Promise.allSettled([startMatch(env.deps, "ana"), startMatch(env.deps, "ana")]);
    expect(results.filter((r) => r.status === "fulfilled")).toHaveLength(1);
    expect(Object.keys((await read<Record<string, unknown>>(env.deps, "matches"))!)).toHaveLength(1);
  });

  it("con la partida en curso nadie entra ni sale de la sala", async () => {
    const { roomId } = await startedMatch(3);
    expect(await reasonOf(joinRoom(env.deps, "dani", { roomId }))).toBe("ROOM_STARTED");
    expect(await reasonOf(leaveRoom(env.deps, "carla"))).toBe("ROOM_IN_MATCH");
    expect(await reasonOf(startMatch(env.deps, "ana"))).toBe("ROOM_STARTED");
  });
});

describe("jugar", () => {
  /** ana (primera), beto y carla con cartas conocidas. */
  async function scripted(overrides: Partial<Parameters<typeof fixedGame>[0]> = {}) {
    const started = await startedMatch(3);
    const game = fixedGame({
      hands: { ana: [1, 8], beto: [1, 9], carla: [4, 6] },
      center: [1, 2],
      first: "ana",
      ...overrides,
    });
    await setGame(started.matchId, game);
    return { ...started, seq: game.seq };
  }

  it("forma una terna con cartas de otro jugador, del centro y propias, y actualiza manos y turno", async () => {
    const { matchId, seq } = await scripted();

    const r1 = await act(matchId, "ana", seq, { type: "REVEAL_HAND", target: "beto", end: "LOW" });
    expect(r1.finished).toBe(false);
    const r2 = await act(matchId, "ana", r1.seq, { type: "REVEAL_CENTER", slot: 0 });

    const node = (await matchNode(matchId))!;
    expect(node.hands!.ana).toEqual([8]);
    expect(node.hands!.beto).toEqual([9]);
    expect(node.hands!.carla).toEqual([4, 6]);
    expect(node.players.ana).toMatchObject({ handCount: 1, sets: [1] });
    expect(node.players.beto!.handCount).toBe(1);
    expect(node.live.center).toEqual([false, true]);
    expect((node.live.turn as { player: string }).player).toBe("beto");
    expect((node.live.lastEvents as { type: string }[]).map((e) => e.type)).toEqual(["CARD_REVEALED", "CARD_REVEALED", "SET_COMPLETED", "TURN_STARTED"]);
    expect(node.live.seq).toBe(r2.seq);
    expect(node.live.revealed ?? []).toEqual([]);
  });

  it("un fallo avisa a todos sin mostrar la carta equivocada y obliga a esperar la pausa antes de que juegue el siguiente", async () => {
    const { matchId, seq } = await scripted();
    const r1 = await act(matchId, "ana", seq, { type: "REVEAL_HAND", target: "beto", end: "LOW" });
    const r2 = await act(matchId, "ana", r1.seq, { type: "REVEAL_CENTER", slot: 1 }); // un 2: no coincide

    const node = (await matchNode(matchId))!;
    const events = node.live.lastEvents as { type: string; revealed?: { value: number }[]; mismatch?: unknown }[];
    expect(events.map((e) => e.type)).toEqual(["TURN_FAILED", "TURN_STARTED"]); // la carta equivocada no genera CARD_REVEALED
    // Solo se ve la carta que sí coincidía (el 1 de beto); la del centro que no coincidía (un 2) no se publica.
    expect(events[0]!.revealed!.map((c) => c.value)).toEqual([1]);
    expect(events[0]!.mismatch).toEqual({ source: "CENTER", slot: 1 });
    expect(JSON.stringify(node.live)).not.toContain('"value":2');
    expect(node.hands!.beto).toEqual([1, 9]); // las cartas volvieron a su sitio
    expect((node.live.turn as { player: string; startsAt: number }).player).toBe("beto");
    expect((node.live.turn as { startsAt: number }).startsAt).toBe(env.clock.now + PAUSE_MS.turnFailed);

    const first = { type: "REVEAL_CENTER", slot: 0 };
    expect(await reasonOf(act(matchId, "beto", r2.seq, first))).toBe("TOO_EARLY");
    env.clock.advance(PAUSE_MS.turnFailed);
    await act(matchId, "beto", r2.seq, first);
  });

  it("deducir mal deja el mensaje '-1000 de aura' para todos y pierde el turno", async () => {
    const { matchId, seq } = await scripted();
    await act(matchId, "ana", seq, { type: "CLAIM", target: "carla", value: 5 });

    const events = (await matchNode(matchId))!.live.lastEvents as { type: string; taunt?: string; target?: string; value?: number }[];
    expect(events.map((e) => e.type)).toEqual(["CLAIM_FAILED", "TURN_FAILED", "TURN_STARTED"]);
    expect(events[0]).toMatchObject({ taunt: "AURA_MINUS_1000", target: "carla", value: 5 });
  });

  it("'Preguntar' solo una vez por turno (`live.askUsed`), pero se puede seguir pidiendo cartas y revelando el centro", async () => {
    const { matchId, seq } = await scripted();
    const r1 = await act(matchId, "ana", seq, { type: "REVEAL_HAND", target: "beto", end: "LOW" });
    expect((await matchNode(matchId))!.live.askUsed).toBe(true);
    expect(await reasonOf(act(matchId, "ana", r1.seq, { type: "REVEAL_HAND", target: "carla", end: "LOW" }))).toBe("ASK_ALREADY_USED");
    // Revelar el centro (y pedir carta) no tienen límite.
    await expect(act(matchId, "ana", r1.seq, { type: "REVEAL_CENTER", slot: 0 })).resolves.toBeTruthy();
  });

  it("deducir bien entrega la carta y sigue el turno", async () => {
    const { matchId, seq } = await scripted();
    await act(matchId, "ana", seq, { type: "CLAIM", target: "carla", value: 4 });
    const node = (await matchNode(matchId))!;
    expect(node.live.revealed).toEqual([{ source: "HAND", owner: "carla", end: "LOW", value: 4, claimed: true }]);
    expect((node.live.turn as { player: string }).player).toBe("ana");
  });

  it("rechaza jugadas inválidas sin cambiar nada", async () => {
    const { matchId, seq } = await scripted();
    const before = JSON.stringify(await matchNode(matchId));

    expect(await reasonOf(act(matchId, "beto", seq, { type: "REVEAL_CENTER", slot: 0 }))).toBe("NOT_YOUR_TURN");
    expect(await reasonOf(act(matchId, "dani", seq, { type: "REVEAL_CENTER", slot: 0 }))).toBe("NOT_A_PLAYER");
    expect(await reasonOf(act(matchId, "ana", seq + 5, { type: "REVEAL_CENTER", slot: 0 }))).toBe("STALE_STATE");
    expect(await reasonOf(act(matchId, "ana", seq, { type: "REVEAL_CENTER", slot: 9 }))).toBe("INVALID_SLOT");
    expect(await reasonOf(act(matchId, "ana", seq, { type: "REVEAL_HAND", target: "nadie", end: "LOW" }))).toBe("UNKNOWN_PLAYER");
    expect(await reasonOf(act(matchId, "ana", seq, { type: "PASS" }))).toBe("PASS_TOO_EARLY");
    expect(await reasonOf(act(matchId, "ana", seq, { type: "HACER_TRAMPA" }))).toBe("INVALID_ACTION");
    expect(await reasonOf(act(matchId, "ana", seq, { type: "REVEAL_HAND", target: "beto", end: "MEDIO" }))).toBe("INVALID_ACTION");
    expect(await reasonOf(act("no-existe", "ana", seq, { type: "PASS" }))).toBe("MATCH_NOT_FOUND");
    expect(await reasonOf(gameAction(env.deps, "ana", { matchId, action: { type: "PASS" } }))).toBe("INVALID_ARGUMENT");

    expect(JSON.stringify(await matchNode(matchId))).toBe(before);
  });

  it("el jugador sale de la sesión: no se puede jugar 'como otro' mandando un campo `player`", async () => {
    const { matchId, seq } = await scripted();
    // beto manda una jugada diciendo que es ana: se ignora el campo y se juega como beto.
    expect(await reasonOf(act(matchId, "beto", seq, { type: "REVEAL_CENTER", slot: 0, player: "ana" }))).toBe("NOT_YOUR_TURN");
  });

  it("si dos jugadas llegan a la vez con el mismo estado, solo una se aplica", async () => {
    const { matchId, seq } = await scripted();
    const tap = { type: "REVEAL_CENTER", slot: 0 };
    const results = await Promise.allSettled([act(matchId, "ana", seq, tap), act(matchId, "ana", seq, tap)]);

    expect(results.filter((r) => r.status === "fulfilled")).toHaveLength(1);
    const rejected = results.find((r): r is PromiseRejectedResult => r.status === "rejected")!;
    expect(rejected.reason.reason).toBe("STALE_STATE");
    expect((await storedOf(matchId)).game.turn.revealed).toHaveLength(1);
  });

  it("al ganar con la terna de 7: termina la partida, libera la sala y guarda historial y estadísticas", async () => {
    const started = await startedMatch(3);
    const { matchId, roomId, uids } = started;
    const firestore = getFirestore(getApps()[0]!);
    for (const uid of uids) {
      await firestore.doc(`users/${uid}`).set({ displayName: env.names[uid], avatar: "jaguar_01", anonymous: true, level: 1, xp: 0, coins: 0 });
    }
    const game = fixedGame({ hands: { ana: [7, 12], beto: [7, 11], carla: [7, 10] }, center: [3], first: "ana" });
    await setGame(matchId, game);

    const r1 = await act(matchId, "ana", game.seq, { type: "REVEAL_HAND", target: "beto", end: "LOW" });
    const r2 = await act(matchId, "ana", r1.seq, { type: "CLAIM", target: "carla", value: 7 }); // preguntar solo se puede una vez por turno
    expect(r2.finished).toBe(true);

    const node = (await matchNode(matchId))!;
    expect(node.meta.status).toBe("FINISHED");
    expect(node.live.result).toEqual({ winners: ["ana"], reason: "INSTANT" });
    expect(await reasonOf(act(matchId, "beto", r2.seq, { type: "REVEAL_CENTER", slot: 0 }))).toBe("GAME_OVER");

    // La sala vuelve a estar disponible para otra partida con el mismo grupo.
    const room = await read<RoomData>(env.deps, `rooms/${roomId}`);
    expect(room?.status).toBe("WAITING");
    expect(room?.currentMatchId).toBeUndefined();
    expect((await read<OpenRoomEntry>(env.deps, `openRooms/${roomId}`))?.players).toBe(3);
    for (const uid of uids) expect(await getActive(env.deps.db, uid)).toEqual({ roomId });
    await expect(startMatch(env.deps, "ana")).resolves.toHaveProperty("matchId");
  });
});

describe("tiempo agotado", () => {
  const timeout = (matchId: string, uid: string, expectedSeq: number) => timeoutTurn(env.deps, uid, { matchId, expectedSeq });

  it("la meta publica las reglas activas para que la app muestre solo los botones que aplican", async () => {
    const { matchId } = await startedMatch(3);
    expect((await matchNode(matchId))!.meta).toMatchObject({
      setSize: 3, allowPass: true, allowClaim: true, allowSelfReveal: true, minRevealsBeforePass: 1,
      askOncePerTurn: true, hideMismatch: true, allowMultiClaim: false,
    });
  });

  it("no se puede dar por vencido un turno que todavía tiene tiempo", async () => {
    const { matchId } = await startedMatch(3);
    const stored = await storedOf(matchId);
    expect(await reasonOf(timeout(matchId, "beto", stored.game.seq))).toBe("NOT_TIMED_OUT");
    env.clock.advance(5_000);
    expect(await reasonOf(timeout(matchId, "beto", stored.game.seq))).toBe("NOT_TIMED_OUT");
  });

  it("al vencer el tiempo, cualquier jugador avisa y el turno pasa al siguiente sin perder cartas", async () => {
    const { matchId, uids } = await startedMatch(3);
    const before = await storedOf(matchId);
    const sleepy = before.game.turn.player;
    const reporter = uids.find((u) => u !== sleepy)!;

    env.clock.advance(PAUSE_MS.gameIntro + 20_000 + 1);
    const result = await timeout(matchId, reporter, before.game.seq);
    expect(result.finished).toBe(false);

    const node = (await matchNode(matchId))!;
    const events = node.live.lastEvents as { type: string; player: string }[];
    expect(events.map((e) => e.type)).toEqual(["TURN_TIMEOUT", "TURN_STARTED"]);
    expect(events[0]!.player).toBe(sleepy);
    expect((node.live.turn as { player: string; startsAt: number }).player).not.toBe(sleepy);
    expect((node.live.turn as { startsAt: number }).startsAt).toBe(env.clock.now + PAUSE_MS.turnFailed);
    expect((await storedOf(matchId)).game.hands).toEqual(before.game.hands);
  });

  it("si dos jugadores avisan a la vez, el turno solo pasa una vez", async () => {
    const { matchId, uids } = await startedMatch(3);
    const before = await storedOf(matchId);
    env.clock.advance(PAUSE_MS.gameIntro + 20_001);

    const results = await Promise.allSettled(uids.map((u) => timeout(matchId, u, before.game.seq)));
    expect(results.filter((r) => r.status === "fulfilled")).toHaveLength(1);
    expect((await storedOf(matchId)).game.turn.number).toBe(before.game.turn.number + 1);
  });

  it("solo los jugadores de la mesa pueden avisar, y con el estado correcto", async () => {
    const { matchId } = await startedMatch(3);
    const stored = await storedOf(matchId);
    env.clock.advance(PAUSE_MS.gameIntro + 20_001);
    expect(await reasonOf(timeout(matchId, "dani", stored.game.seq))).toBe("NOT_A_PLAYER");
    expect(await reasonOf(timeout(matchId, "ana", stored.game.seq + 3))).toBe("STALE_STATE");
    expect(await reasonOf(timeoutTurn(env.deps, "ana", {}))).toBe("INVALID_ARGUMENT");
  });
});

describe("estadísticas de Firestore", () => {
  it("la victoria suma partidas, ternas y racha; la derrota reinicia la racha; no se cuenta dos veces", async () => {
    const { matchId, uids } = await startedMatch(3);
    const firestore = getFirestore(getApps()[0]!);
    for (const uid of uids) {
      await firestore.doc(`users/${uid}`).set({ displayName: env.names[uid], avatar: "jaguar_01", anonymous: true, level: 1, xp: 0, coins: 0 });
    }
    await firestore.doc("users/beto").update({ "stats.TERNA_CLASSIC": { games: 4, wins: 2, sets: 5, sevensWins: 0, currentStreak: 2, bestStreak: 3 } });

    const game = fixedGame({ hands: { ana: [7], beto: [7], carla: [7] }, center: [3], first: "ana" });
    await setGame(matchId, game);
    let seq = game.seq;
    seq = (await act(matchId, "ana", seq, { type: "REVEAL_HAND", target: "beto", end: "LOW" })).seq;
    seq = (await act(matchId, "ana", seq, { type: "CLAIM", target: "carla", value: 7 })).seq;

    const user = async (uid: string) => (await firestore.doc(`users/${uid}`).get()).data()!;
    expect((await user("ana")).stats.TERNA_CLASSIC).toEqual({ games: 1, wins: 1, sets: 1, sevensWins: 1, currentStreak: 1, bestStreak: 1 });
    expect((await user("beto")).stats.TERNA_CLASSIC).toEqual({ games: 5, wins: 2, sets: 5, sevensWins: 0, currentStreak: 0, bestStreak: 3 });
    expect((await user("carla")).stats.TERNA_CLASSIC).toMatchObject({ games: 1, wins: 0, currentStreak: 0 });
    expect((await user("ana")).level).toBe(1); // el servidor no toca nivel/xp/monedas aquí

    const history = (await firestore.doc(`users/ana/matchHistory/${matchId}`).get()).data()!;
    expect(history).toMatchObject({ result: "VICTORY", reason: "INSTANT", ruleSet: "TERNA_CLASSIC", matchId });
    expect((await firestore.doc(`users/beto/matchHistory/${matchId}`).get()).data()?.result).toBe("DEFEAT");
    const archive = (await firestore.doc(`matchArchive/${matchId}`).get()).data()!;
    expect(archive.playerUids.sort()).toEqual(["ana", "beto", "carla"]);
    expect(archive.winners).toEqual(["ana"]);

    // Volver a archivar la misma partida no duplica las estadísticas.
    await env.deps.archive({
      matchId, roomId: "x", ruleSet: "TERNA_CLASSIC", reason: "INSTANT", winners: ["ana"], durationSec: 1, endedAt: env.clock.now,
      players: [{ uid: "ana", name: "Ana", avatar: "jaguar_01", isBot: false, sets: [7], winner: true }],
    });
    expect((await user("ana")).stats.TERNA_CLASSIC.games).toBe(1);
  });
});

/** Jugador de prueba: casi siempre usa lo que sabe; a veces adivina. */
function chooseAction(state: GameState, rng: Rng): Action {
  const actions = availableActions(state)!;
  const player = state.turn.player;
  const value = actions.claimValue;

  if (value !== null && rng() < 0.9) {
    const slot = actions.centerSlots.find((s) => state.center[s] === value);
    if (slot !== undefined) return { type: "REVEAL_CENTER", player, slot };
    for (const target of actions.revealTargets) {
      for (const end of ["LOW", "HIGH"] as const) {
        const i = hiddenEndIndex(state, target, end);
        if (i !== null && state.hands[target]![i] === value) {
          // Preguntar solo se puede una vez por turno: si se sabe el número, se pide (sin límite).
          return actions.claimTargets.includes(target) ? { type: "CLAIM", player, target, value } : { type: "REVEAL_HAND", player, target, end };
        }
      }
    }
  }
  const choices: Action[] = [];
  for (const target of actions.revealTargets) for (const end of ["LOW", "HIGH"] as const) choices.push({ type: "REVEAL_HAND", player, target, end });
  for (const slot of actions.centerSlots) choices.push({ type: "REVEAL_CENTER", player, slot });
  for (const target of actions.claimTargets) choices.push({ type: "CLAIM", player, target, value: value ?? 1 + Math.floor(rng() * 12) });
  if (choices.length === 0) return { type: "PASS", player };
  return choices[Math.floor(rng() * choices.length)]!;
}

describe("partida completa", () => {
  it("se juega de principio a fin por el servicio y la base de datos siempre coincide con el motor", async () => {
    const { matchId, uids } = await startedMatch(4);
    const rng = seededRng(2026);
    let steps = 0;

    for (; steps < 4000; steps++) {
      const node = (await matchNode(matchId))!;
      if (node.meta.status === "FINISHED") break;
      const stored = JSON.parse(node.secret.state) as StoredMatch;
      const state = stored.game;

      // Lo público siempre refleja el estado real del motor.
      for (const uid of uids) {
        expect(node.players[uid]!.handCount).toBe(state.hands[uid]!.length);
        expect(node.hands?.[uid] ?? []).toEqual(state.hands[uid]);
      }
      expect(node.live.seq).toBe(state.seq);

      env.clock.advance(PAUSE_MS.turnFailed + 100);
      await act(matchId, state.turn.player, state.seq, chooseAction(state, rng));
    }

    const end = (await matchNode(matchId))!;
    expect(end.meta.status).toBe("FINISHED");
    expect((end.live.result as { winners: string[] }).winners.length).toBeGreaterThan(0);
    expect(steps).toBeGreaterThan(5);
    expect((await read<RoomData>(env.deps, `rooms/${end.meta.roomId}`))?.status).toBe("WAITING");
  }, 180_000);
});
