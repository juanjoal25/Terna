import type { Reference } from "firebase-admin/database";
import { GameError } from "./errors.js";

type Json = Record<string, unknown>;

const MAX_STALE_RETRIES = 3;

/**
 * Transacción sobre un nodo de Realtime Database con el patrón correcto del Admin SDK:
 *  - La primera llamada suele recibir `null` (caché vacía). Se devuelve `null` para que Firebase
 *    la reintente con el valor real del servidor.
 *  - `update` puede lanzar un `GameError`: se aborta la transacción y se relanza al llamador.
 *  - `update` puede ejecutarse varias veces: debe ser pura (sin efectos secundarios).
 *  - Un rechazo se confirma con una lectura fresca del servidor: si la primera pasada vio un dato
 *    desactualizado de la caché (otra instancia acababa de escribir), no se le devuelve al jugador un
 *    error falso; se repite la transacción.
 *
 * Devuelve el valor guardado tras la transacción (`null` si el nodo no existe o se borró).
 */
export async function transact<T = Json>(ref: Reference, update: (current: T) => T | null): Promise<T | null> {
  for (let attempt = 1; ; attempt++) {
    let failure: GameError | null = null;

    const result = await ref.transaction(
      (current: T | null) => {
        failure = null;
        if (current === null) return null; // se reintenta con el dato real
        try {
          return update(current);
        } catch (error) {
          if (error instanceof GameError) {
            failure = error;
            return undefined; // aborta
          }
          throw error;
        }
      },
      undefined,
      false,
    );

    if (!failure) return (result.snapshot.val() as T | null) ?? null;

    // ¿El rechazo se sostiene con los datos reales del servidor? (`get` no usa la caché)
    const fresh = (await ref.get()).val() as T | null;
    if (fresh === null || attempt >= MAX_STALE_RETRIES) throw failure;
    update(fresh); // si también falla con datos frescos, el error es real y se propaga
    // Con datos frescos sí sería válido: la primera pasada vio algo viejo. Se vuelve a intentar.
  }
}

/** Quita `undefined` (Realtime Database los rechaza) y deja el resto intacto. */
export function clean<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T;
}
