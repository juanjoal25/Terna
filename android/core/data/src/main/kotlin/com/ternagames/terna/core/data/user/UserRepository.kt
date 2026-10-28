package com.ternagames.terna.core.data.user

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.ternagames.terna.core.model.Avatars
import com.ternagames.terna.core.model.ModeStats
import com.ternagames.terna.core.model.PlayerProfile
import com.ternagames.terna.core.model.SessionUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

interface UserRepository {
    fun observeProfile(uid: String): Flow<PlayerProfile?>

    /**
     * Crea el perfil en el primer inicio de sesión o actualiza `lastLoginAt`.
     * Si un invitado acaba de vincular Google, marca `anonymous = false` y adopta su nombre.
     */
    suspend fun ensureProfile(session: SessionUser): PlayerProfile

    /** Cambia el avatar (un ID de [Avatars] o del catálogo del design system). */
    suspend fun updateAvatar(uid: String, avatar: String)
}

/**
 * Perfil en `users/{uid}`. Los mapas que se escriben respetan exactamente las reglas de
 * `backend/firestore.rules`: si se agrega un campo aquí, hay que permitirlo allí.
 */
@Singleton
class FirestoreUserRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) : UserRepository {

    private fun doc(uid: String) = firestore.collection("users").document(uid)

    override fun observeProfile(uid: String): Flow<PlayerProfile?> = callbackFlow {
        val registration = doc(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.takeIf { it.exists() }?.toProfile())
        }
        awaitClose { registration.remove() }
    }

    override suspend fun ensureProfile(session: SessionUser): PlayerProfile {
        val ref = doc(session.uid)
        val existing = ref.get().await()

        if (!existing.exists()) {
            val name = if (session.anonymous) guestName() else ProfileNames.sanitize(session.providerName)
            ref.set(
                mapOf(
                    "displayName" to name,
                    "avatar" to defaultAvatar(session),
                    "anonymous" to session.anonymous,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "lastLoginAt" to FieldValue.serverTimestamp(),
                    "level" to 1,
                    "xp" to 0,
                    "coins" to 0,
                ),
            ).await()
        } else {
            val updates = mutableMapOf<String, Any>("lastLoginAt" to FieldValue.serverTimestamp())
            val wasGuest = existing.getBoolean("anonymous") == true
            if (wasGuest && !session.anonymous) {
                updates["anonymous"] = false
                if (existing.getString("displayName").orEmpty().startsWith(GUEST_PREFIX)) {
                    updates["displayName"] = ProfileNames.sanitize(session.providerName)
                }
                // Si nunca cambió la mascota, pasa a mostrar su foto de Google.
                if (existing.getString("avatar") == Avatars.DEFAULT) {
                    updates["avatar"] = defaultAvatar(session)
                }
            }
            ref.update(updates).await()
        }
        return ref.get().await().toProfile()
    }

    override suspend fun updateAvatar(uid: String, avatar: String) {
        doc(uid).update("avatar", avatar).await()
    }

    /** Las cuentas con foto de Google empiezan con ella (diseño "Perfil"); los invitados, con el jaguar. */
    private fun defaultAvatar(session: SessionUser) =
        if (!session.anonymous && session.photoUrl != null) Avatars.PHOTO else Avatars.DEFAULT

    private fun guestName() = GUEST_PREFIX + Random.nextInt(0, 1000).toString().padStart(3, '0')

    private companion object {
        const val GUEST_PREFIX = "Guest"
    }
}

/** Reglas de nombre compartidas con firestore.rules (1 a 20 caracteres). */
object ProfileNames {
    const val MAX_LENGTH = 20
    private const val FALLBACK = "Jugador"

    fun sanitize(raw: String?): String {
        val clean = raw.orEmpty().trim().replace(Regex("\\s+"), " ")
        return clean.take(MAX_LENGTH).trim().ifEmpty { FALLBACK }
    }
}

private fun DocumentSnapshot.toProfile() = PlayerProfile(
    uid = id,
    displayName = getString("displayName").orEmpty(),
    avatar = getString("avatar") ?: Avatars.DEFAULT,
    anonymous = getBoolean("anonymous") ?: true,
    level = (getLong("level") ?: 1L).toInt(),
    xp = getLong("xp") ?: 0L,
    coins = getLong("coins") ?: 0L,
    stats = parseStats(get("stats")),
)

/** `stats` lo escribe el servidor; se lee con tolerancia para que un campo raro no tumbe el perfil. */
internal fun parseStats(raw: Any?): Map<String, ModeStats> {
    val modes = raw as? Map<*, *> ?: return emptyMap()
    return modes.entries.mapNotNull { (mode, value) ->
        val fields = value as? Map<*, *> ?: return@mapNotNull null
        fun int(key: String) = (fields[key] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
        (mode as? String ?: return@mapNotNull null) to ModeStats(
            games = int("games"),
            wins = int("wins"),
            sets = int("sets"),
            sevensWins = int("sevensWins"),
            currentStreak = int("currentStreak"),
            bestStreak = int("bestStreak"),
        )
    }.toMap()
}
