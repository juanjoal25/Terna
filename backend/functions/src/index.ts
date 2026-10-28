/**
 * TERNA! — Punto de entrada de Cloud Functions (2.ª generación, us-central1)
 *
 * Aquí solo se exportan funciones: Firebase despliega cada export como un endpoint.
 * La lógica vive en `rooms/` y `match/`; el motor de reglas, en `engine/`.
 */
import { setGlobalOptions } from "firebase-functions/v2";
import { onCall, HttpsError, type CallableRequest } from "firebase-functions/v2/https";
import { getDeps } from "./db.js";
import { GameError } from "./lib/errors.js";
import { gameAction as runGameAction, startMatch as runStartMatch } from "./match/matchService.js";
import { createRoom as runCreateRoom, joinRoom as runJoinRoom, kickPlayer as runKickPlayer, leaveRoom as runLeaveRoom } from "./rooms/roomService.js";
import type { Deps } from "./rooms/types.js";

// Misma región que Realtime Database. `maxInstances` pone un tope al gasto si algo se descontrola.
setGlobalOptions({ region: "us-central1", maxInstances: 10 });

/**
 * Convierte una operación de servicio en un callable: exige sesión, pasa el UID del usuario
 * (nunca uno enviado por el cliente) y traduce los `GameError` a errores que la app entiende
 * leyendo `details.reason`.
 */
function callable<T>(operation: (deps: Deps, uid: string, data: unknown) => Promise<T>) {
  return onCall(async (request: CallableRequest): Promise<T> => {
    if (!request.auth) throw new HttpsError("unauthenticated", "Inicia sesión para usar TERNA!");
    try {
      return await operation(getDeps(), request.auth.uid, request.data);
    } catch (error) {
      if (error instanceof GameError) throw new HttpsError(error.code, error.message, { reason: error.reason });
      console.error("Error inesperado", error);
      throw new HttpsError("internal", "Algo salió mal. Inténtalo de nuevo.", { reason: "INTERNAL" });
    }
  });
}

/** Health check para verificar desde Android que la app llega al backend. */
export const ping = onCall((request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Inicia sesión para usar TERNA!");
  return { status: "ok", region: "us-central1", timestamp: Date.now() };
});

// Salas
export const createRoom = callable(runCreateRoom);
export const joinRoom = callable(runJoinRoom);
export const leaveRoom = callable((deps, uid) => runLeaveRoom(deps, uid));
export const kickPlayer = callable(runKickPlayer);

// Partidas
export const startMatch = callable((deps, uid) => runStartMatch(deps, uid));
export const gameAction = callable(runGameAction);
