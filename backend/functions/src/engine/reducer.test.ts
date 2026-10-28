import { describe, expect, it } from "vitest";
import { applyAction } from "./reducer.js";
import { TERNA_CLASSIC, TERNA_SPICY, TRIO_ORIGINAL, withOptions } from "./rulesets.js";
import { eventTypes, expectRejected, fixedGame, play, playAll } from "./test-utils.js";
import type { GameState, RuleSet } from "./types.js";

const revealHand = (player: string, target: string, end: "LOW" | "HIGH") =>
  ({ type: "REVEAL_HAND", player, target, end }) as const;
const revealCenter = (player: string, slot: number) => ({ type: "REVEAL_CENTER", player, slot }) as const;
const claim = (player: string, target: string, value: number, end?: "LOW" | "HIGH") =>
  ({ type: "CLAIM", player, target, value, end }) as const;

describe("revelar cartas", () => {
  it("pedir la menor dos veces revela la siguiente carta oculta", () => {
    const g = fixedGame({ hands: { A: [3, 8], B: [1, 1, 9], C: [4, 6] }, center: [2] });
    const { state, events } = playAll(g, [revealHand("A", "B", "LOW"), revealHand("A", "B", "LOW")]);
    expect(state.turn.revealed.map((r) => r.value)).toEqual([1, 1]);
    expect(state.turn.player).toBe("A");
    expect(eventTypes(events)).toEqual(["CARD_REVEALED", "CARD_REVEALED"]);
  });

  it("revela la carta mayor", () => {
    const g = fixedGame({ hands: { A: [3], B: [1, 9], C: [4] }, center: [2] });
    const { state } = play(g, revealHand("A", "B", "HIGH"));
    expect(state.turn.revealed[0]).toMatchObject({ source: "HAND", owner: "B", end: "HIGH", value: 9 });
  });

  it("si no coincide, el turno falla, las cartas vuelven y juega el siguiente", () => {
    const g = fixedGame({ hands: { A: [3, 8], B: [1, 9], C: [4, 6] }, center: [1, 2] });
    const { state, events } = playAll(g, [revealHand("A", "B", "LOW"), revealCenter("A", 1)]);
    expect(eventTypes(events)).toEqual(["CARD_REVEALED", "CARD_REVEALED", "TURN_FAILED", "TURN_STARTED"]);
    const failed = events.find((e) => e.type === "TURN_FAILED");
    expect(failed?.type === "TURN_FAILED" && failed.revealed.map((r) => r.value)).toEqual([1, 2]);
    expect(state.turn).toEqual({ player: "B", number: 2, revealed: [] });
    expect(state.hands).toEqual(g.hands);
    expect(state.center).toEqual(g.center);
  });

  it("forma una terna con cartas de otra mano, del centro y de la propia", () => {
    const g = fixedGame({ hands: { A: [1, 8], B: [1, 9], C: [4, 6] }, center: [1, 2] });
    const { state, events } = playAll(g, [revealHand("A", "B", "LOW"), revealCenter("A", 0)]);
    expect(eventTypes(events)).toEqual(["CARD_REVEALED", "CARD_REVEALED", "CARD_REVEALED", "SET_COMPLETED", "TURN_STARTED"]);
    expect(state.sets.A).toEqual([1]);
    expect(state.hands).toEqual({ A: [8], B: [9], C: [4, 6] });
    expect(state.center).toEqual([null, 2]);
    expect(state.turn.player).toBe("B");
  });

  it("forma una terna con dos cartas de la misma mano", () => {
    const g = fixedGame({ hands: { A: [8], B: [5, 5, 9], C: [5] }, center: [2] });
    const { state } = playAll(g, [revealHand("A", "B", "LOW"), revealHand("A", "B", "LOW"), revealHand("A", "C", "HIGH")]);
    expect(state.sets.A).toEqual([5]);
    expect(state.hands.B).toEqual([9]);
    expect(state.hands.C).toEqual([]);
  });

  it("con continueAfterSet el jugador sigue tras la terna", () => {
    const rs = withOptions(TERNA_CLASSIC, { continueAfterSet: true });
    const g = fixedGame({ ruleSet: rs, hands: { A: [1, 8], B: [1, 9], C: [4] }, center: [1] });
    const { state } = playAll(g, [revealHand("A", "B", "LOW"), revealCenter("A", 0)]);
    expect(state.turn).toEqual({ player: "A", number: 1, revealed: [] });
  });
});

describe("revelar la propia mano", () => {
  const noSelf = withOptions(TERNA_CLASSIC, { allowSelfReveal: false });

  it("se rechaza si está desactivado y hay otras cartas ocultas", () => {
    const g = fixedGame({ ruleSet: noSelf, hands: { A: [1], B: [2], C: [3] }, center: [4] });
    expectRejected(g, revealHand("A", "A", "LOW"), "SELF_REVEAL_DISABLED");
  });

  it("se permite aunque esté desactivado si no queda ninguna otra carta (evita bloqueos)", () => {
    const g = fixedGame({ ruleSet: noSelf, hands: { A: [1, 2], B: [], C: [] }, center: [null] });
    expect(applyAction(g, revealHand("A", "A", "LOW")).ok).toBe(true);
  });
});

describe("deducir (CLAIM)", () => {
  it("acierta si el número es la carta menor del rival", () => {
    const g = fixedGame({ hands: { A: [3], B: [1, 5, 9], C: [4] }, center: [2] });
    const { state, events } = play(g, claim("A", "B", 1));
    expect(eventTypes(events)).toEqual(["CARD_REVEALED"]);
    expect(state.turn.revealed[0]).toMatchObject({ owner: "B", end: "LOW", value: 1, claimed: true });
  });

  it("acierta si el número es la carta mayor del rival", () => {
    const g = fixedGame({ hands: { A: [3], B: [1, 5, 9], C: [4] }, center: [2] });
    const { state } = play(g, claim("A", "B", 9));
    expect(state.turn.revealed[0]).toMatchObject({ end: "HIGH", value: 9 });
  });

  it("falla con -1000 de aura si la carta no está en un extremo, y pierde el turno", () => {
    const g = fixedGame({ hands: { A: [3], B: [1, 5, 9], C: [4] }, center: [2] });
    const { state, events } = play(g, claim("A", "B", 5));
    expect(eventTypes(events)).toEqual(["CLAIM_FAILED", "TURN_FAILED", "TURN_STARTED"]);
    expect(events[0]).toMatchObject({ type: "CLAIM_FAILED", target: "B", value: 5, taunt: "AURA_MINUS_1000" });
    expect(state.turn.player).toBe("B");
    expect(state.hands).toEqual(g.hands);
  });

  it("si claimFailEndsTurn está apagado, fallar solo da el mensaje", () => {
    const rs = withOptions(TERNA_CLASSIC, { claimFailEndsTurn: false });
    const g = fixedGame({ ruleSet: rs, hands: { A: [3], B: [1, 5, 9], C: [4] }, center: [2] });
    const { state, events } = play(g, claim("A", "B", 5));
    expect(eventTypes(events)).toEqual(["CLAIM_FAILED"]);
    expect(state.turn.player).toBe("A");
  });

  it("tras una carta revelada solo se puede deducir ese mismo número", () => {
    const g = fixedGame({ hands: { A: [3], B: [1, 9], C: [1, 4] }, center: [2] });
    const after = play(g, revealHand("A", "B", "LOW")).state;
    expectRejected(after, claim("A", "C", 4), "CLAIM_VALUE_MISMATCH");
    expect(play(after, claim("A", "C", 1)).state.turn.revealed).toHaveLength(2);
  });

  it("se puede completar la terna deduciendo", () => {
    const g = fixedGame({ hands: { A: [6], B: [6, 9], C: [2, 6] }, center: [6] });
    const { state } = playAll(g, [claim("A", "B", 6), claim("A", "C", 6)]);
    expect(state.sets.A).toEqual([6]);
  });

  it("con claimRequiresEnd hay que decir menor o mayor, y debe coincidir", () => {
    const rs = withOptions(TERNA_CLASSIC, { claimRequiresEnd: true });
    const g = fixedGame({ ruleSet: rs, hands: { A: [3], B: [1, 9], C: [4] }, center: [2] });
    expectRejected(g, claim("A", "B", 9), "CLAIM_END_REQUIRED");
    expect(eventTypes(play(g, claim("A", "B", 9, "LOW")).events)[0]).toBe("CLAIM_FAILED");
    expect(eventTypes(play(g, claim("A", "B", 9, "HIGH")).events)).toEqual(["CARD_REVEALED"]);
  });

  it("rechaza deducirse a uno mismo, valores fuera de rango y el modo original", () => {
    const g = fixedGame({ hands: { A: [3], B: [1], C: [4] }, center: [2] });
    expectRejected(g, claim("A", "A", 3), "CLAIM_SELF");
    expectRejected(g, claim("A", "B", 0), "INVALID_VALUE");
    expectRejected(g, claim("A", "B", 13), "INVALID_VALUE");
    expectRejected({ ...g, ruleSet: TRIO_ORIGINAL }, claim("A", "B", 1), "CLAIM_DISABLED");
  });
});

describe("pasar turno", () => {
  it("está desactivado por defecto", () => {
    const g = fixedGame({ hands: { A: [3], B: [1], C: [4] }, center: [2] });
    expectRejected(g, { type: "PASS", player: "A" }, "PASS_DISABLED");
  });

  it("si se activa, exige el mínimo de cartas reveladas y devuelve las cartas", () => {
    const rs = withOptions(TERNA_CLASSIC, { allowPass: true, minRevealsBeforePass: 1 });
    const g = fixedGame({ ruleSet: rs, hands: { A: [3], B: [1], C: [4] }, center: [2] });
    expectRejected(g, { type: "PASS", player: "A" }, "PASS_TOO_EARLY");
    const { state, events } = playAll(g, [revealCenter("A", 0), { type: "PASS", player: "A" }]);
    expect(eventTypes(events)).toEqual(["CARD_REVEALED", "TURN_PASSED", "TURN_STARTED"]);
    expect(state.turn.player).toBe("B");
    expect(state.center).toEqual([2]);
  });
});

describe("tiempo agotado", () => {
  it("pasa el turno al siguiente, devuelve las cartas reveladas y no cuenta como fallo de otro", () => {
    const g = fixedGame({ hands: { A: [3, 8], B: [1, 9], C: [4, 6] }, center: [2] });
    const afterReveal = play(g, revealHand("A", "B", "LOW")).state;
    const { state, events } = play(afterReveal, { type: "TIMEOUT", player: "A" });

    expect(eventTypes(events)).toEqual(["TURN_TIMEOUT", "TURN_STARTED"]);
    expect(events[0]).toMatchObject({ type: "TURN_TIMEOUT", player: "A" });
    expect(state.turn).toEqual({ player: "B", number: 2, revealed: [] });
    expect(state.hands).toEqual(g.hands);
  });

  it("solo se acepta para el jugador del turno", () => {
    const g = fixedGame({ hands: { A: [3], B: [1], C: [4] }, center: [2] });
    expectRejected(g, { type: "TIMEOUT", player: "B" }, "NOT_YOUR_TURN");
  });
});

describe("victoria", () => {
  const winWith = (ruleSet: RuleSet, value: number, previousSets: number[]): GameState => {
    const g = fixedGame({
      ruleSet,
      hands: { A: [value], B: [value], C: [value] },
      center: [value === 12 ? 1 : 12],
      sets: { A: previousSets },
    });
    return playAll(g, [revealHand("A", "B", "LOW"), revealHand("A", "C", "LOW")]).state;
  };

  it("la terna de 7 gana al instante", () => {
    const s = winWith(TERNA_CLASSIC, 7, []);
    expect(s.status).toBe("FINISHED");
    expect(s.result).toEqual({ winners: ["A"], reason: "INSTANT" });
  });

  it("tres ternas ganan en modo clásico", () => {
    expect(winWith(TERNA_CLASSIC, 3, [1, 2]).result).toEqual({ winners: ["A"], reason: "SETS" });
    expect(winWith(TERNA_CLASSIC, 3, [1]).status).toBe("PLAYING");
  });

  it("en Picante ganan dos ternas enlazadas por suma o resta de 7", () => {
    expect(winWith(TERNA_SPICY, 11, [4]).result?.reason).toBe("LINKED"); // 11 - 4 = 7
    expect(winWith(TERNA_SPICY, 5, [2]).result?.reason).toBe("LINKED"); // 2 + 5 = 7
  });

  it("en Picante tres ternas sin enlace no ganan", () => {
    expect(winWith(TERNA_SPICY, 3, [1, 2]).status).toBe("PLAYING");
  });

  it("si se acaban las cartas sin ganador, gana quien tenga más sets (con empate)", () => {
    const rs: RuleSet = { ...TERNA_CLASSIC, win: { setsNeeded: 5, instantWinValues: [], linkedPairTarget: null } };
    const g = fixedGame({ ruleSet: rs, hands: { A: [2], B: [2], C: [2] }, center: [null], sets: { A: [1], B: [3, 4] } });
    const { state } = playAll(g, [revealHand("A", "B", "LOW"), revealHand("A", "C", "LOW")]);
    expect(state.result).toEqual({ winners: ["A", "B"], reason: "EXHAUSTED" });
  });

  it("no acepta acciones con la partida terminada", () => {
    const s = winWith(TERNA_CLASSIC, 7, []);
    expectRejected(s, revealCenter(s.turn.player, 0), "GAME_OVER");
  });
});

describe("validaciones", () => {
  const g = fixedGame({ hands: { A: [3], B: [1], C: [] }, center: [2, null] });

  it("rechaza jugar fuera de turno o con jugadores desconocidos", () => {
    expectRejected(g, revealCenter("B", 0), "NOT_YOUR_TURN");
    expectRejected(g, revealCenter("Z", 0), "UNKNOWN_PLAYER");
    expectRejected(g, revealHand("A", "Z", "LOW"), "UNKNOWN_PLAYER");
  });

  it("rechaza huecos del centro inválidos, vacíos o ya revelados", () => {
    expectRejected(g, revealCenter("A", -1), "INVALID_SLOT");
    expectRejected(g, revealCenter("A", 2), "INVALID_SLOT");
    expectRejected(g, revealCenter("A", 0.5), "INVALID_SLOT");
    expectRejected(g, revealCenter("A", 1), "SLOT_EMPTY");
    expectRejected(play(g, revealCenter("A", 0)).state, revealCenter("A", 0), "SLOT_ALREADY_REVEALED");
  });

  it("rechaza pedir cartas a una mano sin cartas ocultas", () => {
    expectRejected(g, revealHand("A", "C", "LOW"), "NO_HIDDEN_CARDS");
    expectRejected(play(g, revealHand("A", "B", "LOW")).state, revealHand("A", "B", "HIGH"), "NO_HIDDEN_CARDS");
  });

  it("nunca muta el estado recibido", () => {
    const before = structuredClone(g);
    applyAction(g, revealCenter("A", 0));
    expect(g).toEqual(before);
  });

  it("numera los eventos de forma consecutiva", () => {
    const h = fixedGame({ hands: { A: [3], B: [1, 9], C: [4] }, center: [2] });
    const { state, events } = playAll(h, [revealHand("A", "B", "LOW"), revealCenter("A", 0)]);
    expect(events.map((e) => e.seq)).toEqual([2, 3, 4, 5]);
    expect(state.seq).toBe(5);
  });
});

describe("cartas repetidas en la misma mano", () => {
  const claimN = (player: string, target: string, value: number, count: number) =>
    ({ type: "CLAIM", player, target, value, count }) as const;

  it("se pueden pedir dos cartas iguales seguidas a la vez", () => {
    const g = fixedGame({ hands: { A: [3, 8], B: [5, 5, 9], C: [5, 6] }, center: [2] });
    const { state, events } = play(g, claimN("A", "B", 5, 2));
    expect(eventTypes(events)).toEqual(["CARD_REVEALED", "CARD_REVEALED"]);
    expect(state.turn.revealed.map((r) => [r.source === "HAND" && r.owner, r.value])).toEqual([["B", 5], ["B", 5]]);
    // Con la tercera completa la terna.
    const done = play(state, claim("A", "C", 5)).state;
    expect(done.sets.A).toEqual([5]);
    expect(done.hands.B).toEqual([9]);
  });

  it("pedir dos cuando solo hay una seguida falla con aura y no revela nada", () => {
    const g = fixedGame({ hands: { A: [3], B: [5, 7, 9], C: [1] }, center: [2] });
    const { state, events } = play(g, claimN("A", "B", 5, 2));
    expect(eventTypes(events)).toEqual(["CLAIM_FAILED", "TURN_FAILED", "TURN_STARTED"]);
    expect(state.hands).toEqual(g.hands);
  });

  it("rechaza cantidades imposibles sin penalizar", () => {
    const g = fixedGame({ hands: { A: [3], B: [5, 5], C: [1] }, center: [2] });
    expectRejected(g, claimN("A", "B", 5, 0), "INVALID_COUNT");
    expectRejected(g, claimN("A", "B", 5, 3), "INVALID_COUNT"); // B solo tiene 2 cartas
    const one = play(g, claim("A", "B", 5)).state;
    expectRejected(one, claimN("A", "B", 5, 2), "INVALID_COUNT"); // solo queda 1 oculta
  });

  it("si tienes dos iguales seguidas y aciertas la tercera, la terna se completa sola", () => {
    const g = fixedGame({ hands: { A: [5, 5, 9], B: [2, 5], C: [1] }, center: [4] });
    const { state, events } = play(g, claim("A", "B", 5));
    expect(eventTypes(events)).toEqual(["CARD_REVEALED", "CARD_REVEALED", "CARD_REVEALED", "SET_COMPLETED", "TURN_STARTED"]);
    expect(state.sets.A).toEqual([5]);
    expect(state.hands).toEqual({ A: [9], B: [2], C: [1] });
  });

  it("también se completa sola al destapar el centro o la mano de otro", () => {
    const g = fixedGame({ hands: { A: [5, 5, 9], B: [5, 8], C: [1] }, center: [4] });
    expect(play(g, revealHand("A", "B", "LOW")).state.sets.A).toEqual([5]);
    const c = fixedGame({ hands: { A: [5, 5, 9], B: [2, 8], C: [1] }, center: [5] });
    expect(play(c, revealCenter("A", 0)).state.sets.A).toEqual([5]);
  });

  it("no se completa sola si tus cartas iguales no están en un extremo", () => {
    // Mano ordenada [3,5,5,9]: los 5 quedan en medio, así que no se pueden destapar.
    const g = fixedGame({ hands: { A: [3, 5, 5, 9], B: [5, 8], C: [1] }, center: [4] });
    const after = play(g, revealHand("A", "B", "LOW")).state;
    expect(after.sets.A).toEqual([]);
    expect(after.turn.revealed).toHaveLength(1);
  });

  it("no se completa sola si las reglas de la sala no dejan revelar la propia mano", () => {
    const rs = withOptions(TERNA_CLASSIC, { allowSelfReveal: false });
    const g = fixedGame({ ruleSet: rs, hands: { A: [5, 5, 9], B: [2, 5], C: [1] }, center: [4] });
    expect(play(g, claim("A", "B", 5)).state.turn.revealed).toHaveLength(1);
  });
});
