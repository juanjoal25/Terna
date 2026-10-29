import { askBlocked, currentTurnValue, hasHiddenCards, hasOtherSources, hiddenCount, hiddenEndIndex, isDeadEnd, ownCardsToComplete, planHandCards } from "./queries.js";
import type {
  Action,
  ApplyResult,
  Card,
  EngineErrorCode,
  GameEvent,
  GameEventBody,
  GameResult,
  GameState,
  HandEnd,
  MismatchPick,
  PlayerId,
  Reveal,
} from "./types.js";
import { isExhausted, mostSets, winReasonAfterSet } from "./victory.js";

/** Acumula eventos numerados mientras se resuelve una acción. */
class Emitter {
  readonly events: GameEvent[] = [];
  constructor(private readonly state: GameState) {}
  emit(body: GameEventBody): void {
    this.state.seq += 1;
    this.events.push({ ...body, seq: this.state.seq });
  }
}

const reject = (error: EngineErrorCode): ApplyResult => ({ ok: false, error });

/**
 * Aplica una acción del jugador activo. Nunca muta `state`: devuelve uno nuevo.
 * Si la acción no es válida devuelve `{ ok: false }` y el estado no cambia.
 */
export function applyAction(state: GameState, action: Action): ApplyResult {
  if (state.status !== "PLAYING") return reject("GAME_OVER");
  if (!state.players.includes(action.player)) return reject("UNKNOWN_PLAYER");
  if (action.player !== state.turn.player) return reject("NOT_YOUR_TURN");

  const next = structuredClone(state);
  const out = new Emitter(next);
  const turnBefore = state.turn.number;
  let error: EngineErrorCode | null;

  switch (action.type) {
    case "REVEAL_HAND":
      error = revealHand(next, out, action.target, action.end);
      break;
    case "REVEAL_CENTER":
      error = revealCenter(next, out, action.slot);
      break;
    case "CLAIM":
      error = claim(next, out, action.target, action.value, action.end, action.count ?? 1);
      break;
    case "PASS":
      error = pass(next, out);
      break;
    case "TIMEOUT":
      out.emit({ type: "TURN_TIMEOUT", player: next.turn.player, revealed: next.turn.revealed });
      endTurn(next, out);
      error = null;
      break;
  }

  if (error) return reject(error);

  // Si el turno sigue abierto y las cartas de tu propia mano completan el set, se destapan solas.
  if (next.status === "PLAYING" && next.turn.number === turnBefore && out.events.some((e) => e.type === "CARD_REVEALED")) {
    completeWithOwnHand(next, out);
  }
  return { ok: true, state: next, events: out.events };
}

function revealHand(state: GameState, out: Emitter, target: PlayerId, end: HandEnd): EngineErrorCode | null {
  if (!state.players.includes(target)) return "UNKNOWN_PLAYER";
  if (target === state.turn.player && !state.ruleSet.options.allowSelfReveal && hasOtherSources(state)) {
    return "SELF_REVEAL_DISABLED";
  }
  const index = hiddenEndIndex(state, target, end);
  if (index === null) return "NO_HIDDEN_CARDS";
  // Preguntar a un rival solo se puede una vez por turno. Revelar tu propia mano no cuenta (hace falta para cerrar la terna).
  if (target !== state.turn.player) {
    if (askBlocked(state)) return "ASK_ALREADY_USED";
    if (state.ruleSet.options.askOncePerTurn) state.turn.askUsed = true;
  }

  const value = state.hands[target]![index]!;
  resolveReveal(state, out, { source: "HAND", owner: target, index, end, value, claimed: false });
  return null;
}

function revealCenter(state: GameState, out: Emitter, slot: number): EngineErrorCode | null {
  if (!Number.isInteger(slot) || slot < 0 || slot >= state.center.length) return "INVALID_SLOT";
  const value = state.center[slot];
  if (value === null || value === undefined) return "SLOT_EMPTY";
  if (state.turn.revealed.some((r) => r.source === "CENTER" && r.slot === slot)) return "SLOT_ALREADY_REVEALED";

  resolveReveal(state, out, { source: "CENTER", slot, value });
  return null;
}

/**
 * "Deducir": el jugador afirma que `target` tiene `count` cartas de `value` seguidas en sus extremos
 * (menor o mayor). Si acierta, se le entregan (cuentan como reveladas). Si no, "-1000 de aura".
 */
function claim(state: GameState, out: Emitter, target: PlayerId, value: Card, end: HandEnd | undefined, count: number): EngineErrorCode | null {
  const { options, maxNumber, setSize } = state.ruleSet;
  if (!options.allowClaim) return "CLAIM_DISABLED";
  if (!state.players.includes(target)) return "UNKNOWN_PLAYER";
  if (target === state.turn.player) return "CLAIM_SELF";
  if (!Number.isInteger(value) || value < 1 || value > maxNumber) return "INVALID_VALUE";
  if (options.claimRequiresEnd && !end) return "CLAIM_END_REQUIRED";
  if (!hasHiddenCards(state, target)) return "NO_HIDDEN_CARDS";

  // Si ya se está persiguiendo un número, deducir otro no tiene sentido: se rechaza sin penalizar.
  const chasing = currentTurnValue(state);
  if (chasing !== null && chasing !== value) return "CLAIM_VALUE_MISMATCH";
  // No se puede pedir más de lo que falta para la terna ni más cartas de las que el rival aún tiene ocultas.
  if (!Number.isInteger(count) || count < 1 || count > setSize - state.turn.revealed.length || count > hiddenCount(state, target)) {
    return "INVALID_COUNT";
  }
  // Sin "varias de golpe", cada petición saca una sola carta: las demás hay que descubrirlas después.
  if (count > 1 && !options.allowMultiClaim) return "INVALID_COUNT";

  const picks = planHandCards(state, target, value, count, end);
  if (picks) {
    for (const pick of picks) resolveReveal(state, out, { source: "HAND", owner: target, index: pick.index, end: pick.end, value, claimed: true });
    return null;
  }

  out.emit({ type: "CLAIM_FAILED", player: state.turn.player, target, value, taunt: "AURA_MINUS_1000" });
  if (options.claimFailEndsTurn) {
    out.emit({ type: "TURN_FAILED", player: state.turn.player, revealed: state.turn.revealed });
    endTurn(state, out);
  }
  return null;
}

/** Destapa las cartas de tu propia mano que faltan para la terna (si las tienes seguidas en un extremo). */
function completeWithOwnHand(state: GameState, out: Emitter): void {
  if (!state.ruleSet.options.allowSelfReveal) return;
  const picks = ownCardsToComplete(state);
  if (!picks) return;
  const me = state.turn.player;
  for (const pick of picks) {
    resolveReveal(state, out, { source: "HAND", owner: me, index: pick.index, end: pick.end, value: state.hands[me]![pick.index]!, claimed: false });
  }
}

function pass(state: GameState, out: Emitter): EngineErrorCode | null {
  const { options } = state.ruleSet;
  // Si ya no queda nada que revelar ni pedir, siempre se puede pasar: el turno nunca se bloquea.
  if (!isDeadEnd(state)) {
    if (!options.allowPass) return "PASS_DISABLED";
    if (state.turn.revealed.length < options.minRevealsBeforePass) return "PASS_TOO_EARLY";
  }

  out.emit({ type: "TURN_PASSED", player: state.turn.player, revealed: state.turn.revealed });
  endTurn(state, out);
  return null;
}

/** Decide qué pasa tras destapar una carta: sigue, falla o completa un set. */
function resolveReveal(state: GameState, out: Emitter, reveal: Reveal): void {
  const player = state.turn.player;
  const chasing = currentTurnValue(state);
  const mismatch = chasing !== null && reveal.value !== chasing;

  if (mismatch && state.ruleSet.options.hideMismatch) {
    // La carta no se muestra a nadie (tampoco se guarda): solo se avisa de dónde estaba y que no era la correcta.
    out.emit({ type: "TURN_FAILED", player, revealed: state.turn.revealed, mismatch: toMismatchPick(reveal) });
    endTurn(state, out);
    return;
  }

  state.turn.revealed.push(reveal);
  out.emit({ type: "CARD_REVEALED", player, reveal });

  if (mismatch) {
    // Las cartas reveladas "vuelven a su sitio": como nunca se movieron, basta con olvidarlas.
    out.emit({ type: "TURN_FAILED", player, revealed: state.turn.revealed });
    endTurn(state, out);
    return;
  }

  if (state.turn.revealed.length === state.ruleSet.setSize) completeSet(state, out);
}

function toMismatchPick(reveal: Reveal): MismatchPick {
  return reveal.source === "HAND" ? { source: "HAND", owner: reveal.owner, end: reveal.end } : { source: "CENTER", slot: reveal.slot };
}

function completeSet(state: GameState, out: Emitter): void {
  const player = state.turn.player;
  const cards = state.turn.revealed;
  const value = cards[0]!.value;

  // Retira las cartas: en cada mano, de mayor a menor índice para no desplazar las demás.
  const byOwner = new Map<PlayerId, number[]>();
  for (const r of cards) {
    if (r.source === "CENTER") state.center[r.slot] = null;
    else byOwner.set(r.owner, [...(byOwner.get(r.owner) ?? []), r.index]);
  }
  for (const [owner, indexes] of byOwner) {
    for (const i of indexes.sort((a, b) => b - a)) state.hands[owner]!.splice(i, 1);
  }

  state.sets[player]!.push(value);
  state.turn.revealed = [];
  delete state.turn.askUsed;
  out.emit({ type: "SET_COMPLETED", player, value, cards });

  const reason = winReasonAfterSet(state.ruleSet, state.sets[player]!, value);
  if (reason) return finish(state, out, { winners: [player], reason });
  if (isExhausted(state)) return finish(state, out, { winners: mostSets(state), reason: "EXHAUSTED" });

  if (!state.ruleSet.options.continueAfterSet) endTurn(state, out);
}

function endTurn(state: GameState, out: Emitter): void {
  const seat = state.players.indexOf(state.turn.player);
  const player = state.players[(seat + 1) % state.players.length]!;
  state.turn = { player, number: state.turn.number + 1, revealed: [] };
  out.emit({ type: "TURN_STARTED", player, number: state.turn.number });
}

function finish(state: GameState, out: Emitter, result: GameResult): void {
  state.status = "FINISHED";
  state.result = result;
  state.turn.revealed = [];
  out.emit({ type: "GAME_OVER", result });
}
