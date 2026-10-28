import type { Database } from "firebase-admin/database";
import { GameError } from "../lib/errors.js";
import { clean, transact } from "../lib/tx.js";
import { normalizeRoomCode, randomRoomCode } from "./codes.js";
import type { ActiveEntry, Deps, OpenRoomEntry, RoomData, RoomMember } from "./types.js";
import { parseCreateRoom } from "./validation.js";

const MAX_NAME = 20;
const CODE_ATTEMPTS = 10;

const memberCount = (room: RoomData) => Object.keys(room.members ?? {}).length;

// ───────────────────────── Índices y lecturas auxiliares ─────────────────────────

export function openRoomEntry(room: RoomData): OpenRoomEntry {
  return {
    name: room.name,
    code: room.code,
    hostName: room.members[room.hostUid]?.name ?? "",
    players: memberCount(room),
    max: room.maxPlayers,
    ruleSet: room.config.ruleSet,
    createdAt: room.createdAt,
  };
}

/** Mantiene `openRooms` (lista de salas disponibles) acorde con el estado de la sala. */
export async function syncOpenRoom(db: Database, roomId: string, room: RoomData | null): Promise<void> {
  const ref = db.ref(`openRooms/${roomId}`);
  const visible = room && room.type === "PUBLIC" && room.status === "WAITING" && memberCount(room) < room.maxPlayers;
  if (visible) await ref.set(clean(openRoomEntry(room)));
  else await ref.remove();
}

export async function getActive(db: Database, uid: string): Promise<ActiveEntry | null> {
  return ((await db.ref(`active/${uid}`).get()).val() as ActiveEntry | null) ?? null;
}

/** Sala en la que está el usuario ahora mismo, ignorando punteros viejos (sala borrada o expulsión). */
async function activeRoomOf(db: Database, uid: string): Promise<{ roomId: string; room: RoomData } | null> {
  const active = await getActive(db, uid);
  if (!active?.roomId) return null;
  const room = (await db.ref(`rooms/${active.roomId}`).get()).val() as RoomData | null;
  if (!room?.members?.[uid]) return null;
  return { roomId: active.roomId, room };
}

const shortName = (name: string) => name.trim().slice(0, MAX_NAME);

// ───────────────────────── Crear sala ─────────────────────────

export async function createRoom(deps: Deps, uid: string, rawInput: unknown): Promise<{ roomId: string; code: string }> {
  const input = parseCreateRoom(rawInput);
  const { db } = deps;

  if (await activeRoomOf(db, uid)) throw new GameError("failed-precondition", "ALREADY_IN_ROOM", "Ya estás en una sala");

  const profile = await deps.getProfile(uid);
  const roomId = db.ref("rooms").push().key;
  if (!roomId) throw new Error("No se pudo generar el ID de la sala");

  const code = await reserveCode(db, roomId);
  const now = deps.now();
  const host: RoomMember = { name: shortName(profile.displayName), avatar: profile.avatar, joinedAt: now, host: true, ready: true };
  const room: RoomData = {
    name: `Sala de ${host.name}`,
    type: input.isPrivate ? "PRIVATE" : "PUBLIC",
    hostUid: uid,
    code,
    maxPlayers: input.maxPlayers,
    status: "WAITING",
    createdAt: now,
    updatedAt: now,
    config: input.config,
    members: { [uid]: host },
  };

  try {
    await db.ref().update(
      clean({
        [`rooms/${roomId}`]: room,
        [`active/${uid}`]: { roomId },
        ...(room.type === "PUBLIC" ? { [`openRooms/${roomId}`]: openRoomEntry(room) } : {}),
      }),
    );
  } catch (error) {
    await db.ref(`roomCodes/${code}`).remove();
    throw error;
  }
  return { roomId, code };
}

/** Reserva un código libre con una transacción: dos salas nunca comparten código. */
async function reserveCode(db: Database, roomId: string): Promise<string> {
  for (let attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
    const code = randomRoomCode();
    const result = await db.ref(`roomCodes/${code}`).transaction((current) => (current === null ? roomId : undefined), undefined, false);
    if (result.committed) return code;
  }
  throw new GameError("resource-exhausted", "NO_FREE_CODE", "No se pudo generar un código de sala");
}

// ───────────────────────── Unirse ─────────────────────────

export async function joinRoom(deps: Deps, uid: string, rawInput: unknown): Promise<{ roomId: string }> {
  const input = (rawInput ?? {}) as { roomId?: unknown; code?: unknown };
  const { db } = deps;

  let roomId: string;
  let viaCode = false;
  if (typeof input.code === "string" && input.code.trim() !== "") {
    const code = normalizeRoomCode(input.code);
    if (!code) throw new GameError("invalid-argument", "INVALID_CODE", "Código con formato incorrecto");
    const found = (await db.ref(`roomCodes/${code}`).get()).val() as string | null;
    if (!found) throw new GameError("not-found", "ROOM_NOT_FOUND", "No existe una sala con ese código");
    roomId = found;
    viaCode = true;
  } else if (typeof input.roomId === "string" && input.roomId !== "") {
    roomId = input.roomId;
  } else {
    throw new GameError("invalid-argument", "INVALID_ARGUMENT", "Falta el código o la sala");
  }

  const current = await activeRoomOf(db, uid);
  if (current && current.roomId !== roomId) throw new GameError("failed-precondition", "ALREADY_IN_ROOM", "Ya estás en otra sala");

  const profile = await deps.getProfile(uid);
  const now = deps.now();
  const member: RoomMember = { name: shortName(profile.displayName), avatar: profile.avatar, joinedAt: now, host: false, ready: true };

  const saved = await transact<RoomData>(db.ref(`rooms/${roomId}`), (room) => {
    if (room.members?.[uid]) return room; // volver a pulsar "Unirse" no duplica nada
    if (room.banned?.[uid]) throw new GameError("permission-denied", "BANNED", "El anfitrión te sacó de esta sala");
    if (room.type === "QUICKPLAY") throw new GameError("failed-precondition", "NOT_JOINABLE");
    if (room.type === "PRIVATE" && !viaCode) throw new GameError("permission-denied", "ROOM_PRIVATE", "Esta sala es privada: necesitas el código");
    if (room.status !== "WAITING") throw new GameError("failed-precondition", "ROOM_STARTED", "La partida ya empezó");
    if (memberCount(room) >= room.maxPlayers) throw new GameError("resource-exhausted", "ROOM_FULL", "La sala está llena");
    return { ...room, members: { ...room.members, [uid]: member }, updatedAt: now };
  });
  if (!saved) throw new GameError("not-found", "ROOM_NOT_FOUND", "La sala ya no existe");

  await db.ref(`active/${uid}`).set({ roomId });
  await syncOpenRoom(db, roomId, saved);
  return { roomId };
}

// ───────────────────────── Salir y expulsar ─────────────────────────

/** Quita a `target` de la sala, pasa el anfitrión al jugador más antiguo y borra la sala si queda vacía. */
async function removeMember(
  deps: Deps,
  roomId: string,
  target: string,
  opts: { ban: boolean; requireHost?: string },
): Promise<{ deleted: boolean }> {
  const { db } = deps;
  const now = deps.now();
  let before: RoomData | null = null;

  const saved = await transact<RoomData>(db.ref(`rooms/${roomId}`), (room) => {
    before = room;
    if (opts.requireHost !== undefined && room.hostUid !== opts.requireHost) throw new GameError("permission-denied", "NOT_HOST", "Solo el anfitrión puede hacerlo");
    if (!room.members?.[target]) {
      if (opts.requireHost !== undefined) throw new GameError("not-found", "NOT_A_MEMBER", "Ese jugador no está en la sala");
      return room;
    }
    if (room.status !== "WAITING") throw new GameError("failed-precondition", "ROOM_IN_MATCH", "La partida está en curso");

    const members = { ...room.members };
    delete members[target];
    const rest = Object.entries(members);
    if (rest.length === 0) return null;

    let hostUid = room.hostUid;
    if (hostUid === target) {
      const [next] = rest.sort(([, a], [, b]) => a.joinedAt - b.joinedAt)[0]!;
      members[next] = { ...members[next]!, host: true };
      hostUid = next;
    }
    return {
      ...room,
      hostUid,
      members,
      updatedAt: now,
      ...(opts.ban ? { banned: { ...room.banned, [target]: true as const } } : {}),
    };
  });

  await db.ref(`active/${target}`).remove();
  const deleted = saved === null;
  if (deleted) {
    const code = (before as RoomData | null)?.code;
    await db.ref().update({ [`openRooms/${roomId}`]: null, ...(code ? { [`roomCodes/${code}`]: null } : {}) });
  } else {
    await syncOpenRoom(db, roomId, saved);
  }
  return { deleted };
}

export async function leaveRoom(deps: Deps, uid: string): Promise<{ left: boolean }> {
  const active = await getActive(deps.db, uid);
  if (!active?.roomId) return { left: false };
  await removeMember(deps, active.roomId, uid, { ban: false });
  return { left: true };
}

export async function kickPlayer(deps: Deps, uid: string, rawInput: unknown): Promise<void> {
  const target = (rawInput as { targetUid?: unknown } | null)?.targetUid;
  if (typeof target !== "string" || target === "") throw new GameError("invalid-argument", "INVALID_ARGUMENT");
  if (target === uid) throw new GameError("invalid-argument", "CANNOT_KICK_SELF", "No puedes sacarte a ti mismo");

  const active = await getActive(deps.db, uid);
  if (!active?.roomId) throw new GameError("failed-precondition", "NOT_IN_ROOM", "No estás en ninguna sala");
  await removeMember(deps, active.roomId, target, { ban: true, requireHost: uid });
}
