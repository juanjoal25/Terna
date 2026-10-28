import { beforeAll, beforeEach, describe, expect, it } from "vitest";
import { createRoom, getActive, joinRoom, kickPlayer, leaveRoom } from "../rooms/roomService.js";
import type { OpenRoomEntry, RoomData } from "../rooms/types.js";
import { createTestEnv, read, reasonOf, type TestEnv } from "./harness.js";

let env: TestEnv;
beforeAll(() => {
  env = createTestEnv();
});
beforeEach(async () => {
  await env.reset();
  Object.assign(env.names, { ana: "Ana", beto: "Beto", carla: "Carla", dani: "Dani", eli: "Eli", fer: "Fer", gus: "Gus" });
});

const room = (id: string) => read<RoomData>(env.deps, `rooms/${id}`);

describe("crear sala", () => {
  it("una sala pública queda en la lista de salas abiertas, con su código y su anfitrión", async () => {
    const { roomId, code } = await createRoom(env.deps, "ana", { maxPlayers: 6, isPrivate: false });

    expect(code).toMatch(/^TRN-[2-9A-HJKMNP-Z]{4}$/);
    const saved = await room(roomId);
    expect(saved).toMatchObject({ name: "Sala de Ana", type: "PUBLIC", hostUid: "ana", maxPlayers: 6, status: "WAITING", code });
    expect(saved?.members.ana).toMatchObject({ name: "Ana", host: true });
    expect(saved?.config).toMatchObject({ ruleSet: "TERNA_CLASSIC", turnSeconds: 20 });

    expect(await read(env.deps, `roomCodes/${code}`)).toBe(roomId);
    expect(await getActive(env.deps.db, "ana")).toEqual({ roomId });
    expect(await read<OpenRoomEntry>(env.deps, `openRooms/${roomId}`)).toMatchObject({ name: "Sala de Ana", hostName: "Ana", players: 1, max: 6, code });
  });

  it("una sala privada no aparece en la lista pero sí se encuentra por código", async () => {
    const { roomId, code } = await createRoom(env.deps, "ana", { maxPlayers: 4, isPrivate: true });
    expect((await room(roomId))?.type).toBe("PRIVATE");
    expect(await read(env.deps, `openRooms/${roomId}`)).toBeNull();
    expect(await read(env.deps, `roomCodes/${code}`)).toBe(roomId);
  });

  it("valida los datos que manda la app", async () => {
    expect(await reasonOf(createRoom(env.deps, "ana", { maxPlayers: 2, isPrivate: false }))).toBe("INVALID_MAX_PLAYERS");
    expect(await reasonOf(createRoom(env.deps, "ana", { maxPlayers: 7, isPrivate: false }))).toBe("INVALID_MAX_PLAYERS");
    expect(await reasonOf(createRoom(env.deps, "ana", { maxPlayers: 3.5, isPrivate: false }))).toBe("INVALID_MAX_PLAYERS");
    expect(await reasonOf(createRoom(env.deps, "ana", { maxPlayers: 4 }))).toBe("INVALID_PRIVACY");
    expect(await reasonOf(createRoom(env.deps, "ana", { maxPlayers: 4, isPrivate: false, ruleSet: "NO_EXISTE" }))).toBe("UNKNOWN_RULESET");
    expect(await reasonOf(createRoom(env.deps, "ana", { maxPlayers: 4, isPrivate: false, turnSeconds: 5 }))).toBe("INVALID_TURN_SECONDS");
    expect(await reasonOf(createRoom(env.deps, "ana", { maxPlayers: 4, isPrivate: false, options: { allowPass: "sí" } }))).toBe("INVALID_OPTIONS");
    expect(await read(env.deps, "rooms")).toBeNull();
  });

  it("guarda solo las opciones de turno que el anfitrión cambió e ignora las desconocidas", async () => {
    const { roomId } = await createRoom(env.deps, "ana", { maxPlayers: 4, isPrivate: false, options: { allowPass: true, hackeo: true } });
    expect((await room(roomId))?.config.options).toEqual({ allowPass: true });
  });

  it("no deja estar en dos salas a la vez", async () => {
    await createRoom(env.deps, "ana", { maxPlayers: 4, isPrivate: false });
    expect(await reasonOf(createRoom(env.deps, "ana", { maxPlayers: 4, isPrivate: false }))).toBe("ALREADY_IN_ROOM");
  });

  it("los códigos de salas distintas nunca se repiten", async () => {
    const uids = Array.from({ length: 12 }, (_, i) => `u${i}`);
    const codes = await Promise.all(uids.map(async (uid) => (await createRoom(env.deps, uid, { maxPlayers: 3, isPrivate: true })).code));
    expect(new Set(codes).size).toBe(uids.length);
  });
});

describe("unirse", () => {
  it("por código, aceptando minúsculas, sin prefijo y con espacios", async () => {
    const { roomId, code } = await createRoom(env.deps, "ana", { maxPlayers: 6, isPrivate: true });
    const body = code.slice(4);

    expect(await joinRoom(env.deps, "beto", { code: code.toLowerCase() })).toEqual({ roomId });
    expect(await joinRoom(env.deps, "carla", { code: ` ${body} ` })).toEqual({ roomId });

    const saved = await room(roomId);
    expect(Object.keys(saved!.members).sort()).toEqual(["ana", "beto", "carla"]);
    expect(saved!.members.beto).toMatchObject({ name: "Beto", host: false });
    expect(await getActive(env.deps.db, "beto")).toEqual({ roomId });
  });

  it("una sala pública también se puede unir por ID, y vuelve a pulsar no duplica", async () => {
    const { roomId } = await createRoom(env.deps, "ana", { maxPlayers: 6, isPrivate: false });
    await joinRoom(env.deps, "beto", { roomId });
    await joinRoom(env.deps, "beto", { roomId });
    expect(Object.keys((await room(roomId))!.members)).toHaveLength(2);
    expect((await read<OpenRoomEntry>(env.deps, `openRooms/${roomId}`))?.players).toBe(2);
  });

  it("una sala privada NO se puede unir adivinando su ID", async () => {
    const { roomId } = await createRoom(env.deps, "ana", { maxPlayers: 6, isPrivate: true });
    expect(await reasonOf(joinRoom(env.deps, "beto", { roomId }))).toBe("ROOM_PRIVATE");
  });

  it("rechaza códigos mal escritos y salas que no existen", async () => {
    expect(await reasonOf(joinRoom(env.deps, "beto", { code: "hola" }))).toBe("INVALID_CODE");
    expect(await reasonOf(joinRoom(env.deps, "beto", { code: "TRN-ZZZZ" }))).toBe("ROOM_NOT_FOUND");
    expect(await reasonOf(joinRoom(env.deps, "beto", { roomId: "no-existe" }))).toBe("ROOM_NOT_FOUND");
    expect(await reasonOf(joinRoom(env.deps, "beto", {}))).toBe("INVALID_ARGUMENT");
  });

  it("no deja entrar a una sala llena y la quita de la lista; al salir alguien vuelve a aparecer", async () => {
    const { roomId } = await createRoom(env.deps, "ana", { maxPlayers: 3, isPrivate: false });
    await joinRoom(env.deps, "beto", { roomId });
    await joinRoom(env.deps, "carla", { roomId });

    expect(await reasonOf(joinRoom(env.deps, "dani", { roomId }))).toBe("ROOM_FULL");
    expect(await read(env.deps, `openRooms/${roomId}`)).toBeNull();

    await leaveRoom(env.deps, "carla");
    expect((await read<OpenRoomEntry>(env.deps, `openRooms/${roomId}`))?.players).toBe(2);
    await joinRoom(env.deps, "dani", { roomId });
  });

  it("si 6 personas entran a la vez a una sala de 3, caben exactamente 2 más", async () => {
    const { roomId } = await createRoom(env.deps, "ana", { maxPlayers: 3, isPrivate: false });
    const results = await Promise.allSettled(["beto", "carla", "dani", "eli", "fer", "gus"].map((uid) => joinRoom(env.deps, uid, { roomId })));

    expect(results.filter((r) => r.status === "fulfilled")).toHaveLength(2);
    const rejected = results.filter((r): r is PromiseRejectedResult => r.status === "rejected");
    expect(rejected).toHaveLength(4);
    expect(rejected.every((r) => r.reason.reason === "ROOM_FULL")).toBe(true);
    expect(Object.keys((await room(roomId))!.members)).toHaveLength(3);
  });

  it("no deja estar en dos salas a la vez", async () => {
    const a = await createRoom(env.deps, "ana", { maxPlayers: 4, isPrivate: false });
    await createRoom(env.deps, "beto", { maxPlayers: 4, isPrivate: false });
    expect(await reasonOf(joinRoom(env.deps, "beto", { roomId: a.roomId }))).toBe("ALREADY_IN_ROOM");
  });
});

describe("salir de la sala", () => {
  it("al irse un invitado, la sala sigue; al irse el anfitrión, pasa al jugador más antiguo", async () => {
    const { roomId } = await createRoom(env.deps, "ana", { maxPlayers: 6, isPrivate: false });
    env.clock.advance(1000);
    await joinRoom(env.deps, "beto", { roomId });
    env.clock.advance(1000);
    await joinRoom(env.deps, "carla", { roomId });

    await leaveRoom(env.deps, "carla");
    expect(Object.keys((await room(roomId))!.members).sort()).toEqual(["ana", "beto"]);
    expect(await getActive(env.deps.db, "carla")).toBeNull();

    await leaveRoom(env.deps, "ana");
    const saved = await room(roomId);
    expect(saved?.hostUid).toBe("beto");
    expect(saved?.members.beto?.host).toBe(true);
    expect((await read<OpenRoomEntry>(env.deps, `openRooms/${roomId}`))?.hostName).toBe("Beto");
  });

  it("cuando sale el último jugador se borra la sala, su código y su entrada en la lista", async () => {
    const { roomId, code } = await createRoom(env.deps, "ana", { maxPlayers: 4, isPrivate: false });
    expect(await leaveRoom(env.deps, "ana")).toEqual({ left: true });

    expect(await room(roomId)).toBeNull();
    expect(await read(env.deps, `roomCodes/${code}`)).toBeNull();
    expect(await read(env.deps, `openRooms/${roomId}`)).toBeNull();
    expect(await getActive(env.deps.db, "ana")).toBeNull();
    // Y ya se puede crear otra sala:
    await createRoom(env.deps, "ana", { maxPlayers: 4, isPrivate: false });
  });

  it("salir sin estar en ninguna sala no es un error", async () => {
    expect(await leaveRoom(env.deps, "ana")).toEqual({ left: false });
  });
});

describe("expulsar", () => {
  it("el anfitrión saca a un jugador, que no puede volver a entrar", async () => {
    const { roomId, code } = await createRoom(env.deps, "ana", { maxPlayers: 6, isPrivate: false });
    await joinRoom(env.deps, "beto", { roomId });

    await kickPlayer(env.deps, "ana", { targetUid: "beto" });
    expect(Object.keys((await room(roomId))!.members)).toEqual(["ana"]);
    expect(await getActive(env.deps.db, "beto")).toBeNull();

    expect(await reasonOf(joinRoom(env.deps, "beto", { roomId }))).toBe("BANNED");
    expect(await reasonOf(joinRoom(env.deps, "beto", { code }))).toBe("BANNED");
  });

  it("solo el anfitrión puede expulsar, y no puede expulsarse a sí mismo", async () => {
    const { roomId } = await createRoom(env.deps, "ana", { maxPlayers: 6, isPrivate: false });
    await joinRoom(env.deps, "beto", { roomId });
    await joinRoom(env.deps, "carla", { roomId });

    expect(await reasonOf(kickPlayer(env.deps, "beto", { targetUid: "carla" }))).toBe("NOT_HOST");
    expect(await reasonOf(kickPlayer(env.deps, "ana", { targetUid: "ana" }))).toBe("CANNOT_KICK_SELF");
    expect(await reasonOf(kickPlayer(env.deps, "ana", { targetUid: "dani" }))).toBe("NOT_A_MEMBER");
    expect(await reasonOf(kickPlayer(env.deps, "ana", {}))).toBe("INVALID_ARGUMENT");
    expect(await reasonOf(kickPlayer(env.deps, "dani", { targetUid: "ana" }))).toBe("NOT_IN_ROOM");
    expect(Object.keys((await room(roomId))!.members)).toHaveLength(3);
  });
});
