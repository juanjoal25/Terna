import { expect } from "vitest";
import { applyAction } from "./reducer.js";
import { TERNA_CLASSIC } from "./rulesets.js";
import type { Action, Card, GameEvent, GameState, PlayerId, RuleSet } from "./types.js";

/** Partida con manos y centro elegidos a mano (sin barajar) para probar reglas concretas. */
export function fixedGame(opts: {
  hands: Record<PlayerId, Card[]>;
  center: (Card | null)[];
  first?: PlayerId;
  ruleSet?: RuleSet;
  sets?: Record<PlayerId, Card[]>;
}): GameState {
  const players = Object.keys(opts.hands);
  const first = opts.first ?? players[0]!;
  return {
    ruleSet: opts.ruleSet ?? TERNA_CLASSIC,
    players,
    hands: Object.fromEntries(players.map((p) => [p, [...opts.hands[p]!].sort((a, b) => a - b)])),
    center: [...opts.center],
    sets: Object.fromEntries(players.map((p) => [p, [...(opts.sets?.[p] ?? [])]])),
    turn: { player: first, number: 1, revealed: [] },
    status: "PLAYING",
    result: null,
    seq: 1,
  };
}

/** Aplica una acción que debe ser válida y devuelve el estado y los eventos. */
export function play(state: GameState, action: Action): { state: GameState; events: GameEvent[] } {
  const result = applyAction(state, action);
  if (!result.ok) throw new Error(`Acción rechazada (${result.error}): ${JSON.stringify(action)}`);
  return { state: result.state, events: result.events };
}

/** Aplica varias acciones seguidas. */
export function playAll(state: GameState, actions: Action[]): { state: GameState; events: GameEvent[] } {
  let current = state;
  const events: GameEvent[] = [];
  for (const a of actions) {
    const r = play(current, a);
    current = r.state;
    events.push(...r.events);
  }
  return { state: current, events };
}

export function eventTypes(events: GameEvent[]): string[] {
  return events.map((e) => e.type);
}

export function expectRejected(state: GameState, action: Action, error: string): void {
  const result = applyAction(state, action);
  expect(result.ok).toBe(false);
  if (!result.ok) expect(result.error).toBe(error);
}

/** Conservación: ninguna carta aparece ni desaparece. */
export function cardCount(state: GameState): Map<Card, number> {
  const counts = new Map<Card, number>();
  const add = (v: Card, n = 1) => counts.set(v, (counts.get(v) ?? 0) + n);
  for (const p of state.players) {
    state.hands[p]!.forEach((v) => add(v));
    state.sets[p]!.forEach((v) => add(v, state.ruleSet.setSize));
  }
  state.center.forEach((v) => v !== null && add(v));
  return counts;
}
