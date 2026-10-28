package com.ternagames.terna.core.data.room

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.ternagames.terna.core.data.ServerException
import com.ternagames.terna.core.model.ActiveEntry
import com.ternagames.terna.core.model.OpenRoom
import com.ternagames.terna.core.model.Room
import com.ternagames.terna.core.model.RoomMember
import com.ternagames.terna.core.model.RoomStatus
import com.ternagames.terna.core.model.RoomType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class CreatedRoom(val roomId: String, val code: String)

interface RoomRepository {
    /** Sala o partida en la que está el usuario (null si no está en ninguna). */
    fun observeActive(uid: String): Flow<ActiveEntry?>

    /** Una sola lectura: la sala a la que debe volver el usuario al abrir la app, si todavía existe. */
    suspend fun findActiveRoomId(uid: String): String?

    /** Emite null si la sala ya no existe o el usuario ya no puede verla (por ejemplo, lo expulsaron). */
    fun observeRoom(roomId: String): Flow<Room?>

    fun observeOpenRooms(): Flow<List<OpenRoom>>

    suspend fun createRoom(maxPlayers: Int, isPrivate: Boolean): CreatedRoom
    suspend fun joinByCode(code: String): String
    suspend fun joinById(roomId: String): String
    suspend fun leaveRoom()
    suspend fun kickPlayer(targetUid: String)
    suspend fun startMatch(): String
}

@Singleton
class FirebaseRoomRepository @Inject constructor(
    private val database: FirebaseDatabase,
    private val functions: FirebaseFunctions,
) : RoomRepository {

    // ───────────── Lectura en tiempo real ─────────────

    override fun observeActive(uid: String): Flow<ActiveEntry?> = callbackFlow {
        val ref = database.getReference("active/$uid")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val roomId = snapshot.child("roomId").getValue(String::class.java)
                trySend(roomId?.let { ActiveEntry(it, snapshot.child("matchId").getValue(String::class.java)) })
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override suspend fun findActiveRoomId(uid: String): String? {
        val roomId = database.getReference("active/$uid/roomId").get().await().getValue(String::class.java) ?: return null
        val stillMember = database.getReference("rooms/$roomId/members/$uid").get().await().exists()
        return roomId.takeIf { stillMember }
    }

    override fun observeRoom(roomId: String): Flow<Room?> = callbackFlow {
        val ref = database.getReference("rooms/$roomId")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.toRoom())
            }

            override fun onCancelled(error: DatabaseError) {
                // En una sala privada, al expulsarte pierdes el permiso de lectura: equivale a "ya no estás".
                trySend(null)
                close()
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    override fun observeOpenRooms(): Flow<List<OpenRoom>> = callbackFlow {
        val query = database.getReference("openRooms").orderByChild("createdAt").limitToLast(OPEN_ROOMS_LIMIT)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Las más nuevas primero.
                trySend(snapshot.children.mapNotNull { it.toOpenRoom() }.reversed())
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        query.addValueEventListener(listener)
        awaitClose { query.removeEventListener(listener) }
    }

    // ───────────── Acciones (pasan por Cloud Functions) ─────────────

    override suspend fun createRoom(maxPlayers: Int, isPrivate: Boolean): CreatedRoom {
        val data = call("createRoom", mapOf("maxPlayers" to maxPlayers, "isPrivate" to isPrivate))
        return CreatedRoom(roomId = data.string("roomId"), code = data.string("code"))
    }

    override suspend fun joinByCode(code: String): String = call("joinRoom", mapOf("code" to code)).string("roomId")

    override suspend fun joinById(roomId: String): String = call("joinRoom", mapOf("roomId" to roomId)).string("roomId")

    override suspend fun leaveRoom() {
        call("leaveRoom")
    }

    override suspend fun kickPlayer(targetUid: String) {
        call("kickPlayer", mapOf("targetUid" to targetUid))
    }

    override suspend fun startMatch(): String = call("startMatch").string("matchId")

    private suspend fun call(name: String, data: Map<String, Any?> = emptyMap()): Map<*, *> = try {
        functions.getHttpsCallable(name).call(data).await().getData() as? Map<*, *> ?: emptyMap<Any, Any>()
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

    private fun Map<*, *>.string(key: String): String =
        this[key] as? String ?: throw ServerException("BAD_RESPONSE", "Falta '$key' en la respuesta del servidor")

    private companion object {
        const val OPEN_ROOMS_LIMIT = 30
    }
}

// ───────────── Conversión de DataSnapshot → modelos ─────────────

private fun DataSnapshot.string(key: String): String = child(key).getValue(String::class.java).orEmpty()

private fun DataSnapshot.long(key: String): Long = child(key).getValue(Long::class.java) ?: 0L

private fun DataSnapshot.toRoom(): Room? {
    if (!exists()) return null
    val members = child("members").children.map {
        RoomMember(
            uid = it.key.orEmpty(),
            name = it.string("name"),
            avatar = it.string("avatar").ifEmpty { "jaguar_01" },
            joinedAt = it.long("joinedAt"),
            host = it.child("host").getValue(Boolean::class.java) ?: false,
        )
    }.sortedBy { it.joinedAt }
    return Room(
        id = key.orEmpty(),
        name = string("name"),
        type = when (string("type")) {
            "PRIVATE" -> RoomType.Private
            "QUICKPLAY" -> RoomType.QuickPlay
            else -> RoomType.Public
        },
        hostUid = string("hostUid"),
        code = string("code"),
        maxPlayers = long("maxPlayers").toInt(),
        status = when (string("status")) {
            "STARTING" -> RoomStatus.Starting
            "IN_MATCH" -> RoomStatus.InMatch
            else -> RoomStatus.Waiting
        },
        currentMatchId = child("currentMatchId").getValue(String::class.java),
        ruleSet = child("config").string("ruleSet"),
        members = members,
    )
}

private fun DataSnapshot.toOpenRoom(): OpenRoom? {
    val id = key ?: return null
    return OpenRoom(
        id = id,
        name = string("name"),
        hostName = string("hostName"),
        players = long("players").toInt(),
        max = long("max").toInt(),
        code = string("code"),
    )
}
