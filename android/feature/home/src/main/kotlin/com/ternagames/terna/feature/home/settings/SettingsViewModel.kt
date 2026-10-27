package com.ternagames.terna.feature.home.settings

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ternagames.terna.core.data.auth.AuthRepository
import com.ternagames.terna.core.data.auth.SignInCancelledException
import com.ternagames.terna.core.data.user.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val signedIn: Boolean = true,
    val guest: Boolean = true,
    val linking: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val users: UserRepository,
) : ViewModel() {

    private val local = MutableStateFlow(SettingsUiState())

    val state: StateFlow<SettingsUiState> = combine(auth.session, local) { session, ui ->
        ui.copy(signedIn = session != null, guest = session?.anonymous ?: true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** Convierte la cuenta de invitado en cuenta de Google, conservando el progreso. */
    fun linkGoogle(activityContext: Context) {
        if (local.value.linking) return
        local.update { it.copy(linking = true, message = null) }
        viewModelScope.launch {
            runCatching { users.ensureProfile(auth.signInWithGoogle(activityContext)) }
                .onSuccess { local.update { it.copy(linking = false, message = "¡Listo! Tu progreso quedó guardado en tu cuenta de Google.") } }
                .onFailure { error ->
                    Log.w("Settings", "No se pudo vincular Google", error)
                    val message = if (error is SignInCancelledException) null else "No se pudo vincular tu cuenta. Inténtalo de nuevo."
                    local.update { it.copy(linking = false, message = message) }
                }
        }
    }

    fun signOut() {
        viewModelScope.launch { auth.signOut() }
    }

    fun messageShown() = local.update { it.copy(message = null) }
}
