import { getApps, initializeApp } from "firebase-admin/app";
import { getDatabase } from "firebase-admin/database";
import { getFirestore } from "firebase-admin/firestore";
import { seededRng } from "../engine/index.js";
import { firestoreArchive } from "../match/archive.js";
import type { Deps } from "../rooms/types.js";

/**
 * Entorno de pruebas contra los emuladores de Realtime Database y Firestore.
 * `firebase emulators:exec` define FIREBASE_DATABASE_EMULATOR_HOST y FIRESTORE_EMULATOR_HOST;
 * el SDK Admin los detecta solo, así que nunca se toca un proyecto real.
 */
export interface TestEnv {
  deps: Deps;
  /** Reloj controlable: las pausas de la mesa se saltan avanzándolo. */
  clock: { now: number; advance: (ms: number) => void };
  names: Record<string, string>;
  reset: () => Promise<void>;
}

export function createTestEnv(): TestEnv {
  if (!process.env.FIREBASE_DATABASE_EMULATOR_HOST || !process.env.FIRESTORE_EMULATOR_HOST) {
    throw new Error("Corre estas pruebas con `npm test:functions` desde backend/rules-tests (levanta los emuladores).");
  }
  const app = getApps()[0] ?? initializeApp({ projectId: "demo-terna", databaseURL: "https://demo-terna-default-rtdb.firebaseio.com" });
  const db = getDatabase(app);
  const firestore = getFirestore(app);

  const clock = {
    now: 1_800_000_000_000,
    advance(ms: number) {
      this.now += ms;
    },
  };
  const names: Record<string, string> = {};

  const deps: Deps = {
    db,
    getProfile: async (uid) => ({ displayName: names[uid] ?? uid, avatar: "jaguar_01" }),
    archive: firestoreArchive(firestore),
    now: () => clock.now,
    rng: seededRng(7),
  };

  const reset = async () => {
    await db.ref().remove();
    await fetch(`http://${process.env.FIRESTORE_EMULATOR_HOST}/emulator/v1/projects/demo-terna/databases/(default)/documents`, { method: "DELETE" });
    clock.now = 1_800_000_000_000;
  };
  return { deps, clock, names, reset };
}

export async function read<T = unknown>(deps: Deps, path: string): Promise<T | null> {
  return ((await deps.db.ref(path).get()).val() as T | null) ?? null;
}

/** Ejecuta una operación que debe fallar y devuelve el `reason` del error. */
export async function reasonOf(promise: Promise<unknown>): Promise<string> {
  try {
    await promise;
  } catch (error) {
    return (error as { reason?: string }).reason ?? `SIN_REASON:${String(error)}`;
  }
  return "NO_FALLÓ";
}
