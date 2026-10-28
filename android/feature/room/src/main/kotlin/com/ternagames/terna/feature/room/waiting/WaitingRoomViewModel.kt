package com.ternagames.terna.feature.room.waiting

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ternagames.terna.core.data.auth.AuthRepository
import com.ternagames.terna.core.data.room.RoomRepository
import com.ternagames.terna.core.data.userMessage
import com.ternagames.terna.core.model.Room
import com.ternagames.terna.core.model.RoomStatus
import com.ternagames.terna.feature.room.WaitingRoomRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ExitReason(val message: String?) {
    /** El jugador pulsó "Abandonar". */
    Left(null),

    /** La sala dejó de ser visible estando dentro: lo expulsó el anfitrión. */
    Kicked("El anfitrión te sacó de la sala."),

    /** Al abrirla, la sala ya no existía. */
    Closed("Esa sala ya no está disponible."),
}

data class WaitingRoomUiState(
    /** null mientras carga. */
    val room: Room? = null,
    val myUid: String = "",
    /** Hay una acción en curso (salir, expulsar, iniciar). */
    val busy: Boolean = false,
    /** Muestra el modal "¡Iniciando Partida!". */
    val starting: Boolean = false,
    val message: String? = null,
    val exit: ExitReason? = null,
    /** Cuando no es null, la partida ya existe y hay que ir a la mesa. */
    val matchId: String? = null,
) {
    val isHost: Boolean get() = room?.isHost(myUid) == true
}

@HiltViewModel
class WaitingRoomViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val rooms: RoomRepository,
    auth: AuthRepository,
) : ViewModel() {

    private val roomId = savedState.toRoute<WaitingRoomRoute>().roomId

    private val _state = MutableStateFlow(WaitingRoomUiState(myUid = auth.currentSession?.uid.orEmpty()))
    val state: StateFlow<WaitingRoomUiState> = _state.asStateFlow()

    private var hadRoom = false
    private var leaving = false
    private var matchHandled = false

    init {
        rooms.observeRoom(roomId)
            .onEach(::onRoom)
            .launchIn(viewModelScope)
    }

    private fun onRoom(room: Room?) {
        val me = _state.value.myUid
        when {
            room == null || !room.hasMember(me) -> {
                val reason = when {
                    leaving -> ExitReason.Left
                    hadRoom -> ExitReason.Kicked
                    else -> ExitReason.Closed
                }
                _state.update { it.copy(exit = reason) }
            }
            room.status == RoomStatus.InMatch && room.currentMatchId != null -> {
                hadRoom = true
                _state.update { it.copy(room = room, starting = true) }
                if (!matchHandled) {
                    matchHandled = true
                    // Un instante para que se vea el modal antes de pasar a la mesa.
                    viewModelScope.launch {
                        delay(START_MODAL_MS)
                        _state.update { it.copy(matchId = room.currentMatchId) }
                    }
                }
            }
            else -> {
                hadRoom = true
                _state.update { it.copy(room = room) }
            }
        }
    }

    fun leave() {
        if (leaving) return
        leaving = true
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching { rooms.leaveRoom() }
                .onSuccess { _state.update { it.copy(busy = false, exit = ExitReason.Left) } }
                .onFailure { error ->
                    Log.w(TAG, "No se pudo salir de la sala", error)
                    leaving = false
                    _state.update { it.copy(busy = false, message = error.userMessage()) }
                }
        }
    }

    fun kick(uid: String) = run { rooms.kickPlayer(uid) }

    fun start() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, starting = true) }
        viewModelScope.launch {
            runCatching { rooms.startMatch() }
                .onSuccess { _state.update { it.copy(busy = false) } } // el modal sigue hasta pasar a la mesa
                .onFailure { error ->
                    Log.w(TAG, "No se pudo iniciar la partida", error)
                    _state.update { it.copy(busy = false, starting = false, message = error.userMessage()) }
                }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun run(action: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching { action() }
                .onFailure { error ->
                    Log.w(TAG, "Falló la acción en la sala", error)
                    _state.update { it.copy(message = error.userMessage()) }
                }
            _state.update { it.copy(busy = false) }
        }
    }

    private companion object {
        const val TAG = "WaitingRoom"
        const val START_MODAL_MS = 1_200L
    }
}
