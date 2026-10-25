import type { Card, GameState, HandEnd, PlayerId } from "./types.js";

/**
 * Índice de la carta oculta en el extremo pedido de la mano de `owner`, teniendo en
 * cuenta las cartas ya reveladas este turno (pedir "la menor" dos veces da la siguiente).
 */
export function hiddenEndIndex(state: GameState, owner: PlayerId, end: HandEnd): number | null {
  const hand = state.hands[owner];
  if (!hand) return null;
  const taken = new Set(
    state.turn.revealed.filter((r) => r.source === "HAND" && r.owner === owner).map((r) => (r.source === "HAND" ? r.index : -1)),
  );
  if (end === "LOW") {
    for (let i = 0; i < hand.length; i++) if (!taken.has(i)) return i;
  } else {
    for (let i = hand.length - 1; i >= 0; i--) if (!taken.has(i)) return i;
  }
  return null;
}

export function hasHiddenCards(state: GameState, owner: PlayerId): boolean {
  return hiddenEndIndex(state, owner, "LOW") !== null;
}

/** Huecos del centro que aún tienen carta y no se han revelado este turno. */
export function hiddenCenterSlots(state: GameState): number[] {
  const revealed = new Set(state.turn.revealed.map((r) => (r.source === "CENTER" ? r.slot : -1)));
  return state.center.flatMap((c, slot) => (c !== null && !revealed.has(slot) ? [slot] : []));
}

/** Valor que se está persiguiendo este turno, o null si aún no se reveló nada. */
export function currentTurnValue(state: GameState): Card | null {
  return state.turn.revealed[0]?.value ?? null;
}

/** ¿Hay alguna carta oculta fuera de la mano del jugador activo? */
export function hasOtherSources(state: GameState): boolean {
  const me = state.turn.player;
  return hiddenCenterSlots(state).length > 0 || state.players.some((p) => p !== me && hasHiddenCards(state, p));
}

/**
 * Para la UI: ¿puede el jugador activo cerrar el set con su propia mano?
 * Devuelve el extremo que debe revelar ("¡Hacer terna!") o null.
 */
export function ownCardToComplete(state: GameState): HandEnd | null {
  const value = currentTurnValue(state);
  if (value === null || state.turn.revealed.length !== state.ruleSet.setSize - 1) return null;
  const me = state.turn.player;
  for (const end of ["LOW", "HIGH"] as const) {
    const i = hiddenEndIndex(state, me, end);
    if (i !== null && state.hands[me]![i] === value) return end;
  }
  return null;
}

export interface AvailableActions {
  /** Jugadores a los que se puede revelar menor/mayor (incluye al propio si está permitido). */
  revealTargets: PlayerId[];
  centerSlots: number[];
  /** Jugadores a los que se les puede deducir una carta. */
  claimTargets: PlayerId[];
  /** Si ya hay cartas reveladas, solo se puede deducir este valor. */
  claimValue: Card | null;
  canPass: boolean;
  completeWithOwn: HandEnd | null;
}

/** Acciones válidas del jugador activo: sirve para habilitar botones y para los bots. */
export function availableActions(state: GameState): AvailableActions | null {
  if (state.status !== "PLAYING") return null;
  const { options } = state.ruleSet;
  const me = state.turn.player;
  const others = state.players.filter((p) => p !== me && hasHiddenCards(state, p));
  const selfAllowed = hasHiddenCards(state, me) && (options.allowSelfReveal || !hasOtherSources(state));

  return {
    revealTargets: selfAllowed ? [...others, me] : others,
    centerSlots: hiddenCenterSlots(state),
    claimTargets: options.allowClaim ? others : [],
    claimValue: currentTurnValue(state),
    canPass: options.allowPass && state.turn.revealed.length >= options.minRevealsBeforePass,
    completeWithOwn: selfAllowed ? ownCardToComplete(state) : null,
  };
}
