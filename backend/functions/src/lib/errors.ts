/** Códigos que Firebase Functions entiende (`HttpsError`). */
export type ErrorCode =
  | "invalid-argument"
  | "not-found"
  | "permission-denied"
  | "failed-precondition"
  | "already-exists"
  | "resource-exhausted"
  | "aborted";

/**
 * Error esperado de la lógica de salas y partidas. `reason` es un código estable que la app
 * traduce a un mensaje en español (p. ej. `ROOM_FULL`); `code` decide el tipo de error HTTP.
 */
export class GameError extends Error {
  constructor(
    readonly code: ErrorCode,
    readonly reason: string,
    message?: string,
  ) {
    super(message ?? reason);
    this.name = "GameError";
  }
}
