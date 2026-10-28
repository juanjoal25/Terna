import type { Database } from "firebase-admin/database";
import type { Rng, TurnOptions } from "../engine/index.js";
import type { MatchRecord } from "../match/types.js";

export type RoomType = "PUBLIC" | "PRIVATE" | "QUICKPLAY";
export type RoomStatus = "WAITING" | "STARTING" | "IN_MATCH";

export interface RoomMember {
  name: string;
  avatar: string;
  joinedAt: number;
  host: boolean;
  ready: boolean;
}

export interface RoomConfig {
  ruleSet: string;
  turnSeconds: number;
  fillWithBots: boolean;
  /** Solo las opciones que el anfitrión cambió respecto al modo base. */
  options?: Partial<TurnOptions>;
}

/** Nodo `rooms/{roomId}` de Realtime Database. Los `null` no se guardan (RTDB los omite). */
export interface RoomData {
  name: string;
  type: RoomType;
  hostUid: string;
  code: string;
  maxPlayers: number;
  status: RoomStatus;
  currentMatchId?: string;
  createdAt: number;
  updatedAt: number;
  config: RoomConfig;
  members: Record<string, RoomMember>;
  /** Jugadores expulsados: no pueden volver a entrar a esta sala. */
  banned?: Record<string, true>;
}

/** Entrada de `openRooms/{roomId}`: lo que muestra la lista "Salas disponibles". */
export interface OpenRoomEntry {
  name: string;
  code: string;
  hostName: string;
  players: number;
  max: number;
  ruleSet: string;
  createdAt: number;
}

/** Nodo `active/{uid}`: dónde está jugando cada usuario (la app lo lee al abrir para reconectar). */
export interface ActiveEntry {
  roomId: string;
  matchId?: string;
}

export interface PlayerProfile {
  displayName: string;
  avatar: string;
}

/** Dependencias inyectables: así los servicios se prueban con los emuladores sin tocar producción. */
export interface Deps {
  db: Database;
  getProfile: (uid: string) => Promise<PlayerProfile>;
  /** Guarda historial y estadísticas al terminar una partida (Firestore). */
  archive: (record: MatchRecord) => Promise<void>;
  now: () => number;
  /** Generador del barajado. En producción es criptográfico; los tests pasan uno con semilla. */
  rng: Rng;
}
