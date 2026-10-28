package com.ternagames.terna.feature.auth.splash

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ternagames.terna.core.data.auth.AuthRepository
import com.ternagames.terna.core.data.room.RoomRepository
import com.ternagames.terna.core.data.user.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SplashUiState(
    val progress: Float = 0f,
    val message: String = "Preparando baraja andina…",
    val destination: SplashDestination? = null,
)

sealed interface SplashDestination {
    data object Home : SplashDestination
    data object Login : SplashDestination

    /** El jugador estaba en una sala (la app se cerró o perdió conexión): vuelve a ella. */
    data class Room(val roomId: String) : SplashDestination
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val users: UserRepository,
    private val rooms: RoomRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SplashUiState())
    val state: StateFlow<SplashUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // La barra avanza mientras se prepara la sesión; dura al menos ~1,2 s para que se vea.
            val work = async { resolveDestination() }
            val steps = 24
            for (i in 1..steps) {
                delay(50)
                val cap = if (work.isCompleted) 1f else 0.9f
                _state.update { it.copy(progress = (i / steps.toFloat()).coerceAtMost(cap)) }
            }
            val destination = work.await()
            _state.update { it.copy(progress = 1f, message = "¡Listo!", destination = destination) }
        }
    }

    private suspend fun resolveDestination(): SplashDestination {
        val session = auth.currentSession ?: return SplashDestination.Login
        _state.update { it.copy(message = "Barajando tu perfil…") }
        // Si falla (sin red, reglas sin publicar), igual se entra: el Home lo reintenta al mostrarse.
        runCatching { users.ensureProfile(session) }.onFailure { Log.w(TAG, "No se pudo sincronizar el perfil", it) }
        val roomId = runCatching { rooms.findActiveRoomId(session.uid) }.getOrNull()
        return if (roomId != null) SplashDestination.Room(roomId) else SplashDestination.Home
    }

    private companion object {
        const val TAG = "Splash"
    }
}
