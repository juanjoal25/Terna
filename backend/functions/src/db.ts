import { getApps, initializeApp } from "firebase-admin/app";
import { getDatabase } from "firebase-admin/database";
import { getFirestore } from "firebase-admin/firestore";
import { secureRng } from "./engine/index.js";
import { firestoreArchive, firestoreProfiles } from "./match/archive.js";
import type { Deps } from "./rooms/types.js";

/**
 * Dependencias reales (Realtime Database, Firestore y barajado criptográfico).
 * Se crean la primera vez que alguien las pide, no al cargar el módulo: así el análisis de
 * despliegue de Firebase no necesita credenciales.
 */
let deps: Deps | undefined;

export function getDeps(): Deps {
  if (!deps) {
    const app = getApps()[0] ?? initializeApp();
    const firestore = getFirestore(app);
    deps = {
      db: getDatabase(app),
      getProfile: firestoreProfiles(firestore),
      archive: firestoreArchive(firestore),
      now: Date.now,
      rng: secureRng(),
    };
  }
  return deps;
}
