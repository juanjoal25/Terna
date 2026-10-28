package com.ternagames.terna.feature.room.create

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ternagames.terna.core.data.ServerException
import com.ternagames.terna.core.data.auth.AuthRepository
import com.ternagames.terna.core.data.room.RoomRepository
import com.ternagames.terna.core.data.userMessage
import com.ternagames.terna.core.model.RoomLimits
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateRoomUiState(
    val maxPlayers: Int = RoomLimits.MAX_PLAYERS,
    val isPrivate: Boolean = true,
    val creating: Boolean = false,
    val message: String? = null,
    /** Cuando no es null, la sala ya existe y hay que entrar a ella. */
    val enterRoomId: String? = null,
)

@HiltViewModel
class CreateRoomViewModel @Inject constructor(
    private val rooms: RoomRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateRoomUiState())
    val state: StateFlow<CreateRoomUiState> = _state.asStateFlow()

    fun increase() = _state.update { it.copy(maxPlayers = (it.maxPlayers + 1).coerceAtMost(RoomLimits.MAX_PLAYERS)) }

    fun decrease() = _state.update { it.copy(maxPlayers = (it.maxPlayers - 1).coerceAtLeast(RoomLimits.MIN_PLAYERS)) }

    fun setPrivate(value: Boolean) = _state.update { it.copy(isPrivate = value) }

    fun messageShown() = _state.update { it.copy(message = null) }

    fun create() {
        val current = _state.value
        if (current.creating) return
        _state.update { it.copy(creating = true, message = null) }
        viewModelScope.launch {
            runCatching { rooms.createRoom(current.maxPlayers, current.isPrivate) }
                .onSuccess { created -> _state.update { it.copy(creating = false, enterRoomId = created.roomId) } }
                .onFailure { error ->
                    Log.w(TAG, "No se pudo crear la sala", error)
                    // Si ya estaba en una sala (p. ej. la app se cerró), lo devolvemos a ella.
                    val resume = if ((error as? ServerException)?.reason == "ALREADY_IN_ROOM") {
                        runCatching { auth.currentSession?.uid?.let { rooms.findActiveRoomId(it) } }.getOrNull()
                    } else {
                        null
                    }
                    _state.update {
                        if (resume != null) it.copy(creating = false, enterRoomId = resume)
                        else it.copy(creating = false, message = error.userMessage())
                    }
                }
        }
    }

    private companion object {
        const val TAG = "CreateRoom"
    }
}
