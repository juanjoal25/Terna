/**
 * Tipos del motor de reglas de TERNA!.
 *
 * El motor es puro: no conoce Firebase, ni red, ni relojes. Recibe un estado y una
 * acción y devuelve el estado nuevo más los eventos que la app debe animar.
 */

/** Valor de una carta (1..maxNumber). Las copias de un mismo número son indistinguibles. */
export type Card = number;
export type PlayerId = string;
export type HandEnd = "LOW" | "HIGH";

/** Reparto para un número de jugadores concreto. */
export interface DealSpec {
  hand: number;
  center: number;
}

export interface WinRules {
  /** Sets necesarios para ganar. `null` desactiva esta condición (modo Picante). */
  setsNeeded: number | null;
  /** Completar un set de alguno de estos valores es victoria instantánea (los 7). */
  instantWinValues: Card[];
  /**
   * Modo Picante: ganas con dos sets "enlazados", es decir, cuyos valores suman
   * o se restan exactamente este número (7 en Trio). `null` lo desactiva.
   */
  linkedPairTarget: number | null;
}

/** Reglas de turno configurables (casas de reglas). */
export interface TurnOptions {
  /** Permite revelar la carta menor/mayor de tu propia mano. */
  allowSelfReveal: boolean;
  /**
   * Permite "deducir": nombrar un número que otro jugador tiene como carta
   * menor o mayor. Si acierta, la entrega; si no, "-1000 de aura".
   */
  allowClaim: boolean;
  /** Si es true, al deducir hay que decir también si es su menor o su mayor. */
  claimRequiresEnd: boolean;
  /** Si es true, deducir mal termina el turno. */
  claimFailEndsTurn: boolean;
  /** Permite pasar el turno voluntariamente. */
  allowPass: boolean;
  /** Cartas reveladas necesarias en el turno antes de poder pasar. */
  minRevealsBeforePass: number;
  /** Si es true, tras completar un set el mismo jugador sigue jugando. */
  continueAfterSet: boolean;
}

export interface RuleSet {
  id: string;
  version: number;
  /** Los números van de 1 a maxNumber. */
  maxNumber: number;
  /** Copias de cada número en el mazo. */
  copies: number;
  /** Cartas iguales que forman un set (3 = terna, 4 = cuarta). */
  setSize: number;
  /** Reparto por número de jugadores: a más jugadores, menos cartas al centro. */
  deal: Record<number, DealSpec>;
  win: WinRules;
  options: TurnOptions;
}

/** Una carta revelada durante el turno actual. Sigue en su sitio hasta que se forma el set. */
export type Reveal =
  | {
      source: "HAND";
      owner: PlayerId;
      /** Posición en la mano ordenada del dueño (uso interno del motor). */
      index: number;
      end: HandEnd;
      value: Card;
      /** true si se obtuvo deduciendo (CLAIM) en lugar de revelando. */
      claimed: boolean;
    }
  | { source: "CENTER"; slot: number; value: Card };

export interface TurnState {
  player: PlayerId;
  number: number;
  revealed: Reveal[];
}

export type WinReason = "INSTANT" | "SETS" | "LINKED" | "EXHAUSTED";

export interface GameResult {
  winners: PlayerId[];
  reason: WinReason;
}

export interface GameState {
  ruleSet: RuleSet;
  /** Orden de asientos; el turno avanza en este orden. */
  players: PlayerId[];
  /** Manos ordenadas de menor a mayor. Privadas: nunca se envían enteras a otros jugadores. */
  hands: Record<PlayerId, Card[]>;
  /** Huecos del centro; `null` = hueco vacío (la carta ya formó un set). */
  center: (Card | null)[];
  /** Valores de los sets completados por cada jugador. */
  sets: Record<PlayerId, Card[]>;
  turn: TurnState;
  status: "PLAYING" | "FINISHED";
  result: GameResult | null;
  /** Secuencia monotónica de eventos; la app la usa para ordenar animaciones. */
  seq: number;
}

export type Action =
  | { type: "REVEAL_HAND"; player: PlayerId; target: PlayerId; end: HandEnd }
  | { type: "REVEAL_CENTER"; player: PlayerId; slot: number }
  /** `count` (por defecto 1): cuántas cartas seguidas de `value` se afirma que tiene el rival. Todo o nada. */
  | { type: "CLAIM"; player: PlayerId; target: PlayerId; value: Card; end?: HandEnd; count?: number }
  | { type: "PASS"; player: PlayerId }
  /** Se acabó el tiempo del turno. Solo la genera el servidor: el turno pasa sin penalizar las cartas. */
  | { type: "TIMEOUT"; player: PlayerId };

type EventBody =
  | { type: "TURN_STARTED"; player: PlayerId; number: number }
  | { type: "CARD_REVEALED"; player: PlayerId; reveal: Reveal }
  | { type: "CLAIM_FAILED"; player: PlayerId; target: PlayerId; value: Card; taunt: "AURA_MINUS_1000" }
  | { type: "TURN_FAILED"; player: PlayerId; revealed: Reveal[] }
  | { type: "TURN_PASSED"; player: PlayerId; revealed: Reveal[] }
  | { type: "TURN_TIMEOUT"; player: PlayerId; revealed: Reveal[] }
  | { type: "SET_COMPLETED"; player: PlayerId; value: Card; cards: Reveal[] }
  | { type: "GAME_OVER"; result: GameResult };

export type GameEvent = EventBody & { seq: number };
export type GameEventBody = EventBody;

/** Errores de validación: la acción se rechaza y el estado no cambia (no es un fallo de juego). */
export type EngineErrorCode =
  | "GAME_OVER"
  | "NOT_YOUR_TURN"
  | "UNKNOWN_PLAYER"
  | "SELF_REVEAL_DISABLED"
  | "NO_HIDDEN_CARDS"
  | "INVALID_SLOT"
  | "SLOT_EMPTY"
  | "SLOT_ALREADY_REVEALED"
  | "CLAIM_DISABLED"
  | "CLAIM_SELF"
  | "CLAIM_END_REQUIRED"
  | "CLAIM_VALUE_MISMATCH"
  | "INVALID_VALUE"
  | "INVALID_COUNT"
  | "PASS_DISABLED"
  | "PASS_TOO_EARLY";

export type ApplyResult =
  | { ok: true; state: GameState; events: GameEvent[] }
  | { ok: false; error: EngineErrorCode };
