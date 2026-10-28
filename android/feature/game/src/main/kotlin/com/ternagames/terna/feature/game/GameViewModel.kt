package com.ternagames.terna.feature.game

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.ternagames.terna.core.data.ServerException
import com.ternagames.terna.core.data.auth.AuthRepository
import com.ternagames.terna.core.data.match.MatchRepository
import com.ternagames.terna.core.data.room.RoomRepository
import com.ternagames.terna.core.data.userMessage
import com.ternagames.terna.core.model.GameAction
import com.ternagames.terna.core.model.HandEnd
import com.ternagames.terna.core.model.MatchEvent
import com.ternagames.terna.core.model.MatchState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class BannerTone { Success, Error, Info }

data class Banner(val id: Long, val text: String, val tone: BannerTone)

/** Ventanas que guían al jugador paso a paso (ver GameDialogs.kt). */
sealed interface GameDialog {
    /** Elegir a qué jugador se le revela su carta mayor/menor. */
    data object PickRevealTarget : GameDialog

    /** Elegir si se revela la carta mayor o la menor de `target`. */
    data class PickEnd(val target: String) : GameDialog

    /** Elegir qué número se pide (cuando todavía no se destapó nada). */
    data object PickNumber : GameDialog

    /** Elegir a quién se le pide el número `value`. */
    data class PickClaimTarget(val value: Int) : GameDialog

    /** Elegir cuántas cartas iguales se le piden a `target` de una sola vez. */
    data class PickClaimCount(val target: String, val value: Int) : GameDialog
}

sealed interface GameExit {
    data class BackToRoom(val roomId: String) : GameExit
    data object Lobby : GameExit
}

data class GameUiState(
    val match: MatchState? = null,
    val myUid: String = "",
    /** Hora estimada del servidor (se actualiza varias veces por segundo). */
    val serverNow: Long = System.currentTimeMillis(),
    val busy: Boolean = false,
    val dialog: GameDialog? = null,
    val selectingCenter: Boolean = false,
    val banner: Banner? = null,
    val message: String? = null,
    val exit: GameExit? = null,
    /** La partida no existe o ya no se puede ver. */
    val gone: Boolean = false,
) {
    val canAct: Boolean get() = match?.let { GameLogic.canAct(it, myUid, serverNow) && serverNow < it.turn.deadline } == true && !busy
}

@HiltViewModel
class GameViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val matches: MatchRepository,
    private val rooms: RoomRepository,
    auth: AuthRepository,
) : ViewModel() {

    private val matchId = savedState.toRoute<GameRoute>().matchId
    private val myUid = auth.currentSession?.uid.orEmpty()

    private val _state = MutableStateFlow(GameUiState(myUid = myUid))
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private var serverOffset = 0L
    private var lastSeenSeq = -1L
    private var bannerCounter = 0L
    private var reportedTimeoutSeq = -1L

    init {
        matches.observeServerOffset().onEach { serverOffset = it }.launchIn(viewModelScope)

        matches.observeMatch(matchId, myUid)
            .onEach(::onMatch)
            .catch {
                Log.w(TAG, "No se pudo leer la partida", it)
                _state.update { s -> s.copy(gone = true) }
            }
            .launchIn(viewModelScope)

        // Reloj de la mesa: cuenta atrás, pausas entre turnos y aviso de tiempo agotado.
        viewModelScope.launch {
            while (true) {
                delay(TICK_MS)
                val now = System.currentTimeMillis() + serverOffset
                _state.update { it.copy(serverNow = now) }
                reportTimeoutIfNeeded(now)
            }
        }
    }

    // ───────────── Estado de la partida ─────────────

    private fun onMatch(match: MatchState?) {
        if (match == null) {
            _state.update { it.copy(gone = true) }
            return
        }
        val firstLoad = lastSeenSeq < 0
        val fresh = if (firstLoad) emptyList() else match.events.filter { it.seq > lastSeenSeq }
        lastSeenSeq = match.seq
        // Si el turno cambió, se cierra lo que el jugador tuviera a medias.
        _state.update { s ->
            val turnChanged = s.match?.turn?.let { it.player != match.turn.player || it.number != match.turn.number } ?: false
            s.copy(
                match = match,
                busy = false,
                dialog = if (turnChanged || match.finished) null else s.dialog,
                selectingCenter = if (turnChanged || match.finished) false else s.selectingCenter,
            )
        }
        bannerFor(fresh, match)?.let(::showBanner)
    }

    private fun showBanner(banner: Pair<String, BannerTone>) {
        val id = ++bannerCounter
        _state.update { it.copy(banner = Banner(id, banner.first, banner.second)) }
        viewModelScope.launch {
            delay(BANNER_MS)
            _state.update { if (it.banner?.id == id) it.copy(banner = null) else it }
        }
    }

    /** El mensaje más importante de una jugada. Se escribe desde el punto de vista de quien mira. */
    private fun bannerFor(events: List<MatchEvent>, match: MatchState): Pair<String, BannerTone>? {
        fun who(uid: String) = if (uid == myUid) "Tú" else match.nameOf(uid)
        val mine = { uid: String -> uid == myUid }

        events.filterIsInstance<MatchEvent.SetCompleted>().lastOrNull()?.let {
            val lucky = if (it.value == LUCKY_NUMBER) " ¡Terna de la suerte!" else ""
            return (if (mine(it.player)) "¡Completaste una terna de ${it.value}!" else "¡${match.nameOf(it.player)} completó una terna de ${it.value}!") + lucky to BannerTone.Success
        }
        events.filterIsInstance<MatchEvent.ClaimFailed>().lastOrNull()?.let {
            val target = match.nameOf(it.target)
            return if (mine(it.player)) {
                "¡Error! $target no tiene el ${it.value}. -1000 de aura. Pierdes tu turno." to BannerTone.Error
            } else {
                "${match.nameOf(it.player)} pidió el ${it.value} a ${if (mine(it.target)) "ti" else target} y falló: ¡-1000 de aura!" to BannerTone.Error
            }
        }
        events.filterIsInstance<MatchEvent.TurnTimeout>().lastOrNull()?.let {
            return (if (mine(it.player)) "Se acabó tu tiempo. Pierdes el turno." else "Se acabó el tiempo de ${match.nameOf(it.player)}.") to BannerTone.Info
        }
        events.filterIsInstance<MatchEvent.TurnFailed>().lastOrNull()?.let {
            return (if (mine(it.player)) "¡No coincide! Pierdes tu turno." else "${match.nameOf(it.player)} falló. Pasa el turno.") to BannerTone.Error
        }
        events.filterIsInstance<MatchEvent.TurnPassed>().lastOrNull()?.let {
            return "${who(it.player)} pasó el turno." to BannerTone.Info
        }
        events.filterIsInstance<MatchEvent.CardRevealed>().lastOrNull { it.card.claimed }?.let {
            val owner = it.card.owner?.let(match::nameOf).orEmpty()
            return (if (mine(it.player)) "¡Éxito! $owner sí tiene esa carta." else "${match.nameOf(it.player)} acertó: $owner tenía un ${it.card.value}.") to BannerTone.Success
        }
        return null
    }

    // ───────────── Tiempo agotado ─────────────

    /**
     * Cualquier jugador avisa al servidor cuando un turno se pasa de tiempo (el servidor lo comprueba con su
     * propio reloj). Quien tiene el turno avisa primero; los demás esperan un poco, escalonados por asiento,
     * para no repetir el aviso entre todos.
     */
    private fun reportTimeoutIfNeeded(now: Long) {
        val match = _state.value.match ?: return
        if (match.finished || reportedTimeoutSeq == match.seq) return
        val seatsAway = (match.players.indexOfFirst { it.uid == myUid } - match.players.indexOfFirst { it.uid == match.turn.player } + match.players.size) % match.players.size
        val grace = TIMEOUT_GRACE_MS + seatsAway * TIMEOUT_STAGGER_MS
        if (now < match.turn.deadline + grace) return

        reportedTimeoutSeq = match.seq
        viewModelScope.launch {
            runCatching { matches.reportTimeout(match.id, match.seq) }.onFailure {
                // Aún no vencía, la mesa ya avanzó o falló la red: si el turno sigue igual se reintenta más tarde.
                delay(RETRY_TIMEOUT_MS)
                if (reportedTimeoutSeq == match.seq) reportedTimeoutSeq = -1
            }
        }
    }

    // ───────────── Acciones del jugador ─────────────

    fun selectCenter() = _state.update { it.copy(selectingCenter = !it.selectingCenter, dialog = null) }

    fun onCenterSlot(slot: Int) {
        if (!_state.value.selectingCenter) return
        act(GameAction.RevealCenter(slot))
    }

    fun ask() = _state.update { it.copy(dialog = GameDialog.PickRevealTarget, selectingCenter = false) }

    fun claim() {
        val match = _state.value.match ?: return
        val chase = GameLogic.chaseValue(match)
        _state.update { it.copy(dialog = if (chase != null) GameDialog.PickClaimTarget(chase) else GameDialog.PickNumber, selectingCenter = false) }
    }

    fun onNumberPicked(value: Int) = _state.update { it.copy(dialog = GameDialog.PickClaimTarget(value)) }

    fun onRevealTargetPicked(uid: String) = _state.update { it.copy(dialog = GameDialog.PickEnd(uid)) }

    fun onEndPicked(target: String, end: HandEnd) = act(GameAction.RevealHand(target, end))

    fun onClaimTargetPicked(target: String, value: Int) {
        val match = _state.value.match ?: return
        // Si caben varias cartas iguales, se pregunta cuántas; si no, se pide una directamente.
        if (GameLogic.maxClaimCount(match, target) >= 2) {
            _state.update { it.copy(dialog = GameDialog.PickClaimCount(target, value)) }
        } else {
            act(GameAction.Claim(target, value))
        }
    }

    fun onClaimCountPicked(target: String, value: Int, count: Int) = act(GameAction.Claim(target, value, count))

    fun completeSet() {
        val match = _state.value.match ?: return
        val end = GameLogic.completeEnd(match, myUid) ?: return
        act(GameAction.RevealHand(myUid, end))
    }

    fun pass() = act(GameAction.Pass)

    fun dismissDialog() = _state.update { it.copy(dialog = null) }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun act(action: GameAction) {
        val s = _state.value
        val match = s.match ?: return
        if (s.busy) return
        _state.update { it.copy(busy = true, dialog = null, selectingCenter = false) }
        viewModelScope.launch {
            runCatching { matches.act(match.id, match.seq, action) }
                .onFailure { error ->
                    Log.w(TAG, "Jugada rechazada: $action", error)
                    // STALE_STATE: otra jugada llegó antes; la mesa ya se está actualizando, no hay nada que decir.
                    val stale = (error as? ServerException)?.reason == "STALE_STATE"
                    _state.update { it.copy(busy = false, message = if (stale) null else error.userMessage()) }
                }
            // Si salió bien, `busy` se libera cuando llega el estado nuevo de la mesa.
            delay(BUSY_SAFETY_MS)
            _state.update { if (it.busy) it.copy(busy = false) else it }
        }
    }

    // ───────────── Salida ─────────────

    fun backToRoom() {
        val roomId = _state.value.match?.roomId ?: return
        _state.update { it.copy(exit = GameExit.BackToRoom(roomId)) }
    }

    fun goToLobby() {
        viewModelScope.launch {
            // Al terminar la partida se puede salir de la sala; si falla, igual se vuelve al menú.
            runCatching { rooms.leaveRoom() }
            _state.update { it.copy(exit = GameExit.Lobby) }
        }
    }

    private companion object {
        const val TAG = "Game"
        const val TICK_MS = 250L
        const val BANNER_MS = 3_200L
        const val LUCKY_NUMBER = 7
        const val TIMEOUT_GRACE_MS = 1_000L
        const val TIMEOUT_STAGGER_MS = 700L
        const val RETRY_TIMEOUT_MS = 3_000L
        const val BUSY_SAFETY_MS = 6_000L
    }
}
