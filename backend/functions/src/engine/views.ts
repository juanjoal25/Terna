import type { Card, GameEvent, GameResult, GameState, HandEnd, PlayerId, Reveal } from "./types.js";

/** Lo que cualquiera en la mesa puede ver. Es lo único que se publica en `/matches/{id}/public`. */
export type PublicReveal =
  | { source: "HAND"; owner: PlayerId; end: HandEnd; value: Card; claimed: boolean }
  | { source: "CENTER"; slot: number; value: Card };

export interface PublicPlayer {
  id: PlayerId;
  handCount: number;
  sets: Card[];
}

export interface PublicView {
  ruleSetId: string;
  players: PublicPlayer[];
  /** true = el hueco tiene una carta boca abajo. Nunca se publica su valor. */
  center: boolean[];
  turn: { player: PlayerId; number: number; revealed: PublicReveal[] };
  status: GameState["status"];
  result: GameResult | null;
  seq: number;
}

export function toPublicReveal(r: Reveal): PublicReveal {
  // El índice en la mano es interno: se quita para no publicar más de lo necesario.
  return r.source === "HAND"
    ? { source: "HAND", owner: r.owner, end: r.end, value: r.value, claimed: r.claimed }
    : { source: "CENTER", slot: r.slot, value: r.value };
}

export function publicView(state: GameState): PublicView {
  return {
    ruleSetId: state.ruleSet.id,
    players: state.players.map((id) => ({ id, handCount: state.hands[id]!.length, sets: [...state.sets[id]!] })),
    center: state.center.map((c) => c !== null),
    turn: { player: state.turn.player, number: state.turn.number, revealed: state.turn.revealed.map(toPublicReveal) },
    status: state.status,
    result: state.result,
    seq: state.seq,
  };
}

/** Mano privada de un jugador (`/hands/{matchId}/{uid}`). */
export function handOf(state: GameState, player: PlayerId): Card[] {
  return [...(state.hands[player] ?? [])];
}

/** Los eventos solo contienen cartas ya reveladas; se limpian igual que el estado público. */
export function toPublicEvent(e: GameEvent): Record<string, unknown> {
  switch (e.type) {
    case "CARD_REVEALED":
      return { ...e, reveal: toPublicReveal(e.reveal) };
    case "TURN_FAILED":
    case "TURN_PASSED":
      return { ...e, revealed: e.revealed.map(toPublicReveal) };
    case "SET_COMPLETED":
      return { ...e, cards: e.cards.map(toPublicReveal) };
    default:
      return { ...e };
  }
}
