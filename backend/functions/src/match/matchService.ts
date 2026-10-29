import {
  applyAction,
  createGame,
  handOf,
  publicView,
  RULESETS,
  toPublicEvent,
  withOptions,
  type Action,
  type GameEvent,
  type HandEnd,
} from "../engine/index.js";
import { GameError } from "../lib/errors.js";
import { clean, transact } from "../lib/tx.js";
import { getActive, syncOpenRoom } from "../rooms/roomService.js";
import type { Deps, RoomData } from "../rooms/types.js";
import { playerRange } from "../rooms/validation.js";
import type { MatchNode, MatchPlayerBase, MatchRecord, StoredMatch } from "./types.js";

/** Pausas que se dan a la mesa para que todos vean lo ocurrido antes de que juegue el siguiente. */
export const PAUSE_MS = { turnFailed: 2000, setCompleted: 1500, claimFailed: 1500, gameIntro: 1500 } as const;
/** Margen para relojes desincronizados: se acepta una jugada hasta 300 ms antes de `startsAt`. */
const EARLY_TOLERANCE_MS = 300;

const memberCount = (room: RoomData) => Object.keys(room.members ?? {}).length;

// ───────────────────────── Proyección: estado del motor → nodo de RTDB ─────────────────────────

function projectNode(stored: StoredMatch, base: Record<string, MatchPlayerBase>, meta: MatchNode["meta"], events: GameEvent[]): MatchNode {
  const { game } = stored;
  const view = publicView(game);

  const players: MatchNode["players"] = {};
  for (const p of view.players) players[p.id] = { ...base[p.id]!, handCount: p.handCount, ...(p.sets.length > 0 ? { sets: p.sets } : {}) };

  const hands: Record<string, number[]> = {};
  for (const uid of game.players) if (game.hands[uid]!.length > 0) hands[uid] = handOf(game, uid);

  return clean({
    meta,
    players,
    live: {
      seq: game.seq,
      turn: { player: game.turn.player, number: game.turn.number, startsAt: stored.timing.startsAt, deadline: stored.timing.deadline },
      revealed: view.turn.revealed,
      askUsed: view.turn.askUsed ? true : undefined,
      center: view.center,
      lastEvents: events.map(toPublicEvent),
      ...(game.result ? { result: game.result } : {}),
    },
    hands,
    secret: { state: JSON.stringify(stored) },
  });
}

function baseOf(node: MatchNode): Record<string, MatchPlayerBase> {
  return Object.fromEntries(
    Object.entries(node.players).map(([uid, p]) => [uid, { seat: p.seat, name: p.name, avatar: p.avatar, isBot: p.isBot, connected: p.connected }]),
  );
}

// ───────────────────────── Empezar partida ─────────────────────────

export async function startMatch(deps: Deps, uid: string): Promise<{ matchId: string }> {
  const { db } = deps;
  const active = await getActive(db, uid);
  if (!active?.roomId) throw new GameError("failed-precondition", "NOT_IN_ROOM", "No estás en ninguna sala");
  const roomId = active.roomId;
  const now = deps.now();

  // 1) Reservar el inicio: pasa a STARTING, así dos pulsaciones de "Iniciar" no crean dos partidas.
  const room = await transact<RoomData>(db.ref(`rooms/${roomId}`), (r) => {
    if (r.hostUid !== uid) throw new GameError("permission-denied", "NOT_HOST", "Solo el anfitrión puede iniciar");
    if (r.status !== "WAITING") throw new GameError("failed-precondition", "ROOM_STARTED", "La partida ya empezó");
    const range = playerRange(RULESETS[r.config.ruleSet]!);
    if (memberCount(r) < range.min) throw new GameError("failed-precondition", "NOT_ENOUGH_PLAYERS", `Se necesitan al menos ${range.min} jugadores`);
    if (memberCount(r) > range.max) throw new GameError("failed-precondition", "TOO_MANY_PLAYERS");
    return { ...r, status: "STARTING", updatedAt: now };
  });
  if (!room) throw new GameError("not-found", "ROOM_NOT_FOUND");

  const revert = () => db.ref(`rooms/${roomId}`).update({ status: "WAITING", updatedAt: deps.now() });
  try {
    // 2) Barajar, repartir y publicar todo en un solo guardado.
    const ruleSet = withOptions(RULESETS[room.config.ruleSet]!, room.config.options ?? {});
    const seating = Object.entries(room.members).sort(([, a], [, b]) => a.joinedAt - b.joinedAt);
    const players = seating.map(([id]) => id);
    const { state: game, events } = createGame(ruleSet, players, { rng: deps.rng });

    const matchId = db.ref("matches").push().key;
    if (!matchId) throw new Error("No se pudo generar el ID de la partida");

    const startsAt = now + PAUSE_MS.gameIntro;
    const stored: StoredMatch = {
      v: 1,
      game,
      roomId,
      startedAt: now,
      turnSeconds: room.config.turnSeconds,
      timing: { startsAt, deadline: startsAt + room.config.turnSeconds * 1000 },
    };
    const base: Record<string, MatchPlayerBase> = Object.fromEntries(
      seating.map(([id, m], seat) => [id, { seat, name: m.name, avatar: m.avatar, isBot: false, connected: true }]),
    );
    const meta: MatchNode["meta"] = {
      roomId,
      ruleSet: ruleSet.id,
      ruleVersion: ruleSet.version,
      status: "PLAYING",
      turnSeconds: room.config.turnSeconds,
      createdAt: now,
      setSize: ruleSet.setSize,
      allowPass: ruleSet.options.allowPass,
      allowClaim: ruleSet.options.allowClaim,
      allowSelfReveal: ruleSet.options.allowSelfReveal,
      minRevealsBeforePass: ruleSet.options.minRevealsBeforePass,
      askOncePerTurn: ruleSet.options.askOncePerTurn,
      hideMismatch: ruleSet.options.hideMismatch,
      allowMultiClaim: ruleSet.options.allowMultiClaim,
    };

    await db.ref().update(
      clean({
        [`matches/${matchId}`]: projectNode(stored, base, meta, events),
        [`rooms/${roomId}/status`]: "IN_MATCH",
        [`rooms/${roomId}/currentMatchId`]: matchId,
        [`openRooms/${roomId}`]: null,
        ...Object.fromEntries(players.map((id) => [`active/${id}`, { roomId, matchId }])),
      }),
    );
    return { matchId };
  } catch (error) {
    await revert();
    throw error;
  }
}

// ───────────────────────── Jugar ─────────────────────────

/** Lo que el cliente puede pedir. El jugador NUNCA viene del cliente: sale de la sesión. */
export type ActionInput =
  | { type: "REVEAL_HAND"; target: string; end: HandEnd }
  | { type: "REVEAL_CENTER"; slot: number }
  | { type: "CLAIM"; target: string; value: number; end?: HandEnd; count?: number }
  | { type: "PASS" };

const isEnd = (v: unknown): v is HandEnd => v === "LOW" || v === "HIGH";
const isString = (v: unknown): v is string => typeof v === "string" && v.length > 0 && v.length <= 128;
const isInt = (v: unknown): v is number => typeof v === "number" && Number.isInteger(v);

export function parseActionInput(raw: unknown): ActionInput {
  const a = (raw ?? {}) as Record<string, unknown>;
  const bad = () => new GameError("invalid-argument", "INVALID_ACTION", "Jugada con formato incorrecto");
  switch (a.type) {
    case "REVEAL_HAND":
      if (!isString(a.target) || !isEnd(a.end)) throw bad();
      return { type: "REVEAL_HAND", target: a.target, end: a.end };
    case "REVEAL_CENTER":
      if (!isInt(a.slot)) throw bad();
      return { type: "REVEAL_CENTER", slot: a.slot };
    case "CLAIM":
      if (!isString(a.target) || !isInt(a.value) || (a.end !== undefined && !isEnd(a.end))) throw bad();
      if (a.count !== undefined && (!isInt(a.count) || a.count < 1 || a.count > 12)) throw bad();
      return {
        type: "CLAIM",
        target: a.target,
        value: a.value,
        ...(a.end !== undefined ? { end: a.end } : {}),
        ...(a.count !== undefined ? { count: a.count } : {}),
      };
    case "PASS":
      return { type: "PASS" };
    default:
      throw bad();
  }
}

function pauseAfter(events: GameEvent[]): number {
  if (!events.some((e) => e.type === "TURN_STARTED")) {
    return events.some((e) => e.type === "CLAIM_FAILED") ? PAUSE_MS.claimFailed : 0;
  }
  if (events.some((e) => e.type === "TURN_FAILED" || e.type === "TURN_PASSED" || e.type === "TURN_TIMEOUT")) return PAUSE_MS.turnFailed;
  if (events.some((e) => e.type === "SET_COMPLETED")) return PAUSE_MS.setCompleted;
  return 0;
}

export async function gameAction(deps: Deps, uid: string, rawInput: unknown): Promise<{ seq: number; finished: boolean }> {
  const input = (rawInput ?? {}) as { matchId?: unknown; expectedSeq?: unknown; action?: unknown };
  if (!isString(input.matchId) || !isInt(input.expectedSeq)) throw new GameError("invalid-argument", "INVALID_ARGUMENT");
  const action = parseActionInput(input.action);

  return mutateMatch(deps, uid, input.matchId, input.expectedSeq, (stored, now) => {
    if (now < stored.timing.startsAt - EARLY_TOLERANCE_MS) throw new GameError("failed-precondition", "TOO_EARLY", "Espera a que termine la animación");
    // El jugador sale de la sesión, nunca de lo que mande la app.
    return { ...action, player: uid } as Action;
  });
}

/**
 * Cualquier jugador de la mesa puede avisar de que el turno se pasó de tiempo. El servidor lo
 * comprueba con SU reloj y, si es cierto, pasa el turno. Así una mesa nunca se congela porque
 * alguien se durmió, sin necesitar un temporizador en el servidor (basta con que haya un cliente vivo).
 */
export async function timeoutTurn(deps: Deps, uid: string, rawInput: unknown): Promise<{ seq: number; finished: boolean }> {
  const input = (rawInput ?? {}) as { matchId?: unknown; expectedSeq?: unknown };
  if (!isString(input.matchId) || !isInt(input.expectedSeq)) throw new GameError("invalid-argument", "INVALID_ARGUMENT");

  const result = await mutateMatch(deps, uid, input.matchId, input.expectedSeq, (stored, now) => {
    if (now < stored.timing.deadline) throw new GameError("failed-precondition", "NOT_TIMED_OUT", "Todavía queda tiempo en el turno");
    return { type: "TIMEOUT", player: stored.game.turn.player };
  });
  // Solo llega aquí quien aplicó el tiempo agotado; deja rastro para diagnosticar turnos raros.
  console.log("Tiempo agotado aplicado", { matchId: input.matchId, reportedBy: uid, seq: result.seq });
  return result;
}

/**
 * Núcleo común de `gameAction` y `timeoutTurn`: dentro de UNA transacción valida que `uid` juegue en la
 * partida y que el estado sea el que la app vio (`expectedSeq`), aplica la acción con el motor y
 * publica el resultado completo (mesa pública, manos, estado secreto).
 */
async function mutateMatch(
  deps: Deps,
  uid: string,
  matchId: string,
  expectedSeq: number,
  chooseAction: (stored: StoredMatch, now: number) => Action,
): Promise<{ seq: number; finished: boolean }> {
  const { db } = deps;
  const now = deps.now();

  let outcome: { stored: StoredMatch; events: GameEvent[]; node: MatchNode } | null = null;

  const saved = await transact<MatchNode>(db.ref(`matches/${matchId}`), (node) => {
    outcome = null;
    if (node.meta.status === "FINISHED") throw new GameError("failed-precondition", "GAME_OVER", "La partida terminó");
    const stored = JSON.parse(node.secret.state) as StoredMatch;
    if (!stored.game.players.includes(uid)) throw new GameError("permission-denied", "NOT_A_PLAYER", "No juegas en esta partida");
    if (stored.game.seq !== expectedSeq) throw new GameError("aborted", "STALE_STATE", "El estado de la mesa cambió");

    const result = applyAction(stored.game, chooseAction(stored, now));
    if (!result.ok) throw new GameError("failed-precondition", result.error);

    const { state: game, events } = result;
    const startsAt = now + pauseAfter(events);
    const next: StoredMatch = { ...stored, game, timing: { startsAt, deadline: startsAt + stored.turnSeconds * 1000 } };
    const finished = game.status === "FINISHED";
    const meta = { ...node.meta, status: finished ? ("FINISHED" as const) : ("PLAYING" as const), ...(finished ? { endedAt: now } : {}) };

    const updated = projectNode(next, baseOf(node), meta, events);
    outcome = { stored: next, events, node: updated };
    return updated;
  });
  if (!saved || !outcome) throw new GameError("not-found", "MATCH_NOT_FOUND", "La partida no existe");

  const { stored, node } = outcome as { stored: StoredMatch; events: GameEvent[]; node: MatchNode };
  const finished = stored.game.status === "FINISHED";
  if (finished) await finishMatch(deps, matchId, stored, node);
  return { seq: stored.game.seq, finished };
}

// ───────────────────────── Fin de partida ─────────────────────────

/** Devuelve la sala a "esperando", libera a los jugadores y guarda historial y estadísticas. */
async function finishMatch(deps: Deps, matchId: string, stored: StoredMatch, node: MatchNode): Promise<void> {
  const { db } = deps;
  const { game, roomId } = stored;
  const result = game.result!;

  await db.ref().update(
    clean({
      [`rooms/${roomId}/status`]: "WAITING",
      [`rooms/${roomId}/currentMatchId`]: null,
      [`rooms/${roomId}/updatedAt`]: deps.now(),
      ...Object.fromEntries(game.players.map((id) => [`active/${id}`, { roomId }])),
    }),
  );
  const room = (await db.ref(`rooms/${roomId}`).get()).val() as RoomData | null;
  if (room) await syncOpenRoom(db, roomId, room);

  const record: MatchRecord = {
    matchId,
    roomId,
    ruleSet: game.ruleSet.id,
    reason: result.reason,
    winners: result.winners,
    durationSec: Math.max(0, Math.round(((node.meta.endedAt ?? deps.now()) - node.meta.createdAt) / 1000)),
    endedAt: node.meta.endedAt ?? deps.now(),
    players: game.players.map((id) => ({
      uid: id,
      name: node.players[id]!.name,
      avatar: node.players[id]!.avatar,
      isBot: node.players[id]!.isBot,
      sets: game.sets[id]!,
      winner: result.winners.includes(id),
    })),
  };
  // Si Firestore falla, la partida ya terminó bien: se registra el error y no se rompe la respuesta.
  await deps.archive(record).catch((error) => console.error(`No se pudo archivar la partida ${matchId}`, error));
}
