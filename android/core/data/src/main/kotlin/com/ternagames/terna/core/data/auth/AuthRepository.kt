package com.ternagames.terna.core.data.auth

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.ternagames.terna.core.model.SessionUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

interface AuthRepository {
    /** Sesión actual; emite null cuando no hay nadie autenticado. */
    val session: Flow<SessionUser?>
    val currentSession: SessionUser?

    suspend fun signInAsGuest(): SessionUser

    /**
     * Abre el selector de cuentas de Google. Si el usuario actual es invitado, vincula la
     * cuenta para conservar su UID y su progreso. Necesita un contexto de Activity.
     */
    suspend fun signInWithGoogle(activityContext: Context): SessionUser

    suspend fun signOut()
}

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val googleIdTokens: GoogleIdTokenProvider,
) : AuthRepository {

    override val session: Flow<SessionUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toSession()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override val currentSession: SessionUser? get() = auth.currentUser?.toSession()

    override suspend fun signInAsGuest(): SessionUser {
        auth.currentUser?.let { return it.toSession() }
        val user = auth.signInAnonymously().await().user ?: error("Firebase no devolvió usuario")
        return user.toSession()
    }

    override suspend fun signInWithGoogle(activityContext: Context): SessionUser {
        val idToken = googleIdTokens.request(activityContext)
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val current = auth.currentUser

        val user = if (current != null && current.isAnonymous) {
            try {
                current.linkWithCredential(credential).await().user
            } catch (_: FirebaseAuthUserCollisionException) {
                // Esa cuenta de Google ya tenía perfil: se entra a ella y se deja el invitado.
                auth.signInWithCredential(credential).await().user
            }
        } else {
            auth.signInWithCredential(credential).await().user
        }
        // Tras vincular, el token debe renovarse para que las reglas vean el proveedor "google.com".
        user?.getIdToken(true)?.await()
        return (user ?: error("Firebase no devolvió usuario")).toSession()
    }

    override suspend fun signOut() {
        googleIdTokens.clear()
        auth.signOut()
    }
}

private fun FirebaseUser.toSession(): SessionUser {
    val provider = providerData.firstOrNull { it.providerId != "firebase" }
    return SessionUser(
        uid = uid,
        anonymous = isAnonymous,
        providerName = provider?.displayName ?: displayName,
        photoUrl = (provider?.photoUrl ?: photoUrl)?.toString()?.let(::largeGooglePhoto),
    )
}

/** Google entrega la foto en 96 px (`…=s96-c`); el perfil la muestra grande, así que se pide en 400 px. */
internal fun largeGooglePhoto(url: String): String = url.replace(Regex("=s\\d+-c$"), "=s400-c")
