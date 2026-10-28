# TERNA! — Especificación del Esquema de Base de Datos en Firebase (100% Online)

> **Fecha:** 25 de Octubre de 2026  
> **Arquitectura:** 100% Online · Servidor Autoritativo · Android (Kotlin) + Firebase (RTDB + Firestore + Cloud Functions)

---

## 1. Resumen de Arquitectura (Exclusivamente Online)

Al ser un juego exclusivamente online, el sistema adopta una arquitectura de servidor autoritativo estricto sin necesidad de persistencia ni motores de juego locales en Android:

```
┌────────────────────────────────────────────────────────────────────────┐
│                          APLICACIÓN ANDROID                            │
│  - Jetpack Compose UI (Tablero, Lobby, Perfil, Salas)                  │
│  - MVI / StateFlow / Repositorios                                      │
│  - Firebase Android SDK (Auth, RTDB, Firestore)                        │
└──────────────────┬─────────────────────────────▲───────────────────────┘
                   │                             │
    1. Envía Intención de Jugada                 │ 3. Sincronización reactiva
       (Callable / HTTPS)                        │    (WebSocket RTDB ~50-100ms)
                   │                             │
                   ▼                             │
┌────────────────────────────────────────────────┴───────────────────────┐
│                     FIREBASE BACKEND (us-central1)                     │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ Cloud Functions (2nd Gen, Node 22)                               │  │
│  │  - Motor de reglas puro: barajado, validación de turnos, bots    │  │
│  │  - Transacciones atómicas de partida                             │  │
│  │  - Cloud Tasks: temporizador de turno del lado del servidor      │  │
│  └───────────────┬─────────────────────────────┬────────────────────┘  │
│                  │                             │                       │
│    Actualizaciones en vivo                     │ Al finalizar partida  │
│                  ▼                             ▼                       │
│  ┌──────────────────────────────┐    ┌──────────────────────────────┐  │
│  │  Realtime Database (RTDB)    │    │       Cloud Firestore        │  │
│  │  - Estado en vivo efímero    │    │  - Perfiles y billetera      │  │
│  │  - Salas y Matchmaking       │    │  - Historial de partidas     │  │
│  │  - Presencia (onDisconnect)  │    │  - Estadísticas y Rankings   │  │
│  │  - Manos privadas por UID    │    │  - Solo lectura para cliente │  │
│  └──────────────────────────────┘    └──────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Recomendaciones de Base de Datos Aplicadas

Con la confirmación del modelo **100% Online**, se implementan las siguientes optimizaciones definitivas:

1. **Servidor Autoritativo Estricto:**
   - El cliente Android **nunca escribe el estado de partida, ni sus cartas, ni su experiencia (XP), ni sus estadísticas**.
   - Solo envía intenciones de acción mediante llamadas a Cloud Functions (`gameAction`, `createRoom`, `joinRoom`, `startMatch`).
   - Esto anula completamente la posibilidad de trampas, partidas inventadas o hackeo de monedas/XP.

2. **Aislamiento de la Rama de Juego en RTDB (`/matches/{matchId}/live`):**
   - Para no incurrir en costos excesivos de ancho de banda (over-fetching) ni saturar la memoria del teléfono, los datos que cambian en cada jugada (`turn`, `revealed`, `lastEvents`, `seq`) están aislados en el subnodo `live`.
   - La metadata (`meta`) y los datos fijos de los jugadores (`players`) no se vuelven a descargar en cada micro-acción.

3. **Eventos de la última jugada (`lastEvents`):**
   - Una sola jugada produce **varios eventos seguidos**: revelar → fallo → cambio de turno; "-1000 de aura" → fallo → turno; terna → victoria. Si solo se guardara el último, se perdería la carta que no coincidió (al fallar, `revealed` se vacía) y el mensaje de "-1000 de aura".
   - `live.lastEvents` guarda **todos los eventos de la última jugada**, en orden y con su `seq`. Lo genera `toPublicEvent()` del motor.
   - No hay riesgo de que dos escrituras se pisen: solo escribe el servidor, dentro de una transacción.
   - Si la app detecta un hueco en `seq` (por ejemplo, volvió de segundo plano), no anima: se sincroniza con el estado.

4. **Acceso Seguro y Flexible a Salas (`rooms`):**
   - La regla de RTDB permite que un usuario consulte la información de una sala si es de tipo `PUBLIC` antes de unirse, o si ya figura en `members`. Las salas privadas requieren validación de código mediante Cloud Functions.

5. **Índices Optimizados:**
   - Índice en RTDB (`.indexOn: ["createdAt", "ruleSet"]`) para la lista de salas disponibles sin sobrecargar la red móvil.
   - Índice en RTDB `queue/{clave}.createdAt` para que el emparejamiento tome primero a quien lleva más tiempo esperando.
   - Índice compuesto en Firestore `matchArchive: playerUids + endedAt` para el archivo global. El historial personal (`users/{uid}/matchHistory` ordenado por `endedAt`) y el ranking (`score`) usan índices de un solo campo, que Firestore crea automáticamente: declararlos como compuestos hace fallar el despliegue.

6. **Estadísticas Embebidas en Firestore:**
   - Las estadísticas de los modos de juego (`TERNA_CLASSIC`, `TERNA_SPICY`, `TRIO_ORIGINAL`, `CUARTA`) se guardan directamente dentro del documento del usuario `users/{uid}`. Cargar el perfil consume **1 sola lectura de Firestore** en lugar de múltiples lecturas de subcolecciones.

---

## 3. Esquema Completo: Realtime Database (RTDB)

Ubicación: **`us-central1`**.

```jsonc
{
  // 1. SALAS Y LOBBIES
  "rooms": {
    "{roomId}": {
      "name": "Sala de Miguel",
      "type": "PUBLIC",                   // "PUBLIC" | "PRIVATE" | "QUICKPLAY"
      "hostUid": "usr_abc123",
      "code": "TRN-7K4Q",                 // Código alfanumérico sin caracteres ambiguos
      "maxPlayers": 4,                    // 3 a 6 jugadores
      "status": "WAITING",                // "WAITING" | "STARTING" | "IN_MATCH" | "FINISHED"
      "currentMatchId": null,             // ID de la partida activa
      "createdAt": 1729780000000,
      "updatedAt": 1729780000000,
      "config": {
        "ruleSet": "TERNA_CLASSIC",       // "TERNA_CLASSIC" | "TERNA_SPICY" | "TRIO_ORIGINAL" | (futuro) "CUARTA"
        "turnSeconds": 20,
        "fillWithBots": true,
        "options": {                      // Cambios sobre el modo base: mismas claves que TurnOptions del motor
          "allowPass": false,             // Solo se guardan las opciones que el anfitrión cambió
          "allowClaim": true
        }
      },
      "members": {
        "{uid}": {
          "name": "Felipe",
          "avatar": "jaguar_01",
          "ready": true,
          "host": true,
          "joinedAt": 1729780000000
        }
      }
    }
  },

  // 2. ÍNDICE DE SALAS PÚBLICAS (Para la pantalla "Unirse a sala")
  "openRooms": {
    "{roomId}": {
      "name": "Sala de Miguel",
      "code": "TRN-7K4Q",
      "hostName": "Felipe",
      "players": 3,
      "max": 6,
      "ruleSet": "TERNA_CLASSIC",
      "createdAt": 1729780000000
    }
  },

  // 3. MAPEO PRIVADO DE CÓDIGOS (Solo Cloud Functions)
  "roomCodes": {
    "TRN-7K4Q": "{roomId}"
  },

  // 4. PARTIDAS EN VIVO. Todo lo de una partida cuelga de UN solo nodo para poder actualizarlo
  //    en una única transacción atómica (dos jugadas simultáneas nunca dejan la mesa a medias).
  //    Las reglas de lectura se declaran por rama: meta/players/live = jugadores de la partida;
  //    hands/{uid} = solo su dueño; secret = nadie. `matches/{id}` completo no se puede leer.
  "matches": {
    "{matchId}": {
      "meta": {
        "roomId": "{roomId}",
        "ruleSet": "TERNA_CLASSIC",
        "ruleVersion": 1,
        "status": "PLAYING",              // "PLAYING" | "FINISHED"
        "turnSeconds": 20,
        "createdAt": 1729780100000,
        "endedAt": null
      },
      "players": {
        "{uid}": {
          "seat": 0,
          "name": "Jaguar Veloz",
          "avatar": "jaguar_01",
          "isBot": false,
          "connected": true,
          "handCount": 7,                 // Solo el conteo de cartas restantes
          "sets": [3, 10]                 // Valores de las ternas completadas en mesa
        }
      },
      "live": {
        "seq": 42,                        // Contador monotónico de secuencia
        "turn": {
          "player": "{uid}",
          "number": 12,
          "startsAt": 1729780150000,
          "deadline": 1729780170000       // startsAt + turnSeconds * 1000
        },
        "revealed": [
          { "source": "HAND", "owner": "{uid}", "end": "HIGH", "value": 9, "claimed": false },
          { "source": "CENTER", "slot": 4, "value": 9 }
        ],
        "center": [true, true, false, true, true, true, true, true], // true = carta oculta en mesa (sin null: nunca se recorta)
        "lastEvents": [                   // Todos los eventos de la última jugada, en orden
          { "seq": 41, "type": "CARD_REVEALED", "player": "{uid}",
            "reveal": { "source": "HAND", "owner": "{uid2}", "end": "LOW", "value": 4, "claimed": false } },
          { "seq": 42, "type": "TURN_FAILED", "player": "{uid}", "revealed": [ /* cartas del turno */ ] },
          { "seq": 43, "type": "TURN_STARTED", "player": "{uid3}", "number": 13 }
          // Otros tipos: CLAIM_FAILED { target, value, taunt: "AURA_MINUS_1000" },
          // SET_COMPLETED { value, cards }, TURN_PASSED { revealed }, GAME_OVER { result }
        ],
        "result": null                    // Al terminar: { "winners": ["{uid}"], "reason": "SETS" | "INSTANT" | "LINKED" | "EXHAUSTED" }
      },
      "hands": {                          // Cada jugador lee ÚNICAMENTE su propia rama
        "{uid}": [1, 1, 4, 6, 9, 11, 12]  // Ordenada de menor a mayor
      },
      "secret": {                         // 100% bloqueado para clientes
        // Estado completo del motor como TEXTO JSON: RTDB borra los null del final de un arreglo y
        // el centro usa null para los huecos vacíos. Incluye las reglas, el centro oculto y el reloj.
        "state": "{\"v\":1,\"game\":{...},\"roomId\":\"...\",\"timing\":{\"startsAt\":0,\"deadline\":0}}"
      }
    }
  },

  // 5. DÓNDE ESTÁ JUGANDO CADA USUARIO (la escribe el servidor; la lee solo el dueño)
  //    La app lo consulta al abrir para reconectarse a su sala o partida en curso.
  "active": {
    "{uid}": { "roomId": "{roomId}", "matchId": "{matchId}" }   // matchId solo durante una partida
  },

  // 6. PRESENCIA (onDisconnect nativo). Solo estos dos campos; las reglas rechazan cualquier otro.
  "presence": {
    "{uid}": {
      "online": true,
      "lastSeen": 1729780200000           // ServerValue.TIMESTAMP; no se acepta una fecha futura
    }
  },

  // 7. COLA DE EMPAREJAMIENTO RÁPIDO (índice en createdAt)
  "queue": {
    "TERNA_CLASSIC_4": {
      "{ticketId}": {
        "uid": "{uid}",
        "createdAt": 1729780000000
      }
    }
  }
}
```

**Por qué los arreglos públicos no usan `null`:** RTDB guarda los arreglos como objetos con claves numéricas y no almacena valores `null`. Un arreglo como `[5, 2, 7, null]` vuelve como `[5, 2, 7]`. Por eso `live.center` usa `true/false` y el estado del motor (con huecos `null`) se guarda como texto.

**Barajado:** las partidas reales usan `crypto.randomInt` (`secureRng`). Un generador con semilla de 32 bits sería explotable: quien conozca su propia mano podría probar todas las semillas y deducir las manos de los demás. Por eso ya no se guarda ninguna `seed`.

---

## 4. Esquema Completo: Cloud Firestore

Ubicación: **`us-central1`**.

### 4.1 Colección: `users/{uid}`
Documento de perfil, estadísticas y economía del jugador.

| Campo | Tipo | Quién escribe | Descripción |
|---|---|---|---|
| `displayName` | `string` (1–20 caracteres) | Cliente / Servidor | Nombre público |
| `avatar` | `string` (`a-z 0-9 _`, máx. 32) | Cliente / Servidor | ID de avatar: `jaguar_01`, `oso_01`, `condor_01`, `tucan_01`, `guacamaya_01`, `rana_01`, `chiguiro_01`, `perezoso_01`, `delfin_01`, o `photo` (foto de Google, solo cuentas vinculadas). Un ID desconocido se muestra como `jaguar_01` |
| `anonymous` | `boolean` | Cliente (debe coincidir con la sesión) | Si es cuenta de invitado. Solo puede pasar de `true` a `false`, tras vincular Google/Facebook |
| `level` | `number` | **Solo Servidor** (el cliente crea con `1`) | Nivel de usuario |
| `xp` | `number` | **Solo Servidor** (el cliente crea con `0`) | Experiencia acumulada |
| `coins` | `number` | **Solo Servidor** (el cliente crea con `0`) | Monedas ganadas en partidas |
| `createdAt` | `timestamp` | Cliente, con `serverTimestamp()` | Fecha de registro |
| `lastLoginAt` | `timestamp` | Cliente, con `serverTimestamp()` | Última sesión activa |
| `stats` | `map` | **Solo Servidor** (no existe hasta la primera partida) | Mapa de estadísticas por modo de juego |

Reglas: al crear el perfil, el cliente solo puede enviar estos campos, con `level = 1`, `xp = 0`, `coins = 0` y las fechas puestas por el servidor. Al actualizar, solo puede cambiar `displayName`, `avatar`, `lastLoginAt` y `anonymous`. El cliente **no** puede borrar el perfil: el borrado de cuenta lo hace la función `deleteAccount` (Auth + perfil + historial).

> Los campos booleanos no empiezan por `is`: el SDK de Firebase para Kotlin/Java convierte `isAnonymous` en `anonymous` al leer y escribir, y los nombres dejarían de coincidir.

**Estructura del mapa `stats` en `users/{uid}`:**
```json
{
  "TERNA_CLASSIC": {
    "games": 35,
    "wins": 20,
    "sets": 64,
    "sevensWins": 3,
    "currentStreak": 2,
    "bestStreak": 6
  },
  "TERNA_SPICY": {
    "games": 12,
    "wins": 7,
    "sets": 21,
    "sevensWins": 0,
    "currentStreak": 1,
    "bestStreak": 4
  }
}
```

---

### 4.2 Subcolección: `users/{uid}/matchHistory/{matchId}`
Historial personal de partidas del usuario. Solo el servidor escribe aquí al finalizar una partida en RTDB.

| Campo | Tipo | Descripción |
|---|---|---|
| `matchId` | `string` | Identificador único de la partida |
| `roomId` | `string` | Sala donde se jugó |
| `ruleSet` | `string` | Modo de juego (`"TERNA_CLASSIC"`, `"TERNA_SPICY"`) |
| `result` | `string` | `"VICTORY"` o `"DEFEAT"` |
| `reason` | `string` | `"SETS"`, `"INSTANT"`, `"LINKED"` |
| `players` | `array<map>` | Lista de jugadores: `[{ uid, name, avatar, setsCount, winner }]` |
| `durationSec` | `number` | Duración de la partida en segundos |
| `endedAt` | `timestamp` | Fecha y hora de finalización |

### 4.2b Colección: `matchArchive/{matchId}`
Archivo global de partidas (auditoría y repeticiones). Lo escribe solo el servidor. Se llama distinto al historial personal para que no se mezclen en consultas de *collection group*.

| Campo | Tipo | Descripción |
|---|---|---|
| `playerUids` | `array<string>` | UIDs de quienes jugaron. Lo usan la regla de lectura y el índice `playerUids + endedAt` |
| `players` | `array<map>` | Igual que en el historial personal |
| `ruleSet`, `result`, `seed`, `durationSec`, `endedAt` | — | `result = { winners, reason }` |

---

### 4.3 Colección: `leaderboards/{season}/entries/{uid}`
Ranking por temporadas.

| Campo | Tipo | Quién escribe | Descripción |
|---|---|---|---|
| `displayName` | `string` | Servidor | Nombre del jugador |
| `avatar` | `string` | Servidor | Avatar del jugador |
| `score` | `number` | Servidor | Puntos de clasificación / Trofeos |
| `wins` | `number` | Servidor | Victorias en la temporada |
| `updatedAt` | `timestamp` | Servidor | Momento del cálculo |

---

## 4b. API del servidor (Cloud Functions callable)

El cliente **nunca escribe** salas ni partidas: solo llama a estas funciones con `Firebase.functions("us-central1")`. Todas exigen sesión; el UID sale del token, nunca de los datos enviados. Si algo falla, el error trae `details.reason` (código estable) para que la app muestre su propio mensaje en español.

| Función | Datos | Devuelve | `reason` posibles |
|---|---|---|---|
| `createRoom` | `{ maxPlayers: 3–6, isPrivate, turnSeconds?, ruleSet?, options? }` | `{ roomId, code }` | `INVALID_MAX_PLAYERS`, `INVALID_PRIVACY`, `UNKNOWN_RULESET`, `INVALID_TURN_SECONDS`, `INVALID_OPTIONS`, `ALREADY_IN_ROOM` |
| `joinRoom` | `{ code }` **o** `{ roomId }` (solo salas públicas) | `{ roomId }` | `INVALID_CODE`, `ROOM_NOT_FOUND`, `ROOM_PRIVATE`, `ROOM_FULL`, `ROOM_STARTED`, `BANNED`, `ALREADY_IN_ROOM` |
| `leaveRoom` | — | `{ left }` | `ROOM_IN_MATCH` |
| `kickPlayer` | `{ targetUid }` | — | `NOT_HOST`, `CANNOT_KICK_SELF`, `NOT_A_MEMBER`, `NOT_IN_ROOM` |
| `startMatch` | — (usa la sala del usuario) | `{ matchId }` | `NOT_HOST`, `NOT_ENOUGH_PLAYERS`, `ROOM_STARTED`, `NOT_IN_ROOM` |
| `gameAction` | `{ matchId, expectedSeq, action }` | `{ seq, finished }` | `NOT_YOUR_TURN`, `STALE_STATE`, `TOO_EARLY`, `GAME_OVER`, `INVALID_ACTION` y los del motor (`INVALID_SLOT`, `PASS_DISABLED`, `CLAIM_VALUE_MISMATCH`…) |

`action` es uno de: `{type:"REVEAL_HAND", target, end:"LOW"|"HIGH"}`, `{type:"REVEAL_CENTER", slot}`, `{type:"CLAIM", target, value, end?}` o `{type:"PASS"}`.

**`expectedSeq`** es el `live.seq` que la app tenía al tocar. Si otra jugada llegó antes, el servidor responde `STALE_STATE` y la app simplemente espera al estado nuevo (esto cubre el doble toque). **`TOO_EARLY`** protege la pausa tras un fallo: nadie juega antes de `live.turn.startsAt`, así todos alcanzan a ver las cartas.

Pendiente (fase siguiente): temporizador de turno con Cloud Tasks, bots que reemplacen a quien se desconecte, y la cola de partida rápida.

---

## 5. Modelos de Datos en Kotlin para Android Studio

```kotlin
package com.ternagames.terna.core.model

import com.google.firebase.Timestamp
import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

// Reglas para que los modelos se lean bien con el SDK de Firebase:
// - Todos los campos con valor por defecto (el SDK necesita un constructor vacío).
// - Ningún booleano empieza por "is": el SDK convierte isReady en "ready" y deja de coincidir.
// - Las fechas de Firestore son Timestamp, no Long (leerlas como Long hace fallar la app).

// ==========================================
// MODELOS DE REALTIME DATABASE (EN VIVO)
// ==========================================

@IgnoreExtraProperties
data class RoomDto(
    val name: String = "",
    val type: String = "PUBLIC",            // PUBLIC | PRIVATE | QUICKPLAY
    val hostUid: String = "",
    val code: String = "",
    val maxPlayers: Int = 4,
    val status: String = "WAITING",
    val currentMatchId: String? = null,
    val createdAt: Long = 0L,               // En RTDB las fechas sí son milisegundos (ServerValue.TIMESTAMP)
    val updatedAt: Long = 0L,
    val config: RoomConfigDto = RoomConfigDto(),
    val members: Map<String, RoomMemberDto> = emptyMap()
)

@IgnoreExtraProperties
data class RoomConfigDto(
    val ruleSet: String = "TERNA_CLASSIC",
    val turnSeconds: Int = 20,
    val fillWithBots: Boolean = true,
    val options: Map<String, Any> = emptyMap() // Claves de TurnOptions que el anfitrión cambió
)

@IgnoreExtraProperties
data class RoomMemberDto(
    val name: String = "",
    val avatar: String = "jaguar_01",
    val ready: Boolean = false,
    val host: Boolean = false,
    val joinedAt: Long = 0L
)

@IgnoreExtraProperties
data class MatchLiveDto(
    val seq: Long = 0L,
    val turn: TurnDto = TurnDto(),
    val revealed: List<PublicRevealDto> = emptyList(),
    val center: List<Boolean> = emptyList(),
    val lastEvents: List<GameEventDto> = emptyList(),
    val result: GameResultDto? = null
)

@IgnoreExtraProperties
data class TurnDto(
    val player: String = "",
    val number: Int = 1,
    val startsAt: Long = 0L,
    val deadline: Long = 0L
)

@IgnoreExtraProperties
data class PublicRevealDto(
    val source: String = "CENTER",          // HAND | CENTER
    val owner: String? = null,
    val end: String? = null,                // LOW | HIGH
    val slot: Int? = null,
    val value: Int = 0,
    val claimed: Boolean = false            // true = se obtuvo deduciendo ("Pedir carta")
)

/** Un evento del motor. Cada tipo usa solo algunos campos (ver types.ts → GameEvent). */
@IgnoreExtraProperties
data class GameEventDto(
    val seq: Long = 0L,
    val type: String = "",                  // TURN_STARTED | CARD_REVEALED | CLAIM_FAILED | TURN_FAILED | TURN_PASSED | SET_COMPLETED | GAME_OVER
    val player: String = "",
    val number: Int? = null,                // TURN_STARTED
    val reveal: PublicRevealDto? = null,    // CARD_REVEALED
    val revealed: List<PublicRevealDto> = emptyList(), // TURN_FAILED, TURN_PASSED
    val cards: List<PublicRevealDto> = emptyList(),    // SET_COMPLETED
    val target: String? = null,             // CLAIM_FAILED
    val value: Int? = null,                 // CLAIM_FAILED, SET_COMPLETED
    val taunt: String? = null,              // CLAIM_FAILED → "AURA_MINUS_1000"
    val result: GameResultDto? = null       // GAME_OVER
)

@IgnoreExtraProperties
data class GameResultDto(
    val winners: List<String> = emptyList(), // Puede haber empate
    val reason: String = ""                  // SETS | INSTANT | LINKED | EXHAUSTED
)

// ==========================================
// MODELOS DE FIRESTORE (PERSISTENTE)
// ==========================================

data class UserProfile(
    val displayName: String = "",
    val avatar: String = "jaguar_01",
    val anonymous: Boolean = true,
    val level: Int = 1,
    val xp: Long = 0L,
    val coins: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,   // null al crear → lo pone el servidor
    @ServerTimestamp val lastLoginAt: Timestamp? = null,
    val stats: Map<String, ModeStats> = emptyMap()       // El cliente nunca lo envía al crear
)

data class ModeStats(
    val games: Int = 0,
    val wins: Int = 0,
    val sets: Int = 0,
    val sevensWins: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0
)

data class MatchHistoryEntry(
    val matchId: String = "",
    val roomId: String = "",
    val ruleSet: String = "TERNA_CLASSIC",
    val result: String = "DEFEAT",
    val reason: String = "SETS",
    val players: List<MatchHistoryPlayer> = emptyList(),
    val durationSec: Int = 0,
    val endedAt: Timestamp? = null
)

data class MatchHistoryPlayer(
    val uid: String = "",
    val name: String = "",
    val avatar: String = "",
    val setsCount: Int = 0,
    val winner: Boolean = false
)
```
