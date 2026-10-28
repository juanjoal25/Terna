package com.ternagames.terna.core.model

/** Usuario autenticado en Firebase Auth (invitado o con cuenta). */
data class SessionUser(
    val uid: String,
    val anonymous: Boolean,
    /** Nombre que trae el proveedor (Google/Facebook). Null para invitados. */
    val providerName: String?,
    val photoUrl: String?,
)

/** Perfil público del jugador (`users/{uid}` en Firestore). */
data class PlayerProfile(
    val uid: String,
    val displayName: String,
    val avatar: String,
    val anonymous: Boolean,
    val level: Int,
    val xp: Long,
    val coins: Long,
    /** Estadísticas por modo de juego (`TERNA_CLASSIC`, `TERNA_SPICY`…). Vacío hasta la primera partida. */
    val stats: Map<String, ModeStats> = emptyMap(),
) {
    /** Partidas jugadas sumando todos los modos. */
    val gamesPlayed: Int get() = stats.values.sumOf { it.games }

    /** Partidas ganadas sumando todos los modos. */
    val gamesWon: Int get() = stats.values.sumOf { it.wins }
}

/** Un modo dentro del mapa `stats` del perfil. Solo lo escribe el servidor. */
data class ModeStats(
    val games: Int = 0,
    val wins: Int = 0,
    val sets: Int = 0,
    val sevensWins: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
)

object Avatars {
    /** Avatar por defecto: el jaguar de las ilustraciones. */
    const val DEFAULT = "jaguar_01"

    /** Usa la foto de la cuenta de Google. Quien no la tenga (o no la vea) cae al avatar por defecto. */
    const val PHOTO = "photo"
}
