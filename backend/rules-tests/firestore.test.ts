import { readFileSync } from "node:fs";
import { assertFails, assertSucceeds, initializeTestEnvironment, type RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { deleteDoc, doc, getDoc, serverTimestamp, setDoc, updateDoc } from "firebase/firestore";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";

let env: RulesTestEnvironment;

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-terna",
    firestore: { rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8"), host: "127.0.0.1", port: 8080 },
  });
});
afterAll(() => env.cleanup());
beforeEach(() => env.clearFirestore());

const guest = (uid: string) => env.authenticatedContext(uid, { firebase: { sign_in_provider: "anonymous" } }).firestore();
const google = (uid: string) => env.authenticatedContext(uid, { firebase: { sign_in_provider: "google.com" } }).firestore();

const newProfile = (extra: Record<string, unknown> = {}) => ({
  displayName: "Guest001",
  avatar: "jaguar_01",
  anonymous: true,
  createdAt: serverTimestamp(),
  lastLoginAt: serverTimestamp(),
  level: 1,
  xp: 0,
  coins: 0,
  ...extra,
});

async function seedProfile(uid: string, data: Record<string, unknown> = {}) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), `users/${uid}`), {
      displayName: "Parra", avatar: "jaguar_01", anonymous: true, level: 3, xp: 120, coins: 50, ...data,
    });
  });
}

describe("users: creación", () => {
  it("un invitado crea su perfil inicial", async () => {
    await assertSucceeds(setDoc(doc(guest("ana"), "users/ana"), newProfile()));
  });

  it("no puede crear el perfil con estadísticas inventadas", async () => {
    await assertFails(setDoc(doc(guest("ana"), "users/ana"), newProfile({ stats: { TERNA_CLASSIC: { wins: 9999 } } })));
  });

  it("no puede empezar con nivel, xp o monedas distintos a los iniciales", async () => {
    await assertFails(setDoc(doc(guest("ana"), "users/ana"), newProfile({ level: 50 })));
    await assertFails(setDoc(doc(guest("ana"), "users/ana"), newProfile({ coins: 1000 })));
  });

  it("no puede crear el perfil de otro usuario", async () => {
    await assertFails(setDoc(doc(guest("ana"), "users/beto"), newProfile()));
  });

  it("`anonymous` debe coincidir con el tipo de sesión", async () => {
    await assertFails(setDoc(doc(guest("ana"), "users/ana"), newProfile({ anonymous: false })));
    await assertSucceeds(setDoc(doc(google("ana"), "users/ana"), newProfile({ anonymous: false })));
  });

  it("valida nombre, avatar y fechas del servidor", async () => {
    await assertFails(setDoc(doc(guest("ana"), "users/ana"), newProfile({ displayName: "" })));
    await assertFails(setDoc(doc(guest("ana"), "users/ana"), newProfile({ displayName: "x".repeat(21) })));
    await assertFails(setDoc(doc(guest("ana"), "users/ana"), newProfile({ avatar: "../../hack" })));
    await assertFails(setDoc(doc(guest("ana"), "users/ana"), newProfile({ createdAt: new Date(2000, 0, 1) })));
  });
});

describe("users: actualización y borrado", () => {
  it("cambia su nombre, avatar y última conexión", async () => {
    await seedProfile("ana");
    await assertSucceeds(updateDoc(doc(guest("ana"), "users/ana"), { displayName: "Ana", avatar: "condor_02", lastLoginAt: serverTimestamp() }));
  });

  it("no puede tocar xp, monedas, nivel ni estadísticas", async () => {
    await seedProfile("ana");
    const ref = doc(guest("ana"), "users/ana");
    await assertFails(updateDoc(ref, { xp: 99999 }));
    await assertFails(updateDoc(ref, { coins: 99999 }));
    await assertFails(updateDoc(ref, { level: 99 }));
    await assertFails(updateDoc(ref, { "stats.TERNA_CLASSIC.wins": 500 }));
  });

  it("no puede agregar campos nuevos", async () => {
    await seedProfile("ana");
    await assertFails(updateDoc(doc(guest("ana"), "users/ana"), { isAdmin: true }));
  });

  it("`anonymous` pasa a false solo tras vincular la cuenta", async () => {
    await seedProfile("ana");
    await assertFails(updateDoc(doc(guest("ana"), "users/ana"), { anonymous: false }));
    await assertSucceeds(updateDoc(doc(google("ana"), "users/ana"), { anonymous: false }));
  });

  it("no puede editar el perfil de otro ni borrar el suyo desde el cliente", async () => {
    await seedProfile("ana");
    await assertFails(updateDoc(doc(guest("beto"), "users/ana"), { displayName: "Hackeado" }));
    await assertFails(deleteDoc(doc(guest("ana"), "users/ana")));
  });

  it("los perfiles se leen solo con sesión", async () => {
    await seedProfile("ana");
    await assertSucceeds(getDoc(doc(guest("beto"), "users/ana")));
    await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), "users/ana")));
  });
});

describe("historial y ranking", () => {
  beforeEach(async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const db = ctx.firestore();
      await setDoc(doc(db, "users/ana/matchHistory/m1"), { result: "VICTORY" });
      await setDoc(doc(db, "matchArchive/m1"), { playerUids: ["ana", "beto"] });
      await setDoc(doc(db, "leaderboards/2026-T4/entries/ana"), { score: 10 });
    });
  });

  it("el historial personal solo lo lee su dueño y nadie lo escribe", async () => {
    await assertSucceeds(getDoc(doc(guest("ana"), "users/ana/matchHistory/m1")));
    await assertFails(getDoc(doc(guest("beto"), "users/ana/matchHistory/m1")));
    await assertFails(setDoc(doc(guest("ana"), "users/ana/matchHistory/m2"), { result: "VICTORY" }));
  });

  it("el archivo global lo leen solo quienes jugaron", async () => {
    await assertSucceeds(getDoc(doc(guest("beto"), "matchArchive/m1")));
    await assertFails(getDoc(doc(guest("carla"), "matchArchive/m1")));
  });

  it("el ranking se lee pero no se escribe", async () => {
    await assertSucceeds(getDoc(doc(guest("beto"), "leaderboards/2026-T4/entries/ana")));
    await assertFails(setDoc(doc(guest("ana"), "leaderboards/2026-T4/entries/ana"), { score: 9999 }));
  });
});
