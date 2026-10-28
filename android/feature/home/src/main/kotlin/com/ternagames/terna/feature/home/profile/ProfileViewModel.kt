package com.ternagames.terna.feature.home.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ternagames.terna.core.data.auth.AuthRepository
import com.ternagames.terna.core.data.user.UserRepository
import com.ternagames.terna.core.model.PlayerProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val signedIn: Boolean = true,
    val guest: Boolean = true,
    /** Null mientras carga o si no se pudo leer `users/{uid}`. */
    val profile: PlayerProfile? = null,
    /** Foto de la cuenta de Google; solo existe para cuentas vinculadas. */
    val photoUrl: String? = null,
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val users: UserRepository,
) : ViewModel() {

    private val message = MutableStateFlow<String?>(null)

    // Escucha `users/{uid}` en tiempo real: las estadísticas cambian solas cuando el servidor cierra una partida.
    private val remote: Flow<ProfileUiState> = auth.session.flatMapLatest { session ->
        if (session == null) {
            flowOf(ProfileUiState(signedIn = false))
        } else {
            users.observeProfile(session.uid)
                .map { ProfileUiState(guest = session.anonymous, profile = it, photoUrl = session.photoUrl) }
                .catch {
                    Log.w("Profile", "No se pudo leer el perfil", it)
                    emit(ProfileUiState(guest = session.anonymous, photoUrl = session.photoUrl))
                }
        }
    }

    val state: StateFlow<ProfileUiState> = combine(remote, message) { ui, msg -> ui.copy(message = msg) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    /**
     * Guarda el avatar. Firestore aplica el cambio en local al instante (el listener lo refleja),
     * así que no se bloquea la pantalla esperando al servidor.
     */
    fun selectAvatar(avatarId: String) {
        val profile = state.value.profile ?: return
        if (profile.avatar == avatarId) return
        viewModelScope.launch {
            runCatching { users.updateAvatar(profile.uid, avatarId) }.onFailure { error ->
                Log.w("Profile", "No se pudo cambiar el avatar", error)
                message.value = "No se pudo cambiar tu avatar. Inténtalo de nuevo."
            }
        }
    }

    fun messageShown() = message.update { null }
}
