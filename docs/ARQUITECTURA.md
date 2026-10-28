# TERNA! — Documento de juego y arquitectura técnica

> Versión 0.1 · 2026-10-24 · Android (Kotlin) + Firebase

---

## 1. El juego

**TERNA!** es la adaptación móvil del juego de mesa *Trio* (Kaya Miyano, Cocktail Games) con identidad visual colombiana: cartas estilo papel picado, paisajes cafeteros, jaguar, cóndor, sombrero vueltiao. La experiencia de app (entrar como invitado, buscar partida, caer directo al tablero) se inspira en *UNO! Mobile*.

### 1.1 Reglas base (fieles al original)

- **Mazo:** 36 cartas, números del 1 al 12, tres copias de cada uno.
- **Jugadores:** 3 a 6.
- **Reparto:**

  | Jugadores | Cartas en mano | Cartas al centro (boca abajo) |
  |---|---|---|
  | 3 | 9 | 9 |
  | 4 | 7 | 8 |
  | 5 | 6 | 6 |
  | 6 | 5 | 6 |

- Cada mano se ordena automáticamente de menor a mayor (la app lo hace por el jugador).
- **Turno:** el jugador revela cartas una a una. Cada revelación puede ser:
  - la carta **más baja** o la **más alta** de la mano de cualquier jugador (incluida la propia), o
  - **cualquier carta boca abajo** del centro.
- Si la carta revelada coincide con las anteriores del turno, el jugador sigue. Si no coincide, **todas las cartas reveladas vuelven a su sitio** (a la mano o boca abajo en su mismo hueco del centro) y el turno pasa al siguiente.
- Al revelar **tres cartas iguales** se forma una **terna**: se coloca boca arriba frente al jugador y el turno termina (opción `continueAfterSet`).
- Pedir "la más baja" dos veces a la misma mano revela la **siguiente** más baja aún oculta.
- **Regla propia de TERNA!: deducir ("Pedir carta").** En lugar de revelar a ciegas, el jugador puede afirmar "Hugo, tienes un 5". Si ese número es ahora mismo la carta menor **o** la mayor de Hugo (entre sus cartas aún ocultas), Hugo la entrega y cuenta como revelada. Si no, Hugo responde con **"-1000 de aura"** y el turno se pierde. Si ya hay cartas reveladas en el turno, solo se puede deducir ese mismo número. Ventaja frente a revelar: comprueba los dos extremos a la vez y, si falla, no muestra ninguna carta.

### 1.1.1 Reglas configurables (implementadas en `backend/functions/src/engine`)

| Opción (`TurnOptions`) | TERNA (por defecto) | Trio original | Qué hace |
|---|---|---|---|
| `allowSelfReveal` | sí | sí | Revelar tu propia carta menor o mayor en cualquier momento del turno. Si se apaga, solo se permite cuando no queda otra carta oculta. |
| `allowClaim` | **sí** | no | Deducir ("Pedir carta"). |
| `claimRequiresEnd` | no | — | Obliga a decir también "tu menor" o "tu mayor". |
| `claimFailEndsTurn` | sí | — | Deducir mal hace perder el turno (si no, solo sale "-1000 de aura"). |
| `allowPass` / `minRevealsBeforePass` | no / 1 | no | "Pasar turno". Apagado por defecto porque permite espiar cartas sin riesgo. |
| `continueAfterSet` | no | no | Seguir jugando tras formar una terna. |

Modos predefinidos: `TERNA_CLASSIC`, `TERNA_SPICY` y `TRIO_ORIGINAL`. La sala aplica cambios encima con `withOptions(modo, {...})`.

### 1.2 Condiciones de victoria

| Modo | Gana quien… |
|---|---|
| **Clásico** | consigue 3 tríos, **o** el trío de 7 (victoria instantánea). |
| **Picante** | consigue 2 tríos **enlazados** (que sumen o resten 7: 1–6, 1–8, 2–5, 2–9, 3–4, 3–10, 4–11, 5–12…), **o** el trío de 7. |

Las miniaturas en la parte inferior de cada carta de tu diseño (el 1 muestra 6 y 8, el 2 muestra 5 y 9…) son exactamente los enlaces del modo Picante. El modo ya está contemplado en el arte.

### 1.3 La memoria es la mecánica

Lo único que hace interesante a Trio es **recordar** qué se reveló. Por eso:

- El tablero **no** muestra historial de cartas reveladas en modo normal.
- Se puede ofrecer un **modo "Aprendiz"** (solo en partidas privadas o contra bots) que sí muestra pistas.

### 1.4 Flujo de la app (según el diseño de Figma, `diseño/diseño app/`)

```
SPLASH ("Cargando… Preparando baraja andina")
  └─► LOGIN ── Google │ Facebook │ Jugar como invitado
        └─► HOME (menú principal: avatar, nombre, nivel, Ajustes, Créditos)
              ├─ CREAR SALA ── nº jugadores (3–6) + Privada/Pública ──► SALA DE ESPERA (anfitrión)
              ├─ UNIRSE A SALA ┬─ código ──────────────────────────────► SALA DE ESPERA (invitado)
              │                └─ lista "Salas disponibles" (públicas) ─► SALA DE ESPERA (invitado)
              └─ PARTIDA RÁPIDA ── modal "Buscando oponentes (1/4)" ─────► MESA
SALA DE ESPERA ── anfitrión pulsa "¡Iniciar partida!" ─► modal "¡Iniciando partida!" ─► MESA
MESA ─► Fin de partida ─► Revancha │ Home          (pantalla de fin: aún no diseñada)
```

Hay **tres formas de entrar a una partida**, y el backend debe soportarlas todas:

| Vía | Quién la crea | Cómo se entra | Arranque |
|---|---|---|---|
| Sala **privada** | Un jugador (anfitrión) | Solo con código / enlace | El anfitrión pulsa Iniciar (mín. 3) |
| Sala **pública** | Un jugador (anfitrión) | Aparece en "Salas disponibles" | El anfitrión pulsa Iniciar (mín. 3) |
| **Partida rápida** | El servidor | Emparejamiento automático | Se llena o vence la espera → bots |

### 1.4.1 Inventario de pantallas

| # | Pantalla (archivo) | Ruta en la app | Estados / variantes |
|---|---|---|---|
| 1 | Loading splash | `splash` | progreso real (auth + config + assets) |
| 2 | Landscape login | `login` | cargando, error de red |
| 3 | Lobby.png → **Home** | `home` | invitado / con cuenta |
| 4 | Buscando Partida Rápida (modal) | `home/quickplay` | buscando n/N, cancelando, error |
| 5 | Crear sala | `room/create` | — |
| 6 | Unirse a sala | `room/join` | lista vacía, cargando |
| 7 | Ingresar código (modal) | `room/join/code` | código inválido, sala llena, ya empezó |
| 8–11 | Sala de espera ×4 | `room/{id}` | privada/pública × anfitrión/invitado |
| 12 | Iniciando partida (modal) | sobre `room/{id}` | — |
| 13 | Mesa de juego (+ ~28 frames de flujo de turno) | `game/{id}` | ver §1.5 |
| 14 | Victoria normal (modal) | sobre `game/{id}` | "Volver a la sala" / "Ir al lobby" |
| 15 | Jackpot 777 + Victoria épica (modal) | sobre `game/{id}` | — |
| 16 | Perfil / Perfil invitado | `profile` | foto de Google + lápiz para editar / mascota para invitado |
| 17 | Ajustes / Ajustes invitado | `settings` | música, efectos, cerrar sesión |
| 18 | Créditos / Créditos invitado | `credits` | — |

En código, la pantalla `Lobby.png` / `Main menu / Lobby` se llama **Home**, porque el diseño usa "Lobby" también para la sala de espera.

Fuente: archivo de Figma "Terna" (páginas *Wireframes* y *Diseño de la app*); los frames de flujo se numeran `1.x` (empieza preguntando) y `2.x` (empieza por el centro).

### 1.5 Pantalla de tablero (según los flujos de Figma)

**Distribución (3 jugadores):** rivales arriba con su nombre, nº de cartas y mano boca abajo; centro de 3×3 sobre el logo tallado en la mesa; tu mano abanicada abajo; panel de acciones abajo a la derecha; tus ternas apiladas abajo a la izquierda con la insignia "N Ternas completadas"; las ternas de cada rival se apilan junto a su mano. Los huecos del centro se conservan cuando se retira una carta.

**Panel de acciones según el estado:**

| Estado | Panel | Qué pasa |
|---|---|---|
| Inicio de mi turno | "ES TU TURNO": **Revelar centro** / **Preguntar** | — |
| Preguntar | Modal "¿A quién deseas preguntar?" → modal "Carta mayor / Carta menor" | La carta aparece junto a la mano del rival |
| Revelar centro | Aviso "Selecciona una carta del centro"; el centro se resalta | La carta se voltea en su sitio |
| Carta revelada | **Tomar carta** / **Pasar turno** | "Tomar" la lleva a tu zona de revelado, junto a tu mano |
| 1+ cartas en zona | **Agarrar carta del centro** / **Pedir carta** | Sigue el turno |
| 2 iguales + la 3.ª en mi mano | **¡Hacer terna!** | Se forma la terna con tu propia carta |
| Acierto | Aviso verde "¡Éxito! Viana sí tiene esa carta." | — |
| Fallo | Aviso rojo "¡Error! … Pierdes tu turno."; el panel pasa a "Turno perdido" | Las cartas vuelven a su sitio |
| Turno ajeno | Panel gris "Turno de Hugo"; el rival se resalta con la etiqueta "Pensando…" | — |
| Trío de 7 | Banner "JACKPOT AUTOMÁTICO 777 · ¡Terna de la suerte!" → modal "¡Victoria épica!" | Victoria instantánea |
| 3 ternas | Modal "¡Victoria! Has completado 3 Ternas y dominado la mesa" | — |

**Diferencias con las reglas de Trio** (ya resueltas: "Pedir carta" es la regla de deducir de §1.1, y lo demás quedó configurable en §1.1.1):

1. **"Pasar turno"** → opción `allowPass`, apagada por defecto: si se puede pasar tras una carta, cada turno permite **espiar una carta sin riesgo**.
2. **"Tomar carta" como paso extra** (solo UI). Añade un toque por cada carta revelada. Recomendación: la carta va sola a la zona de revelado al terminar la animación de volteo.
3. **Revelar tu propia carta** → `allowSelfReveal`, encendida por defecto. "¡Hacer terna!" es el caso de la tercera carta (lo calcula `availableActions().completeWithOwn`), pero conviene permitirlo también como primera o segunda carta: añadir "Yo" al modal o dejar tocar los extremos de tu mano.
4. **Mensajes del fallo:**
   - Al **revelar** y no coincidir, todos deben ver la carta (~1,5 s) antes de que vuelva. La memoria es la mecánica. Texto sugerido: "La carta menor de Hugo es un 12. ¡No coincide!".
   - Al **deducir** mal ("Pedir carta"), no se muestra ninguna carta y sale el mensaje **"-1000 de aura"** (evento `CLAIM_FAILED`).
5. **Los demás deben ver cada revelación del turno ajeno**, no solo "Pensando…".

**Falta en el diseño:** temporizador de turno, distribuciones para 4–6 jugadores (centros de 8 o 6 cartas), botón para salir o abandonar la partida, indicador de rival desconectado o reemplazado por bot, y emotes (opcional).

### 1.6 Más allá del MVP

Login con Google para no perder el progreso, niveles/XP y monedas, ranking, cosméticos (reversos y mesas con distintas regiones de Colombia), **modo por equipos** (existe en el original, 4 o 6 jugadores en parejas), torneos semanales.

### 1.7 Modo futuro: CUARTA

Variante propia donde se gana con **cuartetos** (4 cartas iguales) en lugar de tríos.

- **Mazo:** 4 copias de cada número → 48 cartas (1–12 × 4). El arte actual sirve: solo cambia el número de copias.
- **Pendiente de diseñar y equilibrar:** tabla de reparto por nº de jugadores, cuántos cuartetos hacen falta para ganar, si el cuarteto de 7 sigue siendo victoria instantánea y cómo funciona el enlace del modo Picante.

**Qué se hace desde ya para no rehacer nada después** (cuesta poco ahora y mucho más tarde):

| Dónde | Preparación |
|---|---|
| Motor | Nada de "3" fijo en el código: todo sale de un `RuleSet` (ver §6.1). |
| Datos | Se habla de `sets`, no de `trios`; cada partida guarda `ruleSet` y `ruleVersion`. |
| UI | La zona de revelado, la zona de sets y la cuadrícula del centro se dibujan según `setSize` y el tamaño del centro, no con posiciones fijas. |
| Bots | Calculan probabilidades según `setSize` y `copies`. |
| Estadísticas | Se guardan por modo (`stats/{ruleSet}`). |

---

## 2. Principio rector: servidor autoritativo

Trio es un juego de **información oculta**. Si la app recibe el estado completo de la partida, cualquiera con un proxy o una app modificada ve todas las manos. Regla de oro:

> **El cliente nunca escribe estado de partida y nunca recibe cartas que no debería ver.**
> El cliente envía *intenciones* ("revela la más alta de Ana"); el servidor valida, aplica y publica el resultado.

Todo lo demás de la arquitectura se desprende de aquí.

---

## 3. Stack tecnológico

### 3.1 App Android

| Área | Elección | Por qué |
|---|---|---|
| Lenguaje | **Kotlin** 2.x | Estándar Android. |
| UI | **Jetpack Compose** + Material 3 (tema propio) | Animaciones declarativas fluidas, ideal para mover cartas. |
| Arquitectura | **MVI unidireccional** sobre Clean Architecture por capas | El estado del tablero llega del servidor; MVI lo vuelve predecible. |
| Inyección de dependencias | **Hilt** | Integración con ViewModel y Navigation. |
| Asíncrono | **Coroutines + Flow** (`callbackFlow` sobre los listeners de Firebase) | Estado reactivo de la partida. |
| Navegación | **Navigation Compose** (rutas tipadas) | |
| Animación | APIs de Compose (`Animatable`, `updateTransition`, `graphicsLayer.rotationY` para voltear) + **Lottie** para confeti/victoria | |
| Imágenes | Recursos **WebP** locales + **Coil 3** para avatares remotos | Ver §8, assets. |
| Audio | **SoundPool** (efectos) + **Media3/ExoPlayer** (música) | SoundPool tiene latencia baja para el "clic" de carta. |
| Persistencia local | **DataStore** (ajustes, último nombre, sonido) | Room no hace falta al inicio. |
| Hápticos | `HapticFeedback` / `Vibrator` | Al formar trío. |
| Enlaces a sala | **Android App Links** con Firebase Hosting (`terna.web.app/sala/ABC123`) | *Firebase Dynamic Links ya no existe (cerró en agosto de 2025)*. |
| Tests | JUnit5, **Turbine** (Flows), MockK, Compose UI Test | |

### 3.2 Firebase (plan **Blaze**, obligatorio para Cloud Functions)

| Servicio | Uso |
|---|---|
| **Authentication** | Tres botones en el login: **Google** (Credential Manager), **Facebook** (Facebook Login SDK; requiere app en Meta for Developers) e **invitado** (Anonymous Auth). Un invitado puede vincular su cuenta después con `linkWithCredential` y conserva su UID y su progreso. |
| **Realtime Database (RTDB)** | **Estado en vivo** de partidas, salas, cola de búsqueda y **presencia** (`onDisconnect`). |
| **Cloud Firestore** | Datos **persistentes**: perfiles, estadísticas, historial de partidas, ranking. |
| **Cloud Functions (2ª gen, TypeScript, Node 22)** | Toda la lógica de juego: motor de reglas, matchmaking, bots, temporizadores. |
| **Cloud Tasks** (vía `onTaskDispatched`) | Temporizador de turno del lado del servidor. |
| **App Check** (Play Integrity) | Solo tu app firmada puede llamar a las funciones y a la BD. |
| **Remote Config** | Duración del turno, tiempo de espera del matchmaking, dificultad de bots, *feature flags*. |
| **Crashlytics + Analytics + Performance** | Calidad y métricas (embudo Home → partida → fin). |
| **Hosting** | `assetlinks.json` para App Links, página de invitación y políticas. |
| **Cloud Messaging** | (Fase 2) invitaciones de amigos y "tu sala va a empezar". |
| **App Distribution** | Betas internas antes de Play Console. |
| **Emulator Suite** | Desarrollo local completo (Auth, RTDB, Firestore, Functions) sin gastar ni ensuciar producción. |

### 3.3 ¿Por qué RTDB + Firestore y no solo uno?

| | Realtime Database | Firestore |
|---|---|---|
| Latencia de sincronización | Más baja | Algo mayor |
| Coste en partidas (muchas escrituras pequeñas, N oyentes) | Se cobra por GB transferido, barato | Se cobra **por lectura**, cada jugada × cada jugador |
| Presencia / desconexión | `onDisconnect` nativo | No tiene; la propia guía de Firebase usa RTDB para esto |
| Consultas complejas (ranking, historial) | Pobre | Excelente |

**Decisión:** lo efímero y veloz (la partida) en RTDB; lo duradero y consultable (el jugador) en Firestore. Al terminar cada partida, una función copia el resumen a Firestore y borra el nodo de RTDB.

---

## 4. Arquitectura general

```
┌──────────────────────── ANDROID (Kotlin + Compose) ───────────────────────┐
│  UI (Compose)  ◄── StateFlow<UiState> ── ViewModel (MVI) ── Intents ──►   │
│                                              │                            │
│                                  UseCases / Repositorios                  │
│                     ┌────────────────────────┼─────────────────────┐      │
│              GameRemoteSource        MatchmakingSource       ProfileSource│
└─────────────────────┼────────────────────────┼─────────────────────┼──────┘
      escucha (Flow)  │  ▲ llama (callable)    │                     │
                      ▼  │                     ▼                     ▼
┌────────────────── FIREBASE ───────────────────────────────────────────────┐
│  Realtime DB  ◄──escribe── Cloud Functions ──escribe──►  Firestore         │
│  /matches/*                 ├ api/   (callables: jugar, salas, revelar)    │
│  /hands/*  (solo dueño)     ├ engine/ (reglas puras, sin Firebase)         │
│  /secret/* (nadie)          ├ bots/                                         │
│  /presence/*                ├ triggers/ (presencia, fin de partida)         │
│                             └ tasks/  (timeout de turno vía Cloud Tasks)   │
│  Auth (anónimo→Google) · App Check · Remote Config · Crashlytics           │
└────────────────────────────────────────────────────────────────────────────┘
```

### 4.1 Ciclo de una jugada

```
1. Jugador toca "Más alta" de Ana
2. App ─► callable gameAction({matchId, turnNo, seq, action:{type:"REVEAL_HAND", target:"ana", end:"HIGH"}})
3. Función: verifica auth + App Check → transacción en /matches/{id}
      ├ ¿es su turno? ¿turnNo y seq coinciden? (evita doble toque y jugadas viejas)
      ├ engine.apply(state, action) → nuevo estado + eventos
      └ escribe en una sola actualización multiruta:
           /matches/{id}/public   (carta revelada, seq+1, último evento)
           /hands/{id}/{ana}      (si cambia la mano)
           /secret/{id}           (centro, si cambia)
4. Todos los clientes reciben el cambio por su listener (~50–150 ms)
5. Cada cliente encola el evento → anima → luego aplica el estado
```

Latencia típica total con la función "caliente": **~200–400 ms**. Para que se sienta instantáneo:

- **Feedback optimista:** al tocar, la carta objetivo se levanta y brilla *antes* de la respuesta; el volteo ocurre cuando llega el valor.
- **`minInstances: 1`** en la función `gameAction` para evitar *cold starts* (cuesta unos pocos dólares al mes).
- **Una sola función** `gameAction` para todas las acciones de partida (con `concurrency` alta) en vez de una función por acción: una sola instancia caliente lo atiende todo.
- **Misma región** para RTDB, Firestore y Functions: **`us-central1`**. Realtime Database solo se aloja en unas pocas ubicaciones, y en América es `us-central1`, así que el resto va allí también. Las regiones de RTDB y Firestore no se pueden cambiar después (ver [FIREBASE_SETUP.md](FIREBASE_SETUP.md) §3).

### 4.2 Eventos + estado (clave de la fluidez)

El servidor publica dos cosas:

- `public` = **estado actual** completo (fuente de verdad, sirve para reconectar).
- `public/lastEvents` = **eventos** con número de secuencia (`CARD_REVEALED`, `TURN_FAILED`, `TRIO_WON`, `TURN_CHANGED`, `GAME_OVER`).

El cliente **no** salta de un estado a otro: reproduce los eventos en orden en una `AnimationQueue` y solo al terminar refleja el estado. Si detecta un hueco en `seq` (por ejemplo, volvió de segundo plano), descarta la cola y se sincroniza con `public` sin animar.

**Fallo de turno:** el servidor emite `TURN_FAILED` con las cartas reveladas y fija `turn.startsAt = ahora + 1500 ms`. Todos ven las cartas 1,5 s antes de que regresen, y el siguiente jugador no puede actuar antes de esa hora. El ritmo es idéntico para todos.

---

## 5. Modelo de datos

### 5.1 Realtime Database (partidas en vivo)

**Sala ≠ partida.** El modal de victoria ofrece "Volver a la sala", así que la sala (anfitrión, jugadores, código) **sobrevive** a cada partida y permite jugar la revancha con el mismo grupo. Por eso hay dos entidades: `rooms` (persiste mientras haya gente) y `matches` (una por partida jugada). Las partidas rápidas crean una sala técnica sin anfitrión.

```jsonc
{
  "rooms": {
    "{roomId}": {
      "name": "Sala de Miguel", "type": "PUBLIC | PRIVATE | QUICKPLAY",
      "hostUid": "uid", "code": "TRN-7K4Q", "maxPlayers": 6,
      "config": { "ruleSet": "TRIO_CLASSIC", "turnSeconds": 20, "fillWithBots": true },
      "status": "WAITING | IN_MATCH",
      "currentMatchId": null,
      "members": { "{uid}": { "name": "Felipe", "avatar": "jaguar_01", "joinedAt": 0 } }
    }
  },
  "matches": {
    "{matchId}": {
      "meta": {
        "roomId": "{roomId}",
        "ruleSet": "TRIO_CLASSIC",  // cuántas copias, tamaño del set, reparto, victoria
        "ruleVersion": 1,
        "status": "PLAYING | FINISHED",
        "turnSeconds": 20,
        "createdAt": 1729780000000
      },
      "players": {
        "{uid}": {
          "seat": 0, "name": "Jaguar Veloz", "avatar": "jaguar_01",
          "isBot": false, "connected": true,
          "handCount": 7, "sets": [3, 10]   // tríos (o cuartetos en CUARTA)
        }
      },
      "public": {
        "seq": 42,
        "turn": { "uid": "uid", "number": 12, "startsAt": 0, "deadline": 0 },
        "revealed": [ { "from": "HAND", "owner": "uid", "end": "HIGH", "value": 9 },
                      { "from": "CENTER", "slot": 4, "value": 9 } ],
        "center": [true, true, false, true, true, true, true, true],   // hay carta en el hueco
        "lastEvents": [ { "seq": 42, "type": "CARD_REVEALED", "by": "uid", "value": 9 } ],
        "winner": null
      }
    }
  },
  "hands":    { "{matchId}": { "{uid}": [1, 1, 4, 6, 9, 11, 12] } },   // lee SOLO el dueño
  "secret":   { "{matchId}": { "center": [5, 2, null, 7, 7, 3, 8, 12], "seed": "…" } }, // nadie
  "presence": { "{uid}": { "online": true, "lastSeen": 0, "matchId": "…" } },
  "roomCodes":{ "TRN-7K4Q": "{roomId}" },
  "openRooms":{ "{roomId}": { "name": "Sala de Miguel", "players": 3, "max": 6, "ruleSet": "TRIO_CLASSIC" } }, // índice de salas públicas
  "queue":    { "TRIO_CLASSIC_4": { "{roomId}": { "seats": 2, "openUntil": 0 } } }
}
```

### 5.2 Reglas de seguridad RTDB (esqueleto)

```jsonc
{
  "rules": {
    ".read": false, ".write": false,
    "matches": { "$m": {
      ".read": "auth != null && root.child('matches/'+$m+'/players/'+auth.uid).exists()"
    }},
    "hands": { "$m": { "$uid": { ".read": "auth != null && auth.uid === $uid" } } },
    "secret": { ".read": false },
    "openRooms": { ".read": "auth != null" },
    "rooms": { "$r": {
      ".read": "auth != null && root.child('rooms/'+$r+'/members/'+auth.uid).exists()"
    }},
    "presence": { "$uid": {
      ".read": "auth != null",
      ".write": "auth != null && auth.uid === $uid"
    }}
  }
}
```

Escritura de partidas: **ningún cliente**; solo el Admin SDK desde Functions (las reglas no aplican a Admin).

### 5.3 Firestore (persistente)

```
users/{uid}            displayName, avatar, isAnonymous, level, xp, coins, createdAt
users/{uid}/stats/{ruleSet}  games, wins, sets, sevensWins, streak   (uno por modo)
matchHistory/{matchId} mode, players[], winner, durationSec, endedAt
leaderboards/{season}/entries/{uid}  score
```

Reglas: cada usuario lee y escribe solo campos cosméticos de su perfil (nombre, avatar); `xp`, `coins` y `stats` los escribe únicamente el servidor.

---

## 6. Backend: Cloud Functions

### 6.1 Estructura

```
backend/
├─ firebase.json · database.rules.json · firestore.rules · .firebaserc (dev / prod)
└─ functions/src/
   ├─ engine/          ← MOTOR DE REGLAS PURO (sin imports de Firebase)
   │   ├─ types.ts      State, Action, Event
   │   ├─ rulesets.ts   TRIO_CLASSIC, TRIO_SPICY, (futuro) CUARTA…
   │   ├─ setup.ts      reparto según nº de jugadores, barajado con crypto.randomInt
   │   ├─ reducer.ts    apply(state, action) → { state, events } | error
   │   ├─ victory.ts    clásico / picante / trío de 7
   │   └─ *.test.ts     tests exhaustivos de cada regla
   ├─ bots/            ← memoria imperfecta según dificultad
   ├─ api/             ← callables: quickPlay, createRoom, joinRoom, leaveRoom,
   │                     startMatch, gameAction, rematch
   ├─ tasks/           ← turnTimeout (Cloud Tasks)
   ├─ triggers/        ← onPresenceChange, onMatchFinished → Firestore
   └─ scheduled/       ← limpieza de salas abandonadas (cada 15 min)
```

El **motor es una función pura y determinista**: misma semilla + mismas acciones = misma partida. Así se puede testear al 100 %, reproducir bugs reportados y reutilizarlo en los bots.

### 6.2 Matchmaking público (estilo UNO)

1. `quickPlay({mode, players})` busca en `/queue/{mode}_{n}` una partida `WAITING` con puestos libres y se une por **transacción** (sin carreras entre dos jugadores por el último puesto).
2. Si no hay ninguna, crea una y la publica en la cola.
3. Arranca al llenarse **o** a los 20 s (Remote Config): los puestos vacíos se completan con **bots**. Clave en el lanzamiento, cuando hay pocos jugadores.

### 6.3 Salas creadas por jugadores (privadas y públicas)

- **Código:** el diseño usa `TRN-749` (3 dígitos = solo 1.000 combinaciones, fácil de adivinar y de agotar). Se mantiene el prefijo visual `TRN-`, pero con **4–5 caracteres alfanuméricos sin ambiguos** (sin `0/O/1/I`), por ejemplo `TRN-7K4Q`. Se reserva con transacción en `/roomCodes`.
- **Enlace:** `https://terna.web.app/sala/TRN-7K4Q` (App Link). Abre la app directamente en la sala de espera; sin la app, lleva a Play Store.
- **Salas públicas:** se publican en el índice `/openRooms` (solo `WAITING`, con puestos libres). La pantalla "Salas disponibles" lo escucha en tiempo real, limitado a ~20 filas. Al llenarse o empezar, la sala sale del índice.
- **Anfitrión:** elige nº de jugadores y privacidad (en el diseño), y además modo, tiempo de turno y relleno con bots (por añadir). Puede **expulsar** jugadores (botón ✕ rojo): callable `kickPlayer`. Si el anfitrión se va, la sala pasa al siguiente jugador más antiguo.
- **Callables nuevas:** `createRoom`, `joinRoom(code | roomId)`, `leaveRoom`, `kickPlayer`, `startMatch`, `listOpenRooms` (o lectura directa del índice).

### 6.4 Turnos, desconexiones y bots

- **Timeout de turno:** al empezar cada turno se encola una tarea en Cloud Tasks con `(matchId, turnNo)`. Si al dispararse el turno no ha cambiado, el servidor juega por ese jugador (bot fácil).
- **Presencia:** el cliente registra `onDisconnect()` en `/presence/{uid}`. El trigger `onPresenceChange` marca `connected:false`; tras **30 s** de gracia, un bot toma el puesto. Si el jugador vuelve, recupera su puesto.
- **Bots:** recuerdan cada carta revelada con una probabilidad según su nivel (fácil 40 %, medio 70 %, difícil 95 %) y eligen la jugada con más probabilidad de éxito. Juegan con un retardo humano (0,8–1,5 s).

### 6.5 Anti-trampas

- Barajado y reparto solo en el servidor (`crypto.randomInt`, Fisher-Yates).
- El cliente no puede leer manos ajenas ni el centro (reglas de §5.2).
- App Check obligatorio en todas las callables.
- `turnNo` + `seq` en cada acción: idempotencia y rechazo de jugadas viejas o repetidas.
- Límite de frecuencia por UID en `gameAction`.

---

## 7. App Android

### 7.1 Módulos

```
android/
├─ app/                      MainActivity, NavHost, Hilt, App Links
├─ core/
│   ├─ model/                Card, Match, Player, GameEvent (Kotlin puro)
│   ├─ designsystem/         tema TERNA (colores, tipografía), CardView, botones, fondos
│   ├─ data/                 repositorios + fuentes Firebase (RTDB, Firestore, Functions)
│   ├─ domain/               casos de uso (QuickPlay, RevealCard, ObserveMatch…)
│   ├─ audio/                SoundPool + música
│   └─ common/               reloj del servidor, dispatchers, Result
└─ feature/
    ├─ splash/   ├─ home/      ├─ matchmaking/  ├─ room/ (crear/unirse/lobby)
    ├─ game/     ├─ results/   ├─ profile/      └─ tutorial/
```

Regla de dependencias: `feature → domain → model`; solo `data` conoce Firebase. Así se puede cambiar el backend en el futuro sin tocar la UI.

### 7.2 MVI en el tablero

```kotlin
sealed interface GameIntent {
    data class RevealFromHand(val ownerUid: String, val end: HandEnd) : GameIntent
    data class RevealCenter(val slot: Int) : GameIntent
    data class SendEmote(val id: String) : GameIntent
    data object Leave : GameIntent
}

data class GameUiState(
    val myHand: List<Card>,
    val opponents: List<OpponentUi>,
    val center: List<CenterSlotUi>,
    val revealed: List<RevealedCardUi>,
    val turn: TurnUi,                 // de quién, segundos restantes
    val canAct: Boolean,
    val pendingTarget: TargetRef?,    // feedback optimista
    val result: GameResultUi?,
)
```

`GameViewModel` combina: `public` (RTDB) + `mi mano` (RTDB) + `presencia` + `reloj del servidor`, y pasa los eventos por la `AnimationQueue` (§4.2).

**Reloj:** usa `.info/serverTimeOffset` de RTDB para que el temporizador de turno sea el mismo en todos los teléfonos.

### 7.3 Ciclo de vida y reconexión

- Al volver de segundo plano: resuscribirse y reconstruir desde `public` (sin animar lo que pasó mientras tanto).
- `keepSynced(true)` solo en el nodo de la partida activa.
- No hace falta un servicio en primer plano: si la app se cierra, actúan la presencia y el bot.

---

## 8. Assets: problema detectado en `diseño/`

Los `.svg` de la carpeta **no son vectores**: cada uno envuelve una imagen PNG/JPEG en base64 (entre 0,6 y 4,5 MB por archivo). Android no puede convertirlos en `VectorDrawable` útiles y, tal cual, inflarían el APK y la memoria.

Recomendación:

| Asset | Exportar como | Tamaño orientativo |
|---|---|---|
| Cartas 1–12 + reverso | **WebP** ~512×768 en `drawable-nodpi` (o 2 densidades) | 40–80 KB c/u |
| Fondos (Home, General, Crear/Unirse, Ganar7) | WebP 1920×1080, calidad 80 | 150–300 KB c/u |
| Ícono de la app | **Adaptive icon** (capa frontal + fondo) desde `IconoTerna` | — |
| Logo TERNA games | PNG/WebP para el splash (`SplashScreen API`) | — |

Ya existen exportaciones PNG en `diseño/diseño cartas y splash/`: se convierten a WebP en Android Studio.

**Estilo de carta:** todas las pantallas de la app usan el set completo 1–12 (marco negro con número grande), que queda como el estilo oficial.

### 8.1 Design system (extraído de las pantallas)

| Token | Valor | Uso |
|---|---|---|
| `primary` | `#FDE047` | Botones principales, bordes de paneles y modales, acentos |
| `onPrimary` | casi negro `#1A1410` | Texto de botones amarillos |
| `surface` | `#19110F` – `#2B2026` (marrón oscuro, semitransparente) | Paneles y modales sobre los fondos ilustrados |
| `surfaceVariant` | `#2A2220` | Filas de lista, puestos de la sala |
| `danger` | `#FF383C` | Expulsar jugador |
| `facebook` | azul Facebook | Botón de registro |
| Tipografía títulos | Sans condensada en negrita (tipo *Roboto Condensed*) | "SALA DE MIGUEL", "¡Iniciando Partida!" |
| Tipografía texto | Sans geométrica (tipo *Inter*) | Cuerpo, botones |
| Logo | "TERNA!" 3D amarillo | Imagen, no tipografía |
| Forma | Esquinas ~12–16 dp, borde amarillo de 1,5–2 dp | Paneles, botones |

Confirmar los nombres exactos de las fuentes en Figma.

Componentes base para `core/designsystem`: `TernaPanel` (panel oscuro con borde amarillo), `TernaPrimaryButton`, `TernaOutlineButton`, `TernaModal`, `PlayerSlot` (ocupado / vacío / anfitrión / con expulsar), `Stepper`, `TernaSwitch`, `CodeField`, `CardView` (anverso / reverso / volteando), `OpponentBadge`, `LoadingCards` (las cartas girando de los modales).

### 8.2 Tamaños de frame

Los frames tienen proporciones distintas: 932×430 (2,17:1, teléfono), 960×540 (16:9), 1440×900 y 1600×1000 (16:10, tablet). Para Compose conviene:

- Diseñar sobre un frame base de teléfono en **dp**, por ejemplo **800×360 dp** (el teléfono Android más común en horizontal), y revisar cada pantalla también en 915×412 dp y en tablet 1280×800 dp.
- Respetar la zona del *notch* o cámara a un lado y la barra de gestos: `WindowInsets.safeDrawing`.
- Fondos con `ContentScale.Crop` y paneles con anchos máximos, no posiciones absolutas.

Considera **Play Asset Delivery** si en el futuro añades muchas mesas o cartas cosméticas.

---

## 9. Entornos, calidad y despliegue

- **Dos proyectos Firebase:** `terna-dev` y `terna-prod` (cada uno con su `google-services.json` por *build flavor*).
- **Emulator Suite** en local: la app en modo debug apunta a los emuladores.
- **Tests:**
  - Motor TS: tests unitarios de cada regla (reparto, más baja/alta repetida, fallo, trío, victoria clásica/picante/7).
  - Reglas de seguridad: `@firebase/rules-unit-testing` ("un jugador NO puede leer la mano de otro").
  - Android: ViewModel con Turbine; Compose UI test del tablero.
  - Prueba de carga: script que simula N partidas con bots contra el emulador.
- **CI (GitHub Actions):** lint + tests en cada PR; despliegue de functions y reglas a `dev` al hacer *merge*; a `prod` con *tag*.
- **Distribución:** App Distribution → Play Console (pista interna → cerrada → producción).

---

## 10. Hoja de ruta sugerida

| Fase | Entregable | Duración orientativa |
|---|---|---|
| 0 | Repos, proyectos Firebase dev/prod, emuladores, CI, assets exportados | 1 semana |
| 1 | **Motor de reglas TS + tests + bots** (jugable por consola) | 2 semanas |
| 2 | Callables, reglas RTDB, sala privada de extremo a extremo con 3 teléfonos | 2 semanas |
| 3 | App: Splash, Login (invitado + Google), Home, Crear/Unirse, Sala de espera, **Tablero con animaciones** | 3–4 semanas |
| 4 | Salas públicas ("Salas disponibles"), partida rápida, relleno con bots, presencia y reconexión, timeouts | 2 semanas |
| 5 | Pulido: sonido, hápticos, tutorial, login con Facebook (requiere app y revisión en Meta), Crashlytics, beta cerrada | 2 semanas |
| 6+ | Perfil/XP, ranking, modo equipos, modo CUARTA, cosméticos, Play Billing | — |

---

## 11. Decisiones abiertas

1. ~~Orientación~~ → **horizontal en toda la app** (así está diseñada).
2. ~~Estilo de carta~~ → set 1–12 de marco negro (ver §8).
3. **¿2 jugadores?** El original es de 3 a 6. Se puede permitir 1 humano + bots para práctica.
4. ~~Región de Firebase~~ → `us-central1` para todo (lo impone Realtime Database).
5. **Monetización** (anuncios recompensados o cosméticos), que condiciona la fase 6.
6. **Partida rápida:** ¿siempre de 4 jugadores (el modal dice "1/4") o el jugador elige? ¿Qué modo de juego usa?
7. **¿Botón "Listo" por jugador?** La sala de espera dice "Esperando que todos estén listos…" pero no hay forma de marcarse listo.
8. **"Ingresar código" (modal) vs. campo de código** en "Unirse a sala": hay dos entradas para lo mismo. ¿Cuándo aparece el modal?
9. ~~Reglas del turno~~ → configurables (§1.1.1). Falta decidir los **valores por defecto** de cada sala y si el anfitrión puede cambiarlos desde "Crear sala".
10. **"Cerrar sesión" del invitado** borra para siempre su cuenta anónima y su progreso. En "Ajustes invitado" conviene cambiarlo por "Vincular cuenta (Google / Facebook)" y advertir antes de salir.
11. ~~Avatares~~ → set predefinido de fauna colombiana (jaguar, oso de anteojos, cóndor, tucán, guacamaya, rana dorada, chigüiro, perezoso, delfín rosado; fuentes SVG en `diseño/diseño app/avatares/svg/`) más la opción `photo` (foto de Google) para cuentas vinculadas. Sin subir fotos: no hace falta Storage ni moderación. Los demás jugadores no conocen la foto y ven el jaguar.
12. ~~"Ternas ganadas" en el perfil~~ → son **partidas ganadas**: el perfil muestra la suma de `stats.*.games` y `stats.*.wins` de todos los modos.
13. **Imagen de tragamonedas en el Jackpot 777:** Google Play pregunta por "juegos de azar simulados" al clasificar la app, y los íconos de tragamonedas pueden subir la clasificación de edad. Las cartas doradas de `ImagenGanar7` transmiten lo mismo sin ese riesgo.
