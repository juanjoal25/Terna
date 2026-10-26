import { readFileSync } from "node:fs";
import { assertFails, assertSucceeds, initializeTestEnvironment, type RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { get, ref, serverTimestamp, set, update } from "firebase/database";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";

let env: RulesTestEnvironment;

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-terna",
    database: { rules: readFileSync(new URL("../database.rules.json", import.meta.url), "utf8"), host: "127.0.0.1", port: 9000 },
  });
});
afterAll(() => env.cleanup());

beforeEach(async () => {
  await env.clearDatabase();
  await env.withSecurityRulesDisabled(async (ctx) => {
    await set(ref(ctx.database()), {
      matches: { m1: { players: { ana: { seat: 0 }, beto: { seat: 1 } }, live: { seq: 1 } } },
      hands: { m1: { ana: [1, 4, 9], beto: [2, 2, 12] } },
      secret: { m1: { state: "{}" } },
      rooms: {
        pub: { type: "PUBLIC", members: { ana: { name: "Ana" } } },
        priv: { type: "PRIVATE", members: { ana: { name: "Ana" } } },
      },
      openRooms: { pub: { name: "Sala de Ana", players: 1 } },
      roomCodes: { "TRN-7K4Q": "priv" },
      activeMatch: { ana: "m1" },
    });
  });
});

const db = (uid: string) => env.authenticatedContext(uid).database();
const anon = () => env.unauthenticatedContext().database();

describe("partidas", () => {
  it("solo los jugadores de la partida la leen", async () => {
    await assertSucceeds(get(ref(db("ana"), "matches/m1")));
    await assertFails(get(ref(db("carla"), "matches/m1")));
    await assertFails(get(ref(anon(), "matches/m1")));
  });

  it("cada jugador ve solo su propia mano", async () => {
    await assertSucceeds(get(ref(db("ana"), "hands/m1/ana")));
    await assertFails(get(ref(db("ana"), "hands/m1/beto")));
    await assertFails(get(ref(db("ana"), "hands/m1")));
  });

  it("nadie lee el estado secreto ni los códigos de sala", async () => {
    await assertFails(get(ref(db("ana"), "secret/m1")));
    await assertFails(get(ref(db("ana"), "roomCodes")));
  });

  it("ningún cliente escribe partidas, manos ni salas", async () => {
    await assertFails(update(ref(db("ana"), "matches/m1/live"), { seq: 99 }));
    await assertFails(set(ref(db("ana"), "hands/m1/ana"), [7, 7, 7]));
    await assertFails(set(ref(db("ana"), "rooms/pub/members/ana/name"), "Hack"));
    await assertFails(set(ref(db("ana"), "openRooms/nueva"), { name: "spam" }));
  });

  it("activeMatch lo lee solo su dueño", async () => {
    await assertSucceeds(get(ref(db("ana"), "activeMatch/ana")));
    await assertFails(get(ref(db("beto"), "activeMatch/ana")));
  });
});

describe("salas", () => {
  it("las públicas las ve cualquiera con sesión; las privadas, solo sus miembros", async () => {
    await assertSucceeds(get(ref(db("carla"), "rooms/pub")));
    await assertFails(get(ref(db("carla"), "rooms/priv")));
    await assertSucceeds(get(ref(db("ana"), "rooms/priv")));
  });

  it("la lista de salas abiertas exige sesión", async () => {
    await assertSucceeds(get(ref(db("carla"), "openRooms")));
    await assertFails(get(ref(anon(), "openRooms")));
  });
});

describe("presencia", () => {
  it("cada uno escribe su presencia con la hora del servidor", async () => {
    await assertSucceeds(set(ref(db("ana"), "presence/ana"), { online: true, lastSeen: serverTimestamp() }));
  });

  it("rechaza la presencia de otro, campos extra, tipos incorrectos y fechas futuras", async () => {
    await assertFails(set(ref(db("ana"), "presence/beto"), { online: true, lastSeen: serverTimestamp() }));
    await assertFails(set(ref(db("ana"), "presence/ana"), { online: true, lastSeen: serverTimestamp(), basura: "x".repeat(1000) }));
    await assertFails(set(ref(db("ana"), "presence/ana"), { online: "sí", lastSeen: serverTimestamp() }));
    await assertFails(set(ref(db("ana"), "presence/ana"), { online: true, lastSeen: Date.now() + 3_600_000 }));
  });
});
