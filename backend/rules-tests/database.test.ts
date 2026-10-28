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
      matches: {
        m1: {
          meta: { status: "PLAYING" },
          players: { ana: { seat: 0 }, beto: { seat: 1 } },
          live: { seq: 1 },
          hands: { ana: [1, 4, 9], beto: [2, 2, 12] },
          secret: { state: "{}" },
        },
      },
      rooms: {
        pub: { type: "PUBLIC", members: { ana: { name: "Ana" } } },
        priv: { type: "PRIVATE", members: { ana: { name: "Ana" } } },
      },
      openRooms: { pub: { name: "Sala de Ana", players: 1 } },
      roomCodes: { "TRN-7K4Q": "priv" },
      active: { ana: { roomId: "pub", matchId: "m1" } },
    });
  });
});

const db = (uid: string) => env.authenticatedContext(uid).database();
const anon = () => env.unauthenticatedContext().database();

describe("partidas", () => {
  it("solo los jugadores de la partida leen lo público", async () => {
    for (const branch of ["meta", "players", "live"]) {
      await assertSucceeds(get(ref(db("ana"), `matches/m1/${branch}`)));
      await assertSucceeds(get(ref(db("beto"), `matches/m1/${branch}`)));
      await assertFails(get(ref(db("carla"), `matches/m1/${branch}`)));
      await assertFails(get(ref(anon(), `matches/m1/${branch}`)));
    }
  });

  it("nadie lee la partida completa: tendría las manos y el estado secreto", async () => {
    await assertFails(get(ref(db("ana"), "matches/m1")));
    await assertFails(get(ref(db("ana"), "matches")));
  });

  it("cada jugador ve solo su propia mano", async () => {
    await assertSucceeds(get(ref(db("ana"), "matches/m1/hands/ana")));
    await assertFails(get(ref(db("ana"), "matches/m1/hands/beto")));
    await assertFails(get(ref(db("ana"), "matches/m1/hands")));
    await assertFails(get(ref(db("carla"), "matches/m1/hands/ana")));
  });

  it("nadie lee el estado secreto ni los códigos de sala", async () => {
    await assertFails(get(ref(db("ana"), "matches/m1/secret")));
    await assertFails(get(ref(db("ana"), "matches/m1/secret/state")));
    await assertFails(get(ref(db("ana"), "roomCodes")));
  });

  it("ningún cliente escribe partidas, manos ni salas", async () => {
    await assertFails(update(ref(db("ana"), "matches/m1/live"), { seq: 99 }));
    await assertFails(set(ref(db("ana"), "matches/m1/hands/ana"), [7, 7, 7]));
    await assertFails(set(ref(db("ana"), "matches/m1/secret/state"), "{}"));
    await assertFails(set(ref(db("ana"), "rooms/pub/members/ana/name"), "Hack"));
    await assertFails(set(ref(db("ana"), "openRooms/nueva"), { name: "spam" }));
  });

  it("`active` lo lee solo su dueño y no lo escribe nadie", async () => {
    await assertSucceeds(get(ref(db("ana"), "active/ana")));
    await assertFails(get(ref(db("beto"), "active/ana")));
    await assertFails(set(ref(db("ana"), "active/ana"), { roomId: "otra" }));
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
