package com.ternagames.terna.core.data

/**
 * Error devuelto por el servidor. `reason` es el código estable que definen las funciones
 * (ver docs/ESQUEMA_BASE_DATOS_FIREBASE.md §4b), por ejemplo `ROOM_FULL`.
 */
class ServerException(val reason: String, message: String? = null) : Exception(message ?: reason)

/** Mensaje en español para mostrar al jugador. */
fun Throwable.userMessage(): String = when ((this as? ServerException)?.reason) {
    "ROOM_NOT_FOUND" -> "No existe una sala con ese código."
    "INVALID_CODE" -> "El código no es válido. Se ve así: TRN-7K4Q."
    "ROOM_FULL" -> "La sala está llena."
    "ROOM_STARTED" -> "La partida de esa sala ya empezó."
    "ROOM_PRIVATE" -> "Esa sala es privada: necesitas el código."
    "BANNED" -> "El anfitrión te sacó de esa sala."
    "ALREADY_IN_ROOM" -> "Ya estás en una sala."
    "NOT_ENOUGH_PLAYERS" -> "Se necesitan al menos 3 jugadores para empezar."
    "NOT_HOST" -> "Solo el anfitrión puede hacer eso."
    "ROOM_IN_MATCH" -> "No puedes salir mientras la partida está en curso."
    "NOT_YOUR_TURN" -> "Todavía no es tu turno."
    "TOO_EARLY" -> "Espera un momento: todos están viendo las cartas."
    "STALE_STATE" -> "La mesa cambió, inténtalo de nuevo."
    "GAME_OVER" -> "La partida ya terminó."
    "CLAIM_ALREADY_USED" -> "Solo puedes pedir una carta por turno. Sigue preguntando o pasa el turno."
    "PASS_TOO_EARLY" -> "Destapa al menos una carta antes de pasar el turno."
    "PASS_DISABLED" -> "En esta sala no se puede pasar el turno."
    "CLAIM_VALUE_MISMATCH" -> "Ya estás buscando otro número en este turno."
    "INVALID_COUNT" -> "No se puede pedir esa cantidad de cartas."
    "NO_HIDDEN_CARDS" -> "Esa persona ya no tiene cartas ocultas."
    "NETWORK" -> "Sin conexión. Revisa tu internet e inténtalo de nuevo."
    "UNAUTHENTICATED" -> "Tu sesión expiró. Vuelve a iniciar sesión."
    else -> "Algo salió mal. Inténtalo de nuevo."
}
