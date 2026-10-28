package com.ternagames.terna.feature.room.join

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ternagames.terna.core.data.ServerException
import com.ternagames.terna.core.data.auth.AuthRepository
import com.ternagames.terna.core.data.room.RoomRepository
import com.ternagames.terna.core.data.userMessage
import com.ternagames.terna.core.model.OpenRoom
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class JoinRoomUiState(
    val code: String = "",
    /** null mientras carga por primera vez. */
    val rooms: List<OpenRoom>? = null,
    /** ID de la sala (o "code") a la que se está intentando entrar. */
    val joining: String? = null,
    val message: String? = null,
    val enterRoomId: String? = null,
) {
    val canSearch: Boolean get() = code.length >= MIN_CODE_LENGTH && joining == null

    companion object {
        const val MIN_CODE_LENGTH = 4
        const val MAX_CODE_LENGTH = 8
        const val BY_CODE = "code"
    }
}

@HiltViewModel
class JoinRoomViewModel @Inject constructor(
    private val rooms: RoomRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(JoinRoomUiState())
    val state: StateFlow<JoinRoomUiState> = _state.asStateFlow()

    init {
        rooms.observeOpenRooms()
            .onEach { list -> _state.update { it.copy(rooms = list) } }
            .catch {
                Log.w(TAG, "No se pudo leer la lista de salas", it)
                _state.update { s -> s.copy(rooms = emptyList(), message = "No pudimos cargar las salas disponibles.") }
            }
            .launchIn(viewModelScope)
    }

    fun onCodeChange(raw: String) {
        // Solo letras, números y el guion del prefijo "TRN-"; siempre en mayúsculas.
        val clean = raw.uppercase().filter { it.isLetterOrDigit() || it == '-' }.take(JoinRoomUiState.MAX_CODE_LENGTH)
        _state.update { it.copy(code = clean) }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    fun joinByCode() {
        val code = _state.value.code
        if (!_state.value.canSearch) return
        join(JoinRoomUiState.BY_CODE) { rooms.joinByCode(code) }
    }

    fun joinOpenRoom(room: OpenRoom) {
        if (_state.value.joining != null) return
        join(room.id) { rooms.joinById(room.id) }
    }

    private fun join(marker: String, action: suspend () -> String) {
        _state.update { it.copy(joining = marker, message = null) }
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { roomId -> _state.update { it.copy(joining = null, enterRoomId = roomId) } }
                .onFailure { error ->
                    Log.w(TAG, "No se pudo entrar a la sala", error)
                    val resume = if ((error as? ServerException)?.reason == "ALREADY_IN_ROOM") {
                        runCatching { auth.currentSession?.uid?.let { rooms.findActiveRoomId(it) } }.getOrNull()
                    } else {
                        null
                    }
                    _state.update {
                        if (resume != null) it.copy(joining = null, enterRoomId = resume)
                        else it.copy(joining = null, message = error.userMessage())
                    }
                }
        }
    }

    private companion object {
        const val TAG = "JoinRoom"
    }
}
