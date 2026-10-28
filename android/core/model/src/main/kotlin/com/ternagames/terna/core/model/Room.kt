package com.ternagames.terna.core.model

enum class RoomType { Public, Private, QuickPlay }

enum class RoomStatus { Waiting, Starting, InMatch }

/** Límites de jugadores de los modos actuales (los valida el servidor; aquí solo ajustan la UI). */
object RoomLimits {
    const val MIN_PLAYERS = 3
    const val MAX_PLAYERS = 6
}

data class RoomMember(
    val uid: String,
    val name: String,
    val avatar: String,
    val joinedAt: Long,
    val host: Boolean,
)

/** Una sala de espera (`rooms/{roomId}` en Realtime Database). */
data class Room(
    val id: String,
    val name: String,
    val type: RoomType,
    val hostUid: String,
    val code: String,
    val maxPlayers: Int,
    val status: RoomStatus,
    val currentMatchId: String?,
    val ruleSet: String,
    /** Ordenados por orden de llegada. */
    val members: List<RoomMember>,
) {
    val isPrivate: Boolean get() = type == RoomType.Private
    fun isHost(uid: String): Boolean = hostUid == uid
    fun hasMember(uid: String): Boolean = members.any { it.uid == uid }
    val freeSlots: Int get() = (maxPlayers - members.size).coerceAtLeast(0)
    val canStart: Boolean get() = members.size >= RoomLimits.MIN_PLAYERS
}

/** Una fila de "Salas disponibles" (`openRooms/{roomId}`). */
data class OpenRoom(
    val id: String,
    val name: String,
    val hostName: String,
    val players: Int,
    val max: Int,
    val code: String,
)

/** Dónde está jugando el usuario (`active/{uid}`): se usa para reconectarse al abrir la app. */
data class ActiveEntry(
    val roomId: String,
    val matchId: String?,
)
