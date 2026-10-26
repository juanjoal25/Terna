/**
 * TERNA! — Punto de entrada de Cloud Functions (2.ª generación, us-central1)
 *
 * Aquí solo se exportan funciones: Firebase despliega cada export como un endpoint.
 * Los módulos que necesiten base de datos importan `./db.js` directamente.
 */
import { setGlobalOptions } from "firebase-functions/v2";
import { HttpsError, onCall } from "firebase-functions/v2/https";

// Misma región que Realtime Database. `maxInstances` pone un tope al gasto si algo se descontrola.
setGlobalOptions({ region: "us-central1", maxInstances: 10 });

/** Health check para verificar desde Android que la app llega al backend. */
export const ping = onCall((request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Inicia sesión para usar TERNA!");
  return {
    status: "ok",
    region: "us-central1",
    timestamp: Date.now(),
  };
});
