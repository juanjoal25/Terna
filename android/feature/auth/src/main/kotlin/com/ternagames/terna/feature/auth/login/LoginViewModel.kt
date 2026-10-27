package com.ternagames.terna.feature.auth.login

import android.content.Context
import android.util.Log
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.ternagames.terna.core.data.auth.AuthRepository
import com.ternagames.terna.core.data.auth.SignInCancelledException
import com.ternagames.terna.core.data.user.UserRepository
import com.ternagames.terna.core.model.SessionUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LoginMethod { Google, Facebook, Guest }

data class LoginUiState(
    /** Método en curso (muestra el spinner en ese botón y bloquea los demás). */
    val inProgress: LoginMethod? = null,
    val message: String? = null,
    val signedIn: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val users: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun playAsGuest() = signIn(LoginMethod.Guest) { auth.signInAsGuest() }

    /** Necesita el contexto de la Activity para mostrar el selector de cuentas de Google. */
    fun signInWithGoogle(activityContext: Context) = signIn(LoginMethod.Google) { auth.signInWithGoogle(activityContext) }

    fun signInWithFacebook() {
        _state.update { it.copy(message = "El inicio de sesión con Facebook llegará pronto. Mientras tanto, juega como invitado o con Google.") }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun signIn(method: LoginMethod, block: suspend () -> SessionUser) {
        if (_state.value.inProgress != null) return
        _state.update { it.copy(inProgress = method, message = null) }
        viewModelScope.launch {
            runCatching {
                val session = block()
                users.ensureProfile(session)
            }.onSuccess {
                _state.update { it.copy(inProgress = null, signedIn = true) }
            }.onFailure { error ->
                Log.w(TAG, "Falló el inicio de sesión ($method)", error)
                _state.update { it.copy(inProgress = null, message = error.toUserMessage()) }
            }
        }
    }

    private companion object {
        const val TAG = "Login"
    }
}

private fun Throwable.toUserMessage(): String? = when (this) {
    is SignInCancelledException -> null
    is NoCredentialException -> "No hay cuentas de Google en este teléfono. Agrega una en Ajustes o juega como invitado."
    is FirebaseNetworkException -> "Sin conexión. Revisa tu internet e inténtalo de nuevo."
    is FirebaseFirestoreException ->
        if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            "El servidor rechazó tu perfil. (Equipo: ¿se publicaron las reglas nuevas de Firestore?)"
        } else {
            "No pudimos guardar tu perfil. Inténtalo de nuevo."
        }
    else -> "No se pudo iniciar sesión. Inténtalo de nuevo."
}
