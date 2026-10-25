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
   - Para no incurrir en costos excesivos de ancho de banda (over-fetching) ni saturar la memoria del teléfono, los datos que cambian en cada jugada (`turn`, `revealed`, `lastEvent`, `seq`) están aislados en el subnodo `live`.
   - La metadata (`meta`) y los datos fijos de los jugadores (`players`) no se vuelven a descargar en cada micro-acción.

3. **Eventos Atómicos en vez de Arrays:**
   - Se reemplazó el array `lastEvents: []` por el objeto atómico `lastEvent: { seq, type, player, value }` para evitar sobrescrituras de datos concurrentes entre jugadores.

4. **Acceso Seguro y Flexible a Salas (`rooms`):**
   - La regla de RTDB permite que un usuario consulte la información de una sala si es de tipo `PUBLIC` antes de unirse, o si ya figura en `members`. Las salas privadas requieren validación de código mediante Cloud Functions.

5. **Índices Optimizados:**
   - Índice en RTDB (`.indexOn: ["createdAt", "ruleSet"]`) para la lista de salas disponibles sin sobrecargar la red móvil.
   - Índice compuesto en Firestore para consultar el historial de partidas ordenado por fecha de finalización.

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
        "ruleSet": "TERNA_CLASSIC",       // "TERNA_CLASSIC" | "TERNA_SPICY" | "TRIO_ORIGINAL" | "CUARTA"
        "turnSeconds": 20,
        "allowPass": false,
        "allowClaim": true,
        "fillWithBots": true
      },
      "members": {
        "{uid}": {
          "name": "Felipe",
          "avatar": "jaguar_01",
          "isReady": true,
          "isHost": true,
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

  // 4. PARTIDAS EN VIVO (Matches)
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
        "center": [true, true, false, true, true, true, true, true], // true = carta oculta en mesa
        "lastEvent": {
          "seq": 42,
          "type": "CARD_REVEALED",
          "player": "{uid}",
          "value": 9
        },
        "winner": null
      }
    }
  },

  // 5. MANOS PRIVADAS (Cada jugador lee ÚNICAMENTE su propia rama)
  "hands": {
    "{matchId}": {
      "{uid}": [1, 1, 4, 6, 9, 11, 12]   // Ordenada de menor a mayor
    }
  },

  // 6. CENTRO OCULTO (100% Bloqueado para clientes)
  "secret": {
    "{matchId}": {
      "center": [5, 2, null, 7, 7, 3, 8, 12],
      "seed": "f48c3b7a89e1"
    }
  },

  // 7. PRESENCIA (onDisconnect nativo)
  "presence": {
    "{uid}": {
      "online": true,
      "lastSeen": 1729780200000,
      "currentRoomId": "{roomId}",
      "currentMatchId": "{matchId}"
    }
  },

  // 8. COLA DE EMPAREJAMIENTO RÁPIDO
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

---

## 4. Esquema Completo: Cloud Firestore

Ubicación: **`us-central1`**.

### 4.1 Colección: `users/{uid}`
Documento de perfil, estadísticas y economía del jugador.

| Campo | Tipo | Quién escribe | Descripción |
|---|---|---|---|
| `displayName` | `string` | Cliente / Servidor | Nombre público |
| `avatar` | `string` | Cliente / Servidor | ID de recurso de avatar (`"jaguar_01"`) |
| `isAnonymous` | `boolean` | Servidor / Auth | Si es cuenta de invitado |
| `level` | `number` | **Solo Servidor** | Nivel de usuario (inicia en 1) |
| `xp` | `number` | **Solo Servidor** | Experiencia acumulada |
| `coins` | `number` | **Solo Servidor** | Monedas ganadas en partidas |
| `createdAt` | `timestamp` | Cliente (creación) | Fecha de registro |
| `lastLoginAt` | `timestamp` | Cliente / Servidor | Última sesión activa |
| `stats` | `map` | **Solo Servidor** | Mapa de estadísticas por modo de juego |

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
| `players` | `array<map>` | Lista de oponentes: `[{ uid, name, avatar, setsCount, isWinner }]` |
| `durationSec` | `number` | Duración de la partida en segundos |
| `endedAt` | `timestamp` | Fecha y hora de finalización |

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

## 5. Modelos de Datos en Kotlin para Android Studio

```kotlin
package com.ternagames.terna.core.model

import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.PropertyName

// ==========================================
// MODELOS DE REALTIME DATABASE (EN VIVO)
// ==========================================

@IgnoreExtraProperties
data class RoomDto(
    val name: String = "",
    val type: String = "PUBLIC",
    val hostUid: String = "",
    val code: String = "",
    val maxPlayers: Int = 4,
    val status: String = "WAITING",
    val currentMatchId: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val config: RoomConfigDto = RoomConfigDto(),
    val members: Map<String, RoomMemberDto> = emptyMap()
)

@IgnoreExtraProperties
data class RoomConfigDto(
    val ruleSet: String = "TERNA_CLASSIC",
    val turnSeconds: Int = 20,
    val allowPass: Boolean = false,
    val allowClaim: Boolean = true,
    val fillWithBots: Boolean = true
)

@IgnoreExtraProperties
data class RoomMemberDto(
    val name: String = "",
    val avatar: String = "jaguar_01",
    @get:PropertyName("isReady") val isReady: Boolean = false,
    @get:PropertyName("isHost") val isHost: Boolean = false,
    val joinedAt: Long = 0L
)

@IgnoreExtraProperties
data class MatchLiveDto(
    val seq: Long = 0L,
    val turn: TurnDto = TurnDto(),
    val revealed: List<PublicRevealDto> = emptyList(),
    val center: List<Boolean> = emptyList(),
    val lastEvent: GameEventDto? = null,
    val winner: String? = null
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
    val source: String = "CENTER",
    val owner: String? = null,
    val end: String? = null,
    val slot: Int? = null,
    val value: Int = 0,
    val claimed: Boolean = false
)

@IgnoreExtraProperties
data class GameEventDto(
    val seq: Long = 0L,
    val type: String = "",
    val player: String = "",
    val value: Int? = null
)

// ==========================================
// MODELOS DE FIRESTORE (PERSISTENTE)
// ==========================================

data class UserProfile(
    val displayName: String = "",
    val avatar: String = "jaguar_01",
    val isAnonymous: Boolean = false,
    val level: Int = 1,
    val xp: Long = 0L,
    val coins: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis(),
    val stats: Map<String, ModeStats> = emptyMap()
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
    val endedAt: Long = 0L
)

data class MatchHistoryPlayer(
    val uid: String = "",
    val name: String = "",
    val avatar: String = "",
    val setsCount: Int = 0,
    val isWinner: Boolean = false
)
```
