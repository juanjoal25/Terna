import type { GameState } from "../engine/index.js";

/** Lo que se guarda como texto JSON en `matches/{id}/secret/state` (solo el servidor lo lee). */
export interface StoredMatch {
  v: 1;
  game: GameState;
  roomId: string;
  startedAt: number;
  turnSeconds: number;
  /** Ritmo de la partida: nadie puede jugar antes de `startsAt` (así todos ven las cartas del fallo). */
  timing: { startsAt: number; deadline: number };
}

export interface MatchPlayerBase {
  seat: number;
  name: string;
  avatar: string;
  isBot: boolean;
  connected: boolean;
}

/** Nodo `matches/{matchId}` completo. Cada rama tiene sus propias reglas de lectura. */
export interface MatchNode {
  meta: {
    roomId: string;
    ruleSet: string;
    ruleVersion: number;
    status: "PLAYING" | "FINISHED";
    turnSeconds: number;
    createdAt: number;
    endedAt?: number;
    /** Reglas activas: la app las usa para mostrar solo los botones que aplican. */
    setSize: number;
    allowPass: boolean;
    allowClaim: boolean;
    allowSelfReveal: boolean;
    minRevealsBeforePass: number;
  };
  players: Record<string, MatchPlayerBase & { handCount: number; sets?: number[] }>;
  live: Record<string, unknown>;
  hands?: Record<string, number[]>;
  secret: { state: string };
}

/** Resumen de una partida terminada, para el historial y las estadísticas. */
export interface MatchRecord {
  matchId: string;
  roomId: string;
  ruleSet: string;
  reason: string;
  winners: string[];
  durationSec: number;
  endedAt: number;
  players: { uid: string; name: string; avatar: string; isBot: boolean; sets: number[]; winner: boolean }[];
}
