package com.ternagames.terna.core.data.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.ternagames.terna.core.data.di.FirebaseConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** El usuario cerró el selector de cuentas: no es un error que haya que mostrar. */
class SignInCancelledException : Exception("Inicio de sesión cancelado")

/** Obtiene un ID token de Google con Credential Manager ("Iniciar sesión con Google"). */
@Singleton
class GoogleIdTokenProvider @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val config: FirebaseConfig,
) {
    private val credentialManager by lazy { CredentialManager.create(appContext) }

    suspend fun request(activityContext: Context): String {
        // Botón explícito "Iniciar sesión con Google": muestra siempre el selector de cuentas.
        val option = GetSignInWithGoogleOption.Builder(serverClientId = config.webClientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        val credential = try {
            credentialManager.getCredential(activityContext, request).credential
        } catch (_: GetCredentialCancellationException) {
            throw SignInCancelledException()
        }

        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Credencial inesperada: ${credential.type}"
        }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }

    /** Al cerrar sesión, olvida la cuenta elegida para que la próxima vez vuelva a preguntar. */
    suspend fun clear() {
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
    }
}
