import { initializeApp, getApps } from "firebase-admin/app";
import { getDatabase } from "firebase-admin/database";
import { getFirestore } from "firebase-admin/firestore";
import type { GameState } from "./engine/index.js";

// Inicialización singleton de Firebase Admin SDK
export const app = getApps().length === 0 ? initializeApp() : getApps()[0]!;
export const rtdb = getDatabase(app);
export const firestore = getFirestore(app);

/**
 * Referencias de Realtime Database.
 * Evitan errores tipográficos en las rutas (ver docs/ESQUEMA_BASE_DATOS_FIREBASE.md §3).
 */
export const rtdbRefs = {
  rooms: () => rtdb.ref("rooms"),
  room: (roomId: string) => rtdb.ref(`rooms/${roomId}`),
  openRooms: () => rtdb.ref("openRooms"),
  openRoom: (roomId: string) => rtdb.ref(`openRooms/${roomId}`),
  roomCodes: () => rtdb.ref("roomCodes"),
  roomCode: (code: string) => rtdb.ref(`roomCodes/${code}`),
  matches: () => rtdb.ref("matches"),
  match: (matchId: string) => rtdb.ref(`matches/${matchId}`),
  matchLive: (matchId: string) => rtdb.ref(`matches/${matchId}/live`),
  matchPlayers: (matchId: string) => rtdb.ref(`matches/${matchId}/players`),
  hands: (matchId: string) => rtdb.ref(`hands/${matchId}`),
  hand: (matchId: string, uid: string) => rtdb.ref(`hands/${matchId}/${uid}`),
  secret: (matchId: string) => rtdb.ref(`secret/${matchId}`),
  activeMatch: (uid: string) => rtdb.ref(`activeMatch/${uid}`),
  presence: (uid: string) => rtdb.ref(`presence/${uid}`),
  queue: (queueKey: string) => rtdb.ref(`queue/${queueKey}`),
};

/**
 * El estado completo del motor se guarda como texto JSON en `secret/{matchId}/state`.
 * RTDB borra los `null` del final de un arreglo (el centro usa `null` para huecos vacíos),
 * así que guardarlo como árbol cambiaría el tamaño de la mesa al leerlo.
 */
export const serializeState = (state: GameState): string => JSON.stringify(state);
export const deserializeState = (raw: string): GameState => JSON.parse(raw) as GameState;

/**
 * Referencias de Cloud Firestore.
 */
export const firestoreCols = {
  users: () => firestore.collection("users"),
  user: (uid: string) => firestore.doc(`users/${uid}`),
  userMatchHistory: (uid: string) => firestore.collection(`users/${uid}/matchHistory`),
  matchArchive: () => firestore.collection("matchArchive"),
  leaderboards: (season: string) => firestore.collection(`leaderboards/${season}/entries`),
};
