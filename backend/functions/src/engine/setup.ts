import { validateRuleSet } from "./rulesets.js";
import { shuffle, type Rng } from "./rng.js";
import type { Card, GameEvent, GameState, PlayerId, RuleSet } from "./types.js";

export interface CreateGameOptions {
  rng: Rng;
  /** Quién empieza. Si se omite, se sortea con `rng`. */
  firstPlayer?: PlayerId;
}

export function buildDeck(ruleSet: RuleSet): Card[] {
  const deck: Card[] = [];
  for (let value = 1; value <= ruleSet.maxNumber; value++) {
    for (let c = 0; c < ruleSet.copies; c++) deck.push(value);
  }
  return deck;
}

/** Baraja, reparte según el nº de jugadores y abre el primer turno. */
export function createGame(
  ruleSet: RuleSet,
  players: PlayerId[],
  options: CreateGameOptions,
): { state: GameState; events: GameEvent[] } {
  validateRuleSet(ruleSet);

  if (new Set(players).size !== players.length) throw new Error("Hay jugadores repetidos");
  const deal = ruleSet.deal[players.length];
  if (!deal) {
    const allowed = Object.keys(ruleSet.deal).join(", ");
    throw new Error(`${ruleSet.id} admite ${allowed} jugadores, no ${players.length}`);
  }

  const deck = shuffle(buildDeck(ruleSet), options.rng);
  const hands: Record<PlayerId, Card[]> = {};
  const sets: Record<PlayerId, Card[]> = {};
  players.forEach((p, seat) => {
    hands[p] = deck.slice(seat * deal.hand, (seat + 1) * deal.hand).sort((a, b) => a - b);
    sets[p] = [];
  });
  const center = deck.slice(players.length * deal.hand);

  const first = options.firstPlayer ?? players[Math.floor(options.rng() * players.length)]!;
  if (!players.includes(first)) throw new Error(`firstPlayer ${first} no está en la partida`);

  const state: GameState = {
    ruleSet,
    players: [...players],
    hands,
    center,
    sets,
    turn: { player: first, number: 1, revealed: [] },
    status: "PLAYING",
    result: null,
    seq: 1,
  };
  return { state, events: [{ seq: 1, type: "TURN_STARTED", player: first, number: 1 }] };
}
