import { describe, expect, it } from "vitest";
import { seededRng } from "./rng.js";
import { RULESETS, TERNA_CLASSIC, TRIO_DEAL, validateRuleSet } from "./rulesets.js";
import { createGame } from "./setup.js";
import { cardCount } from "./test-utils.js";

const players = (n: number) => Array.from({ length: n }, (_, i) => `p${i + 1}`);

describe("reparto", () => {
  it.each([
    [3, 9, 9],
    [4, 7, 8],
    [5, 6, 6],
    [6, 5, 6],
  ])("con %i jugadores: %i cartas en mano y %i al centro", (n, hand, center) => {
    const { state } = createGame(TERNA_CLASSIC, players(n), { rng: seededRng(42) });
    for (const p of state.players) expect(state.hands[p]).toHaveLength(hand);
    expect(state.center).toHaveLength(center);
  });

  it("a más jugadores, menos (o iguales) cartas al centro", () => {
    const centers = [3, 4, 5, 6].map((n) => TRIO_DEAL[n]!.center);
    expect(centers).toEqual([...centers].sort((a, b) => b - a));
  });

  it("reparte las 36 cartas: tres copias de cada número", () => {
    const { state } = createGame(TERNA_CLASSIC, players(4), { rng: seededRng(7) });
    const counts = cardCount(state);
    for (let v = 1; v <= 12; v++) expect(counts.get(v)).toBe(3);
  });

  it("las manos quedan ordenadas de menor a mayor", () => {
    const { state } = createGame(TERNA_CLASSIC, players(5), { rng: seededRng(3) });
    for (const p of state.players) expect(state.hands[p]).toEqual([...state.hands[p]!].sort((a, b) => a - b));
  });

  it("es determinista con la misma semilla y cambia con otra", () => {
    const a = createGame(TERNA_CLASSIC, players(3), { rng: seededRng(99) }).state;
    const b = createGame(TERNA_CLASSIC, players(3), { rng: seededRng(99) }).state;
    const c = createGame(TERNA_CLASSIC, players(3), { rng: seededRng(100) }).state;
    expect(a).toEqual(b);
    expect(a.hands).not.toEqual(c.hands);
  });

  it("abre el primer turno con el jugador indicado", () => {
    const { state, events } = createGame(TERNA_CLASSIC, players(3), { rng: seededRng(1), firstPlayer: "p2" });
    expect(state.turn).toEqual({ player: "p2", number: 1, revealed: [] });
    expect(events).toEqual([{ seq: 1, type: "TURN_STARTED", player: "p2", number: 1 }]);
  });

  it("rechaza un número de jugadores no soportado o repetido", () => {
    expect(() => createGame(TERNA_CLASSIC, players(2), { rng: seededRng(1) })).toThrow(/admite/);
    expect(() => createGame(TERNA_CLASSIC, players(7), { rng: seededRng(1) })).toThrow(/admite/);
    expect(() => createGame(TERNA_CLASSIC, ["a", "a", "b"], { rng: seededRng(1) })).toThrow(/repetidos/);
  });
});

describe("validateRuleSet", () => {
  it("acepta todos los modos predefinidos", () => {
    for (const rs of Object.values(RULESETS)) expect(() => validateRuleSet(rs)).not.toThrow();
  });

  it("detecta un reparto que no suma el mazo", () => {
    const bad = { ...TERNA_CLASSIC, deal: { 3: { hand: 9, center: 8 } } };
    expect(() => validateRuleSet(bad)).toThrow(/no suma/);
  });

  it("detecta más cartas al centro con más jugadores", () => {
    const bad = { ...TERNA_CLASSIC, deal: { 3: { hand: 10, center: 6 }, 4: { hand: 7, center: 8 } } };
    expect(() => validateRuleSet(bad)).toThrow(/más cartas al centro/);
  });

  it("detecta un modo sin forma de ganar", () => {
    const bad = { ...TERNA_CLASSIC, win: { setsNeeded: null, instantWinValues: [], linkedPairTarget: null } };
    expect(() => validateRuleSet(bad)).toThrow(/victoria/);
  });
});
