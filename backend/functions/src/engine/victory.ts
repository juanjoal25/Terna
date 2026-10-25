import type { Card, GameState, PlayerId, RuleSet, WinReason } from "./types.js";

/** Dos sets están enlazados si sus valores suman o se restan exactamente `target`. */
export function areLinked(a: Card, b: Card, target: number): boolean {
  return a + b === target || Math.abs(a - b) === target;
}

/**
 * Comprueba si el jugador gana tras completar un set de `justCompleted`.
 * `sets` ya incluye ese último set.
 */
export function winReasonAfterSet(ruleSet: RuleSet, sets: Card[], justCompleted: Card): WinReason | null {
  const { win } = ruleSet;
  if (win.instantWinValues.includes(justCompleted)) return "INSTANT";

  if (win.linkedPairTarget !== null) {
    const others = [...sets];
    others.splice(others.lastIndexOf(justCompleted), 1);
    if (others.some((v) => areLinked(v, justCompleted, win.linkedPairTarget!))) return "LINKED";
  }

  if (win.setsNeeded !== null && sets.length >= win.setsNeeded) return "SETS";
  return null;
}

/** No queda ninguna carta en manos ni en el centro. */
export function isExhausted(state: GameState): boolean {
  return state.center.every((c) => c === null) && state.players.every((p) => state.hands[p]!.length === 0);
}

/** Desempate por agotamiento: gana quien tenga más sets (puede haber empate). */
export function mostSets(state: GameState): PlayerId[] {
  const best = Math.max(...state.players.map((p) => state.sets[p]!.length));
  return state.players.filter((p) => state.sets[p]!.length === best);
}
