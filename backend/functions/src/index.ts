/**
 * TERNA! — Cloud Functions Entrypoint (2nd Gen, us-central1)
 *
 * Este archivo centraliza la conexión de Firebase con el backend.
 * Las funciones se despliegan en us-central1 para garantizar mínima latencia
 * con Realtime Database.
 */
import { setGlobalOptions } from "firebase-functions/v2";
import { onCall } from "firebase-functions/v2/https";
import { rtdb, firestore, rtdbRefs, firestoreCols } from "./db.js";

// Configuración regional global para 2ª generación
setGlobalOptions({ region: "us-central1" });

// Exportar utilidades de conexión a base de datos para que los módulos las consuman
export { rtdb, firestore, rtdbRefs, firestoreCols };

/**
 * Health check básico para verificar conectividad desde el cliente Android
 */
export const ping = onCall(async () => {
  return {
    status: "ok",
    region: "us-central1",
    timestamp: Date.now(),
  };
});
