package com.ternagames.terna.core.model

enum class HandEnd { Low, High }

/** Una carta que se destapó durante el turno. Si sale de una mano, `owner` y `end` dicen de quién y de qué extremo. */
data class RevealedCard(
    val owner: String?,
    val end: HandEnd?,
    /** Hueco del centro; null si salió de una mano. */
    val slot: Int?,
    val value: Int,
    /** true si se obtuvo "pidiendo carta" (deduciendo) y no revelando a ciegas. */
    val claimed: Boolean,
) {
    val fromCenter: Boolean get() = slot != null
}

/** Dónde estaba la carta que no coincidía. Su valor no se publica: nadie la ve. */
data class MismatchPick(val owner: String?, val end: HandEnd?, val slot: Int?) {
    val fromCenter: Boolean get() = slot != null
}

data class GameResult(
    val winners: List<String>,
    /** SETS | INSTANT | LINKED | EXHAUSTED */
    val reason: String,
)

data class MatchPlayer(
    val uid: String,
    val seat: Int,
    val name: String,
    val avatar: String,
    val isBot: Boolean,
    val connected: Boolean,
    val handCount: Int,
    /** Valores de las ternas que ya completó. */
    val sets: List<Int>,
)

/** Reglas activas de esta partida: la app muestra solo los botones que aplican. */
data class MatchRules(
    val setSize: Int = 3,
    val allowPass: Boolean = false,
    val allowClaim: Boolean = true,
    val allowSelfReveal: Boolean = true,
    val minRevealsBeforePass: Int = 1,
    /** Cada rival solo puede ser objetivo una vez por turno. */
    val oncePerTarget: Boolean = false,
    /** Se pueden pedir varias cartas iguales de golpe. */
    val allowMultiClaim: Boolean = false,
    /** La carta que no coincide no se muestra: solo se avisa. */
    val hideMismatch: Boolean = false,
)

data class TurnInfo(
    val player: String,
    val number: Int,
    /** Hora del servidor desde la que se puede jugar (antes, todos ven las cartas del turno anterior). */
    val startsAt: Long,
    val deadline: Long,
)

/** Hechos que ocurrieron en la última jugada, en orden. La app los usa para mensajes y animaciones. */
sealed interface MatchEvent {
    val seq: Long

    data class TurnStarted(override val seq: Long, val player: String, val number: Int) : MatchEvent
    data class CardRevealed(override val seq: Long, val player: String, val card: RevealedCard) : MatchEvent

    /** Pidió una carta que el otro no tiene en un extremo: "-1000 de aura". */
    data class ClaimFailed(override val seq: Long, val player: String, val target: String, val value: Int) : MatchEvent
    /** `cards` son las que sí coincidían; con `hideMismatch`, `mismatch` señala dónde estaba la que no (sin valor). */
    data class TurnFailed(override val seq: Long, val player: String, val cards: List<RevealedCard>, val mismatch: MismatchPick? = null) : MatchEvent
    data class TurnPassed(override val seq: Long, val player: String, val cards: List<RevealedCard>) : MatchEvent
    data class TurnTimeout(override val seq: Long, val player: String, val cards: List<RevealedCard>) : MatchEvent
    data class SetCompleted(override val seq: Long, val player: String, val value: Int, val cards: List<RevealedCard>) : MatchEvent
    data class GameOver(override val seq: Long, val result: GameResult) : MatchEvent
}

/** Todo lo que un jugador puede ver de una partida: lo público más su propia mano. */
data class MatchState(
    val id: String,
    val roomId: String,
    val ruleSet: String,
    val finished: Boolean,
    val turnSeconds: Int,
    val rules: MatchRules,
    /** Ordenados por asiento (el orden de los turnos). */
    val players: List<MatchPlayer>,
    val seq: Long,
    val turn: TurnInfo,
    /** Cartas destapadas en el turno actual. */
    val revealed: List<RevealedCard>,
    /** Rivales a los que ya se les preguntó en este turno (solo con `oncePerTarget`). */
    val asked: List<String> = emptyList(),
    /** `true` = hay una carta boca abajo en ese hueco del centro. */
    val center: List<Boolean>,
    /** Eventos de la última jugada. */
    val events: List<MatchEvent>,
    val result: GameResult?,
    /** Mi mano, ordenada de menor a mayor. */
    val myHand: List<Int>,
) {
    fun player(uid: String): MatchPlayer? = players.firstOrNull { it.uid == uid }
    fun nameOf(uid: String): String = player(uid)?.name ?: "Jugador"
}

/** Lo que el jugador puede pedir al servidor en su turno. */
sealed interface GameAction {
    data class RevealHand(val target: String, val end: HandEnd) : GameAction
    data class RevealCenter(val slot: Int) : GameAction
    /** Pide `count` cartas seguidas de `value` a `target` (todo o nada). */
    data class Claim(val target: String, val value: Int, val count: Int = 1) : GameAction
    data object Pass : GameAction
}
