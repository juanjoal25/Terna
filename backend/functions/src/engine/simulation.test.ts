import { describe, expect, it } from "vitest";
import { availableActions, currentTurnValue, hiddenEndIndex } from "./queries.js";
import { applyAction } from "./reducer.js";
import { seededRng, type Rng } from "./rng.js";
import { TERNA_CLASSIC, TERNA_SPICY, TRIO_ORIGINAL, withOptions } from "./rulesets.js";
import { createGame } from "./setup.js";
import { cardCount } from "./test-utils.js";
import type { Action, GameState, RuleSet } from "./types.js";

/** Modo CUARTA de prueba (aún sin diseñar): demuestra que el motor no depende del número 3. */
const CUARTA_DRAFT: RuleSet = {
  ...TERNA_CLASSIC,
  id: "CUARTA_DRAFT",
  copies: 4,
  setSize: 4,
  deal: {
    3: { hand: 12, center: 12 },
    4: { hand: 10, center: 8 },
    5: { hand: 8, center: 8 },
    6: { hand: 7, center: 6 },
  },
};

const pick = <T>(items: T[], rng: Rng): T => items[Math.floor(rng() * items.length)]!;

/**
 * Jugador "con buena memoria": si ya persigue un número y sabe dónde hay otro en un
 * extremo o en el centro, lo pide. Así las partidas avanzan como entre humanos.
 */
function informedAction(state: GameState): Action | null {
  const value = currentTurnValue(state);
  if (value === null) return null;
  const actions = availableActions(state)!;
  const player = state.turn.player;
  const slot = actions.centerSlots.find((s) => state.center[s] === value);
  if (slot !== undefined) return { type: "REVEAL_CENTER", player, slot };
  for (const target of actions.revealTargets) {
    for (const end of ["LOW", "HIGH"] as const) {
      const i = hiddenEndIndex(state, target, end);
      if (i !== null && state.hands[target]![i] === value) return { type: "REVEAL_HAND", player, target, end };
    }
  }
  return null;
}

/** Mezcla jugadas informadas (80 %) con jugadas al azar entre las permitidas. */
function randomAction(state: GameState, rng: Rng): Action {
  const informed = rng() < 0.8 ? informedAction(state) : null;
  if (informed) return informed;
  const actions = availableActions(state)!;
  const player = state.turn.player;
  const options: (() => Action)[] = [];
  if (actions.revealTargets.length > 0) {
    options.push(() => ({ type: "REVEAL_HAND", player, target: pick(actions.revealTargets, rng), end: rng() < 0.5 ? "LOW" : "HIGH" }));
  }
  if (actions.centerSlots.length > 0) {
    options.push(() => ({ type: "REVEAL_CENTER", player, slot: pick(actions.centerSlots, rng) }));
  }
  if (actions.claimTargets.length > 0) {
    const value = actions.claimValue ?? 1 + Math.floor(rng() * state.ruleSet.maxNumber);
    options.push(() => ({ type: "CLAIM", player, target: pick(actions.claimTargets, rng), value }));
  }
  if (actions.canPass) options.push(() => ({ type: "PASS", player }));
  expect(options.length).toBeGreaterThan(0);
  return pick(options, rng)();
}

function simulate(ruleSet: RuleSet, playerCount: number, seed: number): GameState {
  const rng = seededRng(seed);
  const players = Array.from({ length: playerCount }, (_, i) => `p${i}`);
  let state = createGame(ruleSet, players, { rng }).state;
  const initialCounts = cardCount(state);

  for (let step = 0; step < 20_000 && state.status === "PLAYING"; step++) {
    const result = applyAction(state, randomAction(state, rng));
    if (!result.ok) throw new Error(`Acción "válida" rechazada: ${result.error} (seed ${seed}, paso ${step})`);
    state = result.state;
    expect(cardCount(state)).toEqual(initialCounts);
  }
  return state;
}

describe("simulación de partidas aleatorias", () => {
  const cases: [RuleSet, number][] = [
    [TERNA_CLASSIC, 3],
    [TERNA_CLASSIC, 6],
    [TERNA_SPICY, 4],
    [TRIO_ORIGINAL, 5],
    [withOptions(TERNA_CLASSIC, { allowPass: true, claimFailEndsTurn: false, continueAfterSet: true }), 4],
    [CUARTA_DRAFT, 3],
    [CUARTA_DRAFT, 6],
  ];

  it.each(cases.map(([rs, n]) => [rs.id, n, rs] as const))("%s con %i jugadores siempre termina con un ganador y sin perder cartas", (_id, n, ruleSet) => {
    for (let seed = 1; seed <= 40; seed++) {
      const end = simulate(ruleSet, n, seed);
      expect(end.status).toBe("FINISHED");
      expect(end.result!.winners.length).toBeGreaterThan(0);
    }
  });
});
