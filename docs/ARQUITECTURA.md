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
- Al revelar **tres cartas iguales** se forma un **trío**: se coloca boca arriba frente al jugador y el turno termina *(parámetro `continuarTrasTrio` en el motor, por si se quiere variar)*.
- Pedir "la más baja" dos veces a la misma mano revela la **siguiente** más baja aún oculta.

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

### 1.4 Flujo de la app

```
Splash ─► Login invitado automático ─► HOME
                                        ├─ JUGAR (partida rápida pública)
                                        │    └─ elegir modo (Clásico / Picante) y nº de jugadores
                                        │         └─ Pantalla "Buscando jugadores…" (20 s máx.)
                                        │              └─ se llenan los puestos vacíos con bots ─► TABLERO
                                        ├─ CREAR SALA (privada)
                                        │    └─ código de 6 caracteres + enlace para compartir
                                        │         └─ Lobby: puestos, config del anfitrión, "Iniciar" ─► TABLERO
                                        ├─ UNIRSE CON CÓDIGO ─► Lobby ─► TABLERO
                                        ├─ Cómo jugar (tutorial interactivo)
                                        └─ Perfil / Ajustes
TABLERO ─► Fin de partida (ImagenGanar7 si fue con los 7) ─► Revancha │ Volver al Home
```

### 1.5 Pantalla de tablero

- **Abajo:** tu mano, ordenada y abanicada (usa la orientación horizontal; todo el arte está en 16:9).
- **Alrededor:** avatares de los rivales con su nº de cartas, tríos conseguidos y estado de conexión. Al tocar un rival aparecen dos botones: **"Más baja" / "Más alta"**.
- **Centro:** cuadrícula de cartas boca abajo (reverso `CartaReverso`).
- **Zona de revelado:** las cartas del turno actual, a la vista de todos.
- **Barra de turno:** de quién es el turno y un temporizador circular (por defecto 20 s).
- **Extras:** emotes rápidos (no chat libre en públicas), sonido y vibración al formar trío, confeti con la victoria.

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
| **Authentication** | Invitado = **Anonymous Auth**. Después, `linkWithCredential` con Google (Credential Manager) para conservar el mismo UID y el progreso. |
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
- **Misma región** para RTDB, Firestore y Functions. Desde Colombia, `us-east1` suele dar la menor latencia; **mídelo antes de crear el proyecto, porque la región de Firestore no se puede cambiar después**.

### 4.2 Eventos + estado (clave de la fluidez)

El servidor publica dos cosas:

- `public` = **estado actual** completo (fuente de verdad, sirve para reconectar).
- `public/lastEvents` = **eventos** con número de secuencia (`CARD_REVEALED`, `TURN_FAILED`, `TRIO_WON`, `TURN_CHANGED`, `GAME_OVER`).

El cliente **no** salta de un estado a otro: reproduce los eventos en orden en una `AnimationQueue` y solo al terminar refleja el estado. Si detecta un hueco en `seq` (por ejemplo, volvió de segundo plano), descarta la cola y se sincroniza con `public` sin animar.

**Fallo de turno:** el servidor emite `TURN_FAILED` con las cartas reveladas y fija `turn.startsAt = ahora + 1500 ms`. Todos ven las cartas 1,5 s antes de que regresen, y el siguiente jugador no puede actuar antes de esa hora. El ritmo es idéntico para todos.

---

## 5. Modelo de datos

### 5.1 Realtime Database (partidas en vivo)

```jsonc
{
  "matches": {
    "{matchId}": {
      "meta": {
        "type": "PUBLIC | PRIVATE",
        "mode": "CLASSIC | SPICY",
        "ruleSet": "TRIO_CLASSIC",  // cuántas copias, tamaño del set, reparto, victoria
        "ruleVersion": 1,
        "status": "WAITING | PLAYING | FINISHED",
        "maxPlayers": 4,
        "hostUid": "uid",
        "code": "K7M2QX",          // solo privadas
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
  "roomCodes":{ "K7M2QX": "{matchId}" },
  "queue":    { "CLASSIC_4": { "{matchId}": { "seats": 2, "openUntil": 0 } } }
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

### 6.3 Salas privadas

- Código de 6 caracteres sin ambiguos (sin `0/O/1/I`), reservado con transacción en `/roomCodes`.
- Enlace `https://terna.web.app/sala/K7M2QX` (App Link): abre la app directamente en el lobby; sin la app, lleva a Play Store.
- El anfitrión elige modo, nº de jugadores, tiempo de turno, si rellena con bots y si activa el modo Aprendiz.

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

Además, en `Tablero HD` y `Galería` hay **dos estilos de cartas** (el primer 1–7 y el set completo 1–12). Hay que elegir uno antes de empezar el design system.

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
| 3 | App: Splash, invitado, Home, Lobby, **Tablero con animaciones** | 3–4 semanas |
| 4 | Partida rápida pública, relleno con bots, presencia y reconexión, timeouts | 2 semanas |
| 5 | Pulido: sonido, hápticos, tutorial, Crashlytics, beta cerrada | 2 semanas |
| 6+ | Login Google, perfil/XP, ranking, modo equipos, cosméticos, Play Billing | — |

---

## 11. Decisiones abiertas

1. **Orientación:** el arte es 16:9 → recomiendo **horizontal fija** en el tablero. ¿Home también horizontal?
2. **Estilo de carta definitivo** (ver §8).
3. **¿2 jugadores?** El original es de 3 a 6. Se puede permitir 1 humano + bots para práctica.
4. **Región de Firebase**: medir latencia desde Colombia a `us-east1` y `southamerica-east1` antes de crear el proyecto.
5. **Monetización** (anuncios recompensados o cosméticos), que condiciona la fase 6.
