package com.ternagames.terna.core.data.match

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DatabaseError
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.ternagames.terna.core.data.ServerException
import com.ternagames.terna.core.model.GameAction
import com.ternagames.terna.core.model.GameResult
import com.ternagames.terna.core.model.HandEnd
import com.ternagames.terna.core.model.MatchEvent
import com.ternagames.terna.core.model.MatchPlayer
import com.ternagames.terna.core.model.MatchRules
import com.ternagames.terna.core.model.MatchState
import com.ternagames.terna.core.model.RevealedCard
import com.ternagames.terna.core.model.TurnInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

interface MatchRepository {
    /** Estado de la partida en tiempo real. Emite null si la partida no existe. */
    fun observeMatch(matchId: String, myUid: String): Flow<MatchState?>

    /** Diferencia (ms) entre el reloj del servidor y el de este teléfono. */
    fun observeServerOffset(): Flow<Long>

    suspend fun act(matchId: String, expectedSeq: Long, action: GameAction)

    /** Avisa al servidor de que el turno actual se pasó de tiempo. */
    suspend fun reportTimeout(matchId: String, expectedSeq: Long)
}

@Singleton
class FirebaseMatchRepository @Inject constructor(
    private val database: FirebaseDatabase,
    private val functions: FirebaseFunctions,
) : MatchRepository {

    private fun snapshots(path: String): Flow<DataSnapshot> = callbackFlow {
        val ref = database.getReference(path)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override fun observeMatch(matchId: String, myUid: String): Flow<MatchState?> = combine(
        snapshots("matches/$matchId/meta"),
        snapshots("matches/$matchId/players"),
        snapshots("matches/$matchId/live"),
        snapshots("matches/$matchId/hands/$myUid"),
    ) { meta, players, live, hand -> parseMatch(matchId, meta, players, live, hand) }

    override fun observeServerOffset(): Flow<Long> = callbackFlow {
        val ref = database.getReference(".info/serverTimeOffset")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(Long::class.java) ?: 0L)
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }.distinctUntilChanged()

    override suspend fun act(matchId: String, expectedSeq: Long, action: GameAction) {
        call("gameAction", mapOf("matchId" to matchId, "expectedSeq" to expectedSeq, "action" to action.toPayload()))
    }

    override suspend fun reportTimeout(matchId: String, expectedSeq: Long) {
        call("timeoutTurn", mapOf("matchId" to matchId, "expectedSeq" to expectedSeq))
    }

    private suspend fun call(name: String, data: Map<String, Any?>) {
        try {
            functions.getHttpsCallable(name).call(data).await()
        } catch (e: FirebaseFunctionsException) {
            val reason = (e.details as? Map<*, *>)?.get("reason") as? String
                ?: when (e.code) {
                    FirebaseFunctionsException.Code.UNAVAILABLE,
                    FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> "NETWORK"
                    FirebaseFunctionsException.Code.UNAUTHENTICATED -> "UNAUTHENTICATED"
                    else -> e.code.name
                }
            throw ServerException(reason, e.message)
        }
    }
}

// ───────────── GameAction → datos para la función ─────────────

private fun HandEnd.wire() = if (this == HandEnd.Low) "LOW" else "HIGH"

private fun GameAction.toPayload(): Map<String, Any> = when (this) {
    is GameAction.RevealHand -> mapOf("type" to "REVEAL_HAND", "target" to target, "end" to end.wire())
    is GameAction.RevealCenter -> mapOf("type" to "REVEAL_CENTER", "slot" to slot)
    is GameAction.Claim -> mapOf("type" to "CLAIM", "target" to target, "value" to value, "count" to count)
    GameAction.Pass -> mapOf("type" to "PASS")
}

// ───────────── DataSnapshot → modelos ─────────────

private fun DataSnapshot.str(key: String): String = child(key).getValue(String::class.java).orEmpty()

private fun DataSnapshot.num(key: String): Long = child(key).getValue(Long::class.java) ?: 0L

private fun DataSnapshot.flag(key: String, default: Boolean): Boolean = child(key).getValue(Boolean::class.java) ?: default

private fun DataSnapshot.ints(): List<Int> = children.mapNotNull { it.getValue(Long::class.java)?.toInt() }

private fun String.toEnd(): HandEnd? = when (this) {
    "LOW" -> HandEnd.Low
    "HIGH" -> HandEnd.High
    else -> null
}

private fun DataSnapshot.toCard(): RevealedCard = RevealedCard(
    owner = child("owner").getValue(String::class.java),
    end = str("end").toEnd(),
    slot = child("slot").getValue(Long::class.java)?.toInt(),
    value = num("value").toInt(),
    claimed = flag("claimed", false),
)

private fun DataSnapshot.cards(key: String): List<RevealedCard> = child(key).children.map { it.toCard() }

private fun DataSnapshot.toResult(): GameResult? =
    if (!exists()) null else GameResult(winners = child("winners").children.mapNotNull { it.getValue(String::class.java) }, reason = str("reason"))

private fun DataSnapshot.toEvent(): MatchEvent? {
    val seq = num("seq")
    val player = str("player")
    return when (str("type")) {
        "TURN_STARTED" -> MatchEvent.TurnStarted(seq, player, num("number").toInt())
        "CARD_REVEALED" -> MatchEvent.CardRevealed(seq, player, child("reveal").toCard())
        "CLAIM_FAILED" -> MatchEvent.ClaimFailed(seq, player, str("target"), num("value").toInt())
        "TURN_FAILED" -> MatchEvent.TurnFailed(seq, player, cards("revealed"))
        "TURN_PASSED" -> MatchEvent.TurnPassed(seq, player, cards("revealed"))
        "TURN_TIMEOUT" -> MatchEvent.TurnTimeout(seq, player, cards("revealed"))
        "SET_COMPLETED" -> MatchEvent.SetCompleted(seq, player, num("value").toInt(), cards("cards"))
        "GAME_OVER" -> child("result").toResult()?.let { MatchEvent.GameOver(seq, it) }
        else -> null
    }
}

private fun parseMatch(matchId: String, meta: DataSnapshot, players: DataSnapshot, live: DataSnapshot, hand: DataSnapshot): MatchState? {
    if (!meta.exists() || !live.exists()) return null
    val turn = live.child("turn")
    return MatchState(
        id = matchId,
        roomId = meta.str("roomId"),
        ruleSet = meta.str("ruleSet"),
        finished = meta.str("status") == "FINISHED",
        turnSeconds = meta.num("turnSeconds").toInt(),
        rules = MatchRules(
            setSize = meta.num("setSize").toInt().takeIf { it > 0 } ?: 3,
            allowPass = meta.flag("allowPass", false),
            allowClaim = meta.flag("allowClaim", true),
            allowSelfReveal = meta.flag("allowSelfReveal", true),
            minRevealsBeforePass = meta.num("minRevealsBeforePass").toInt(),
        ),
        players = players.children.map {
            MatchPlayer(
                uid = it.key.orEmpty(),
                seat = it.num("seat").toInt(),
                name = it.str("name"),
                avatar = it.str("avatar").ifEmpty { "jaguar_01" },
                isBot = it.flag("isBot", false),
                connected = it.flag("connected", true),
                handCount = it.num("handCount").toInt(),
                sets = it.child("sets").ints(),
            )
        }.sortedBy { it.seat },
        seq = live.num("seq"),
        turn = TurnInfo(turn.str("player"), turn.num("number").toInt(), turn.num("startsAt"), turn.num("deadline")),
        revealed = live.cards("revealed"),
        center = live.child("center").children.map { it.getValue(Boolean::class.java) ?: false },
        events = live.child("lastEvents").children.mapNotNull { it.toEvent() },
        result = live.child("result").toResult(),
        myHand = hand.ints(),
    )
}
