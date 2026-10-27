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
)

object Avatars {
    /** Avatar por defecto: el jaguar de las ilustraciones. */
    const val DEFAULT = "jaguar_01"
}
