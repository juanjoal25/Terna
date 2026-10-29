package com.ternagames.terna.feature.game

import com.ternagames.terna.core.model.HandEnd
import com.ternagames.terna.core.model.MatchEvent
import com.ternagames.terna.core.model.MatchPlayer
import com.ternagames.terna.core.model.MatchState
import com.ternagames.terna.core.model.MismatchPick
import com.ternagames.terna.core.model.RevealedCard

/** Margen para relojes un poco desincronizados (el servidor acepta jugadas hasta 300 ms antes). */
private const val EARLY_TOLERANCE_MS = 300L

/** Números de las cartas (1 a 12). */
const val MAX_CARD_NUMBER = 12

/**
 * Reglas de la mesa calculadas desde lo que ve el jugador. El servidor valida todo de nuevo:
 * esto solo decide qué botones mostrar y qué opciones ofrecer.
 */
internal object GameLogic {

    fun isMyTurn(match: MatchState, me: String) = !match.finished && match.turn.player == me

    /** ¿Ya terminó la pausa que da la mesa para ver las cartas del turno anterior? */
    fun pauseOver(match: MatchState, serverNow: Long) = serverNow >= match.turn.startsAt - EARLY_TOLERANCE_MS

    fun canAct(match: MatchState, me: String, serverNow: Long) = isMyTurn(match, me) && pauseOver(match, serverNow)

    /** Número que se está persiguiendo este turno (el de la primera carta destapada). */
    fun chaseValue(match: MatchState): Int? = match.revealed.firstOrNull()?.value

    /** Cartas de la mano de `uid` que todavía no se destaparon este turno. */
    fun hiddenCards(match: MatchState, uid: String): Int {
        val handCount = match.player(uid)?.handCount ?: return 0
        return handCount - match.revealed.count { it.owner == uid }
    }

    /** Rivales a los que se les puede preguntar o pedir una carta: los que aún tienen cartas ocultas. */
    fun otherTargets(match: MatchState, me: String): List<MatchPlayer> =
        match.players.filter { it.uid != me && hiddenCards(match, it.uid) > 0 }

    /** ¿Ya se usó "Pedir carta" en este turno y la sala solo deja una? */
    fun claimUsedUp(match: MatchState): Boolean = match.rules.claimOncePerTurn && match.claimUsed

    /** "Pedir carta" disponible: la sala lo permite, no se usó ya en este turno y hay a quién pedirle. */
    fun canClaim(match: MatchState, me: String): Boolean =
        match.rules.allowClaim && !claimUsedUp(match) && otherTargets(match, me).isNotEmpty()

    /** Cartas que faltan en la mesa para formar la terna. */
    fun remainingToSet(match: MatchState): Int = (match.rules.setSize - match.revealed.size).coerceAtLeast(0)

    /** Máximo de cartas iguales que se le pueden pedir de una vez a `target` (1 si la sala no permite varias). */
    fun maxClaimCount(match: MatchState, target: String): Int {
        if (!match.rules.allowMultiClaim) return 1
        return minOf(remainingToSet(match), hiddenCards(match, target)).coerceAtLeast(1)
    }

    /** Jugadores a los que se les puede revelar su carta mayor o menor; incluye al propio jugador si las reglas lo permiten. */
    fun revealTargets(match: MatchState, me: String): List<MatchPlayer> {
        val others = otherTargets(match, me)
        val self = match.player(me)?.takeIf { match.rules.allowSelfReveal && hiddenCards(match, me) > 0 }
        return if (self != null) others + self else others
    }

    fun canRevealCenter(match: MatchState): Boolean = match.center.withIndex().any { (slot, hidden) -> hidden && match.revealed.none { it.slot == slot } }

    /** No queda nada que revelar ni pedir: el servidor deja pasar siempre, aunque la sala lo tenga apagado. */
    fun isDeadEnd(match: MatchState, me: String): Boolean =
        revealTargets(match, me).isEmpty() && !canRevealCenter(match) && !canClaim(match, me)

    fun canPass(match: MatchState, me: String): Boolean =
        (match.rules.allowPass && match.revealed.size >= match.rules.minRevealsBeforePass) || isDeadEnd(match, me)

    /** Por qué no se puede pasar todavía (null si se puede). */
    fun passBlockedReason(match: MatchState, me: String): String? = when {
        canPass(match, me) -> null
        !match.rules.allowPass -> "Esta sala no permite pasar el turno"
        else -> "Destapa una carta para poder pasar"
    }

    /** Quien juega después del jugador en turno. */
    fun nextPlayer(match: MatchState): MatchPlayer? {
        val i = match.players.indexOfFirst { it.uid == match.turn.player }
        return if (i < 0) null else match.players[(i + 1) % match.players.size]
    }

    /** Carta de `uid` que está a la vista en la mesa (para resaltar a ese rival), si la hay. */
    fun cardShownBy(tray: Tray, uid: String): RevealedCard? =
        if (tray.kind == TrayKind.Live || tray.kind == TrayKind.Completed) tray.cards.lastOrNull { it.owner == uid } else null

    /**
     * Extremo de mi propia mano con el que puedo cerrar la terna ("¡Hacer terna!"), o null.
     * Solo aplica cuando falta exactamente una carta para completarla.
     */
    fun completeEnd(match: MatchState, me: String): HandEnd? {
        val chase = chaseValue(match) ?: return null
        if (match.revealed.size != match.rules.setSize - 1) return null
        val hand = match.myHand
        val lowsUsed = match.revealed.count { it.owner == me && it.end == HandEnd.Low }
        val highsUsed = match.revealed.count { it.owner == me && it.end == HandEnd.High }
        val low = lowsUsed
        val high = hand.size - 1 - highsUsed
        if (low > high) return null
        return when {
            hand[low] == chase -> HandEnd.Low
            hand[high] == chase -> HandEnd.High
            else -> null
        }
    }

    // ───────── Cartas que se muestran en la mesa ─────────

    enum class TrayKind { Live, Failed, Passed, TimedOut, Completed }

    /**
     * `mismatch` señala la carta que no coincidía cuando la sala no la muestra: la mesa pinta una carta
     * boca abajo marcada en rojo en su lugar, sin revelar su valor.
     */
    data class Tray(val cards: List<RevealedCard>, val kind: TrayKind, val mismatch: MismatchPick? = null) {
        val isEmpty: Boolean get() = cards.isEmpty() && mismatch == null
    }

    /**
     * Cartas "en mesa". Durante la pausa que sigue a un fallo, un pase o una terna, el servidor ya vació las
     * cartas del turno, pero todos deben alcanzar a verlas: se toman del último evento.
     */
    fun tray(match: MatchState, serverNow: Long): Tray {
        if (serverNow < match.turn.startsAt) {
            for (event in match.events.reversed()) {
                when (event) {
                    is MatchEvent.TurnFailed -> return Tray(event.cards, TrayKind.Failed, event.mismatch)
                    is MatchEvent.TurnPassed -> return Tray(event.cards, TrayKind.Passed)
                    is MatchEvent.TurnTimeout -> return Tray(event.cards, TrayKind.TimedOut)
                    is MatchEvent.SetCompleted -> return Tray(event.cards, TrayKind.Completed)
                    else -> Unit
                }
            }
        }
        return Tray(match.revealed, TrayKind.Live)
    }

    /** Hueco del centro → valor que debe verse boca arriba (las cartas destapadas de ese hueco). */
    fun faceUpCenter(tray: Tray): Map<Int, Int> = tray.cards.mapNotNull { c -> c.slot?.let { it to c.value } }.toMap()

    /** Número que se perseguía cuando el turno falló (el de la primera carta que sí coincidía). */
    fun failedChase(tray: Tray): Int? = tray.cards.firstOrNull()?.value
}
