import type { Card, GameState, HandEnd, PlayerId } from "./types.js";

/**
 * Índice de la carta oculta en el extremo pedido de la mano de `owner`, teniendo en
 * cuenta las cartas ya reveladas este turno (pedir "la menor" dos veces da la siguiente).
 */
export function hiddenEndIndex(state: GameState, owner: PlayerId, end: HandEnd): number | null {
  const hand = state.hands[owner];
  if (!hand) return null;
  return endIndex(hand, takenIndexes(state, owner), end);
}

/** Posiciones de la mano de `owner` que ya se destaparon este turno. */
function takenIndexes(state: GameState, owner: PlayerId): Set<number> {
  const taken = new Set<number>();
  for (const r of state.turn.revealed) if (r.source === "HAND" && r.owner === owner) taken.add(r.index);
  return taken;
}

function endIndex(hand: readonly Card[], taken: ReadonlySet<number>, end: HandEnd): number | null {
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

/** Cartas de `owner` que siguen ocultas este turno. */
export function hiddenCount(state: GameState, owner: PlayerId): number {
  return (state.hands[owner]?.length ?? 0) - takenIndexes(state, owner).size;
}

export interface HandPick {
  index: number;
  end: HandEnd;
}

/**
 * Si `owner` tiene `count` cartas de `value` seguidas en sus extremos (cualquier mezcla de menores y
 * mayores, sin saltarse ninguna), devuelve cuáles son, en orden. Si no, null.
 * Con `end` solo se mira ese extremo. Es lo que permite pedir "dos 5" de una sola vez.
 */
export function planHandCards(state: GameState, owner: PlayerId, value: Card, count: number, end?: HandEnd): HandPick[] | null {
  const hand = state.hands[owner];
  if (!hand) return null;
  const taken = takenIndexes(state, owner);
  const ends: HandEnd[] = end ? [end] : ["LOW", "HIGH"];
  const picks: HandPick[] = [];
  for (let n = 0; n < count; n++) {
    const found = ends.flatMap((e) => {
      const index = endIndex(hand, taken, e);
      return index !== null && hand[index] === value ? [{ index, end: e }] : [];
    })[0];
    if (!found) return null;
    taken.add(found.index);
    picks.push(found);
  }
  return picks;
}

/** Huecos del centro que aún tienen carta y no se han revelado este turno. */
export function hiddenCenterSlots(state: GameState): number[] {
  const revealed = new Set(state.turn.revealed.map((r) => (r.source === "CENTER" ? r.slot : -1)));
  return state.center.flatMap((c, slot) => (c !== null && !revealed.has(slot) ? [slot] : []));
}

/** ¿Ya se usó "Pedir carta" en este turno y la sala solo permite una vez? */
export function claimBlocked(state: GameState): boolean {
  return state.ruleSet.options.claimOncePerTurn === true && state.turn.claimUsed === true;
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
 * Cartas de la propia mano del jugador activo que, destapadas ahora, completan el set (p. ej. tiene
 * dos 5 seguidos y ya hay uno en la mesa). null si no alcanzan. El motor las destapa solo.
 */
export function ownCardsToComplete(state: GameState): HandPick[] | null {
  const value = currentTurnValue(state);
  const missing = state.ruleSet.setSize - state.turn.revealed.length;
  if (value === null || missing < 1) return null;
  return planHandCards(state, state.turn.player, value, missing);
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

/** Jugadas de mano, centro y deducción que quedan (sin contar "pasar"). */
function moves(state: GameState) {
  const { options } = state.ruleSet;
  const me = state.turn.player;
  const others = state.players.filter((p) => p !== me && hasHiddenCards(state, p));
  const selfAllowed = hasHiddenCards(state, me) && (options.allowSelfReveal || !hasOtherSources(state));
  return {
    others,
    selfAllowed,
    revealTargets: selfAllowed ? [...others, me] : others,
    centerSlots: hiddenCenterSlots(state),
    claimTargets: options.allowClaim && !claimBlocked(state) ? others : [],
  };
}

/**
 * Sin salida: no queda ninguna carta que revelar ni pedir (p. ej. ya le preguntaste a todos y el centro
 * está vacío). Se permite pasar aunque la sala lo tenga apagado, para que el turno nunca se bloquee.
 */
export function isDeadEnd(state: GameState): boolean {
  const m = moves(state);
  return m.revealTargets.length === 0 && m.centerSlots.length === 0 && m.claimTargets.length === 0;
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
  const m = moves(state);

  return {
    revealTargets: m.revealTargets,
    centerSlots: m.centerSlots,
    claimTargets: m.claimTargets,
    claimValue: currentTurnValue(state),
    canPass: (options.allowPass && state.turn.revealed.length >= options.minRevealsBeforePass) || isDeadEnd(state),
    completeWithOwn: m.selfAllowed ? ownCardToComplete(state) : null,
  };
}
