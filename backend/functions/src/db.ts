import { initializeApp, getApps } from "firebase-admin/app";
import { getDatabase } from "firebase-admin/database";
import { getFirestore } from "firebase-admin/firestore";

// Inicialización singleton de Firebase Admin SDK
export const app = getApps().length === 0 ? initializeApp() : getApps()[0]!;
export const rtdb = getDatabase(app);
export const firestore = getFirestore(app);

/**
 * Referencias fuertemente tipadas de Realtime Database.
 * Permiten al backend interactuar con la base de datos sin errores tipográficos en las rutas.
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
  presence: (uid: string) => rtdb.ref(`presence/${uid}`),
  queue: (queueKey: string) => rtdb.ref(`queue/${queueKey}`),
};

/**
 * Referencias a colecciones y documentos de Cloud Firestore.
 */
export const firestoreCols = {
  users: () => firestore.collection("users"),
  user: (uid: string) => firestore.doc(`users/${uid}`),
  userMatchHistory: (uid: string) => firestore.collection(`users/${uid}/matchHistory`),
  matchHistory: () => firestore.collection("matchHistory"),
  leaderboards: (season: string) => firestore.collection(`leaderboards/${season}/entries`),
};
