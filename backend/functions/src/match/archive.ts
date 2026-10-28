import { Timestamp, type Firestore } from "firebase-admin/firestore";
import type { PlayerProfile } from "../rooms/types.js";
import type { MatchRecord } from "./types.js";

const DEFAULT_AVATAR = "jaguar_01";

/** Perfil público de un jugador, leído del servidor (el cliente no puede falsear su nombre). */
export function firestoreProfiles(firestore: Firestore) {
  return async (uid: string): Promise<PlayerProfile> => {
    const snap = await firestore.doc(`users/${uid}`).get();
    return {
      displayName: (snap.get("displayName") as string | undefined) || "Jugador",
      avatar: (snap.get("avatar") as string | undefined) || DEFAULT_AVATAR,
    };
  };
}

interface ModeStats {
  games: number;
  wins: number;
  sets: number;
  sevensWins: number;
  currentStreak: number;
  bestStreak: number;
}

/**
 * Guarda el archivo global de la partida, el historial de cada jugador y sus estadísticas.
 * Es idempotente: si el historial de ese jugador ya existe, no vuelve a contar la partida.
 */
export function firestoreArchive(firestore: Firestore) {
  return async (record: MatchRecord): Promise<void> => {
    const humans = record.players.filter((p) => !p.isBot);
    const endedAt = Timestamp.fromMillis(record.endedAt);
    const summary = {
      matchId: record.matchId,
      roomId: record.roomId,
      ruleSet: record.ruleSet,
      reason: record.reason,
      durationSec: record.durationSec,
      endedAt,
      players: record.players.map((p) => ({ uid: p.uid, name: p.name, avatar: p.avatar, setsCount: p.sets.length, winner: p.winner })),
    };

    await firestore.doc(`matchArchive/${record.matchId}`).set({ ...summary, playerUids: humans.map((p) => p.uid), winners: record.winners });

    await Promise.all(
      humans.map((player) =>
        firestore.runTransaction(async (tx) => {
          const userRef = firestore.doc(`users/${player.uid}`);
          const historyRef = userRef.collection("matchHistory").doc(record.matchId);
          const [user, history] = await Promise.all([tx.get(userRef), tx.get(historyRef)]);
          if (history.exists) return; // esta partida ya se contó

          tx.set(historyRef, { ...summary, result: player.winner ? "VICTORY" : "DEFEAT" });
          if (!user.exists) return;

          const prev = (user.get(`stats.${record.ruleSet}`) as Partial<ModeStats> | undefined) ?? {};
          const currentStreak = player.winner ? (prev.currentStreak ?? 0) + 1 : 0;
          const next: ModeStats = {
            games: (prev.games ?? 0) + 1,
            wins: (prev.wins ?? 0) + (player.winner ? 1 : 0),
            sets: (prev.sets ?? 0) + player.sets.length,
            sevensWins: (prev.sevensWins ?? 0) + (player.winner && record.reason === "INSTANT" ? 1 : 0),
            currentStreak,
            bestStreak: Math.max(prev.bestStreak ?? 0, currentStreak),
          };
          tx.update(userRef, { [`stats.${record.ruleSet}`]: next });
        }),
      ),
    );
  };
}
