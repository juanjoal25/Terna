# TERNA! 🃏

Juego de cartas **multijugador para Android**, basado en el juego de mesa *Trio*, con identidad visual colombiana (cartas estilo papel picado, jaguar, cóndor, sombrero vueltiao) y una experiencia de app inspirada en *UNO! Mobile*: entras como invitado o con Google, creas o te unes a una sala y caes directo a la mesa.

> Proyecto de la **Universidad Pontificia Bolivariana (UPB)**.
> Créditos (pantalla de la app): desarrollo y lógica — Hugo y Espinosa · diseño y arte — Miguel.

| | |
|---|---|
| **App** | Android · Kotlin · Jetpack Compose · Hilt · Navigation Compose |
| **Backend** | Firebase: Auth, Realtime Database, Firestore, Cloud Functions 2.ª gen (Node 22 + TypeScript) |
| **Región** | `us-central1` |
| **Proyecto Firebase (dev)** | `terna-dev-718c7` (el de producción, `terna-prod`, aún no existe) |
| **Paquete** | `com.ternagames.terna` (dev: `com.ternagames.terna.dev`) |

---

## Contenido

1. [Estado del proyecto](#1-estado-del-proyecto)
2. [Cómo se juega](#2-cómo-se-juega)
3. [Arquitectura](#3-arquitectura)
4. [Estructura del repositorio](#4-estructura-del-repositorio)
5. [Backend](#5-backend)
6. [App Android](#6-app-android)
7. [Poner todo a funcionar](#7-poner-todo-a-funcionar)
8. [Pruebas](#8-pruebas)
9. [Despliegue](#9-despliegue)
10. [Problemas conocidos y lecciones aprendidas](#10-problemas-conocidos-y-lecciones-aprendidas)
11. [Hoja de ruta](#11-hoja-de-ruta)
12. [Documentación adicional](#12-documentación-adicional)

---

## 1. Estado del proyecto

### ✅ Funciona hoy (probado en teléfono real contra Firebase dev)

- **Inicio de sesión** como invitado (anónimo) o con Google. *(Facebook: pantalla lista, sin configurar.)*
- **Salas privadas** con código `TRN-XXXX` y **salas públicas** listadas en tiempo real.
- **Sala de espera:** ver quién entró, expulsar jugadores (anfitrión), iniciar la partida, salir.
- **Partida completa en la mesa**, de 3 a 6 jugadores, en redes distintas (todo pasa por la nube):
  revelar menor/mayor de cualquiera (incluido uno mismo), revelar el centro, **"Pedir carta"** (deducir) con la penalización de *"-1000 de aura"*, ternas, victoria por 3 ternas o por la terna de 7 (*Jackpot 777*), temporizador por turno y fin de partida.
- **Información controlada:** la carta equivocada no se muestra, "Pedir carta" solo una vez por turno, se puede pasar turno y la terna se completa sola con tus propias cartas (ver [§2.4](#24-que-no-se-regale-información)).
- **Historial y estadísticas** guardados en Firestore al terminar cada partida (idempotente: no cuenta dos veces).
- **Reglas configurables** por sala (casas de reglas) y motor parametrizado para el futuro modo **CUARTA**.

### 🚧 Pendiente

| Tema | Detalle |
|---|---|
| **Bots** | No existen todavía. Hoy hacen falta jugadores humanos (mínimo 3). |
| **Partida rápida** | Depende de los bots (rellenar mesas incompletas). |
| **Presencia / desconexión** | Si alguien se cae, se le pasa el turno por tiempo, pero aún no lo reemplaza un bot. |
| **Modo CUARTA** | Conjuntos de 4 con 48 cartas (el motor ya está parametrizado por `RuleSet`). |
| **Producción** | Crear `terna-prod`, App Check, Remote Config, Hosting con `assetlinks.json`, `deleteAccount`. |
| **Perfil** | Pantalla de perfil, estadísticas y foto: la desarrolla otro compañero del equipo. |
| **Turnos superpuestos** | Reportado tras el temporizador agotado; sin reproducir. Ver [§10](#10-problemas-conocidos-y-lecciones-aprendidas). |

---

## 2. Cómo se juega

### 2.1 Reglas base (fieles a Trio)

- **Mazo:** 36 cartas (números 1–12, tres copias de cada uno). **Jugadores:** 3 a 6.
- **Reparto** (a más jugadores, menos cartas al centro):

  | Jugadores | Cartas en mano | Cartas al centro (boca abajo) |
  |:-:|:-:|:-:|
  | 3 | 9 | 9 |
  | 4 | 7 | 8 |
  | 5 | 6 | 6 |
  | 6 | 5 | 6 |

- Cada mano se ordena sola de menor a mayor.
- **En tu turno** revelas cartas una a una: la **menor** o la **mayor** de la mano de cualquier jugador (incluida la tuya) o **una carta del centro**.
- Si coincide con las anteriores del turno, sigues. Si no, **todas vuelven a su sitio** y pasa el turno (la carta equivocada no se muestra: ver [§2.4](#24-que-no-se-regale-información)).
- **Tres cartas iguales = una terna.** Se queda boca arriba frente a ti.
- Pedir "la menor" dos veces a la misma mano revela la *siguiente* menor oculta.

### 2.2 Regla propia de TERNA!: deducir ("Pedir carta")

En vez de revelar a ciegas, afirmas: *"Hugo, tienes un 5"*.

- Si el 5 es **ahora mismo** la carta menor **o** la mayor de Hugo (entre sus cartas aún ocultas), **la entrega** y cuenta como revelada.
- Si no, Hugo responde con **"-1000 de aura"** y **pierdes el turno**.
- Si ya hay cartas reveladas en el turno, solo puedes pedir ese mismo número.

Ventaja sobre revelar: comprueba los dos extremos a la vez y, si fallas, no se muestra ninguna carta.

### 2.3 Cómo se gana

| Modo | Gana quien… |
|---|---|
| **Clásico** | consigue **3 ternas**, o la **terna de 7** (victoria instantánea, *Jackpot 777*). |
| **Picante** | consigue **2 ternas enlazadas** (cuyos valores suman o restan 7: 1–6, 1–8, 2–5, 2–9, 3–4…), o la terna de 7. |

Si se acaban las cartas sin ganador, gana quien tenga más ternas.

### 2.4 Que no se regale información

Para que nadie se entere de demasiado en cada turno (todas son opciones de la sala, activadas por defecto):

- **La carta equivocada no se muestra.** Si destapas algo que no es el número que buscas (la menor o la mayor de alguien, o una del centro), no se ve su valor: la mesa solo avisa *"No es la carta correcta: la carta menor de Ana no era el 5"* y pierdes el turno. La primera carta del turno sí se ve siempre, porque es el número que persigues.
- **"Pedir carta" (deducir) solo una vez por turno**; en cambio **"Preguntar" (revelar la menor o la mayor) no tiene límite**, también con la misma persona.
- **Una carta por jugada.** Si Beto tiene dos 5 seguidos, le preguntas dos veces y te da una por pregunta; no se piden dos iguales de golpe.
- **Una carta en el medio de una mano no se puede sacar**, ni pidiéndola ni revelando: solo cuentan la menor y la mayor. Pedir una que está en el medio es fallar (*-1000 de aura*); cuando salen las de afuera, la del medio pasa a ser extremo.
- **Terna automática con tu mano.** Si tras destapar una carta (pidiéndola, revelándola o sacándola del centro) tus propias cartas iguales, **seguidas en un extremo de tu mano**, completan la terna, el servidor las destapa solo.
  Ejemplo: tienes `5 5 9`, aciertas el 5 de Beto → terna de 5 al instante.
  *(No se aplica si la sala desactiva "revelar la propia mano".)*
- **Pasar turno.** El botón siempre está en el panel; se activa tras destapar al menos una carta (y la pantalla dice por qué está apagado). Devuelve las cartas a su sitio y juega el siguiente. Si ya no queda ninguna jugada, siempre se puede pasar para que el turno nunca se bloquee.
- La pantalla muestra el progreso: **"Buscando el 5 · 1 de 3 en la mesa"**.

### 2.5 Reglas configurables

Se define por sala con `TurnOptions` (el motor las valida en el servidor):

| Opción | TERNA (por defecto) | Trio original | Qué hace |
|---|:-:|:-:|---|
| `allowSelfReveal` | sí | sí | Revelar tu propia carta menor/mayor. Si se apaga, solo cuando no queda otra carta oculta. |
| `allowClaim` | **sí** | no | "Pedir carta" (deducir). |
| `claimRequiresEnd` | no | — | Obliga a decir "tu menor" o "tu mayor". |
| `claimFailEndsTurn` | sí | — | Deducir mal pierde el turno. |
| `allowPass` / `minRevealsBeforePass` | **sí** / 1 | no | "Pasar turno" tras destapar al menos una carta. Sin jugadas posibles, siempre se puede pasar. |
| `continueAfterSet` | no | no | Seguir jugando tras una terna. |
| `claimOncePerTurn` | **sí** | — | "Pedir carta" solo se puede usar una vez por turno (preguntar no tiene límite). |
| `hideMismatch` | **sí** | no | La carta que no coincide no se muestra: solo se avisa. |
| `allowMultiClaim` | no | — | Permite pedir varias cartas iguales de golpe (`count` > 1). |

Modos predefinidos: `TERNA_CLASSIC`, `TERNA_SPICY`, `TRIO_ORIGINAL`.

---

## 3. Arquitectura

### 3.1 Principio rector: **servidor autoritativo**

```
        ┌─────────────────────── App Android ───────────────────────┐
        │  Compose UI  →  ViewModel  →  Repository                  │
        └───────┬───────────────────────────────▲───────────────────┘
   intenciones  │ (callables)                   │ lecturas en tiempo real
   "revelar X"  ▼                               │ (listeners RTDB)
        ┌───────────────────┐        ┌──────────┴───────────┐
        │  Cloud Functions  │ ─────▶ │  Realtime Database   │  salas, partidas, manos
        │  (motor de reglas │        │  (estado en vivo)    │
        │   puro + servicios│        └──────────────────────┘
        │   de salas/partida)│       ┌──────────────────────┐
        │                   │ ─────▶ │  Firestore           │  perfiles, stats, historial
        └───────────────────┘        └──────────────────────┘
```

- El cliente **solo manda intenciones** ("revela la menor de Ana"). Nunca calcula el resultado ni ve cartas ajenas.
- Las **Cloud Functions** validan con un **motor de reglas puro** (sin Firebase, determinista y testeable al 100 %) y escriben el estado.
- Las **reglas de seguridad** impiden que un cliente escriba partidas o lea manos y centro de otros.
- La app **escucha** el estado y dibuja; las animaciones salen de la lista de eventos (`lastEvents`).

### 3.2 Decisiones que merece la pena conocer

| Decisión | Por qué |
|---|---|
| **Todo `matches/{id}` se actualiza en UNA transacción** | Evita estados a medias (turno nuevo con manos viejas). |
| **`expectedSeq` en cada jugada** | Rechaza jugadas basadas en un estado viejo (`STALE_STATE`) y evita dobles jugadas. |
| **Barajado con `crypto.randomInt`** (`secureRng`) | Una semilla de 32 bits sería deducible desde tu propia mano. La semilla fija solo existe en los tests. |
| **El estado del motor se guarda como JSON en `matches/{id}/secret/state`** | Realtime Database descarta arrays vacíos/`null`; un string no se corrompe. |
| **Pausa `startsAt` entre turnos** | Todos alcanzan a ver las cartas del turno anterior antes de que juegue el siguiente (`TOO_EARLY`). |
| **Tiempo agotado dirigido por clientes** (`timeoutTurn`) | Cualquier jugador avisa; el servidor comprueba con **su** reloj. Evita Cloud Tasks y funciona con que haya un cliente vivo. |
| **Realtime Database para lo vivo, Firestore para lo permanente** | RTDB es barata y rápida para estados que cambian a cada jugada; Firestore da consultas para perfiles e historial. |
| **Motor parametrizado por `RuleSet`** | `setSize`, `copies`, `maxNumber`, reparto y victoria son datos: **CUARTA** será un `RuleSet` nuevo, no un motor nuevo. |

### 3.3 Flujo de una jugada

1. La app llama a `gameAction({ matchId, expectedSeq, action })`.
2. La función abre **una transacción** sobre `matches/{id}`, comprueba que quien llama juega en esa partida y que `seq` coincide.
3. El jugador sale **de la sesión**, nunca de lo que mande el cliente.
4. `applyAction(estado, acción)` → nuevo estado + eventos.
5. Se publican `live` (mesa pública), `hands/{uid}` (cada mano, solo legible por su dueño) y `secret/state`.
6. Si la partida terminó: se libera la sala y se archiva el resultado en Firestore.

---

## 4. Estructura del repositorio

```
terna/
├─ README.md                      ← este archivo
├─ docs/
│  ├─ ARQUITECTURA.md             ← juego + arquitectura técnica completa
│  ├─ ESQUEMA_BASE_DATOS_FIREBASE.md  ← esquema RTDB/Firestore, reglas y API de las funciones
│  └─ FIREBASE_SETUP.md           ← guía paso a paso para configurar Firebase
├─ diseño/diseño app/             ← PNG exportados de Figma
├─ backend/
│  ├─ firebase.json · .firebaserc
│  ├─ database.rules.json         ← reglas de Realtime Database
│  ├─ firestore.rules · firestore.indexes.json
│  ├─ hosting/public/             ← enlaces de sala (/sala/TRN-XXXX) y página de respaldo
│  ├─ functions/src/
│  │   ├─ engine/                 ← MOTOR DE REGLAS PURO (sin Firebase)
│  │   ├─ rooms/                  ← crear/unirse/salir/expulsar, códigos
│  │   ├─ match/                  ← iniciar, jugar, tiempo agotado, archivo
│  │   ├─ lib/                    ← transacciones y errores
│  │   ├─ integration/            ← pruebas contra los emuladores
│  │   ├─ db.ts · index.ts        ← dependencias y callables
│  └─ rules-tests/                ← pruebas de reglas de seguridad + lanzador de integración
└─ android/
   ├─ app/                        ← Application, Activity, navegación, flavors dev/prod
   ├─ core/
   │   ├─ model/                  ← modelos puros (Room, Match, Session…)
   │   ├─ data/                   ← repositorios Firebase, Hilt
   │   └─ designsystem/           ← tema, botones, cartas y assets (edición Colombia)
   └─ feature/
       ├─ auth/                   ← Splash y Login
       ├─ home/                   ← Home, Perfil, Ajustes, Créditos
       ├─ room/                   ← Crear, Unirse y Sala de espera
       └─ game/                   ← La mesa
```

---

## 5. Backend

### 5.1 Motor de reglas (`backend/functions/src/engine/`)

Función pura: `applyAction(state, action) → { ok, state, events } | { ok:false, error }`. No muta el estado y no conoce red ni relojes.

| Archivo | Responsabilidad |
|---|---|
| `types.ts` | Estado, acciones, eventos, códigos de error |
| `rulesets.ts` | `TERNA_CLASSIC`, `TERNA_SPICY`, `TRIO_ORIGINAL`, `withOptions`, `validateRuleSet` |
| `setup.ts` | Mazo, barajado y reparto según nº de jugadores |
| `reducer.ts` | Revelar, deducir, pasar, tiempo agotado, completar ternas, terna automática, una pregunta por rival, carta equivocada oculta |
| `queries.ts` | Qué se puede hacer (`availableActions`), extremos ocultos, planes de cartas seguidas |
| `victory.ts` | Clásico, Picante, terna de 7, mazo agotado |
| `views.ts` | Vista pública (nunca incluye valores del centro ni manos ajenas) |
| `rng.ts` | `secureRng` (producción) y `seededRng` (tests) |

### 5.2 API: Cloud Functions callable

Todas exigen sesión (`request.auth`) y traducen errores a `details.reason`, que la app convierte a mensajes en español.

| Función | Entrada | Salida | Qué hace |
|---|---|---|---|
| `createRoom` | `{ maxPlayers, isPrivate, turnSeconds?, ruleSet?, options? }` | `{ roomId, code }` | Crea la sala y reserva el código `TRN-XXXX` |
| `joinRoom` | `{ code }` ó `{ roomId }` | `{ roomId }` | Une por código o por id (solo salas públicas) |
| `leaveRoom` | — | — | Sale; si era anfitrión, pasa al más antiguo |
| `kickPlayer` | `{ targetUid }` | — | El anfitrión expulsa (queda vetado) |
| `startMatch` | — | `{ matchId }` | Baraja, reparte y publica la partida |
| `gameAction` | `{ matchId, expectedSeq, action }` | `{ seq, finished }` | Una jugada: `REVEAL_HAND`, `REVEAL_CENTER`, `CLAIM {…, count?}`, `PASS` |
| `timeoutTurn` | `{ matchId, expectedSeq }` | `{ seq, finished }` | Pasa el turno si el servidor confirma que venció |
| `ping` | — | `{ status }` | Comprobación de conexión |

Detalle de errores y ejemplos: [`docs/ESQUEMA_BASE_DATOS_FIREBASE.md`](docs/ESQUEMA_BASE_DATOS_FIREBASE.md) §4b.

### 5.3 Datos

**Realtime Database** (vivo):

```
rooms/{roomId}              config, miembros, anfitrión, estado
openRooms/{roomId}          índice de salas públicas con puestos
roomCodes/{TRN-XXXX}        reserva de códigos
active/{uid}                sala en la que está cada usuario (reanudar al abrir la app)
matches/{matchId}/
    meta                    reglas publicadas, estado, tiempos
    players                 asiento, nombre, avatar, nº de cartas, ternas
    live                    seq, turno, cartas reveladas, centro (booleanos), últimos eventos
    hands/{uid}             tu mano (solo legible por ti)
    secret/state            estado completo del motor (solo servidor)
```

**Firestore** (permanente): `users/{uid}` (perfil + estadísticas por modo) y `matchArchive/{matchId}` (historial; índice `playerUids + endedAt`).

### 5.4 Seguridad

- Reglas de RTDB con **lectura por rama**: las reglas se heredan hacia abajo, por eso `matches/$matchId` no tiene `.read`; se concede en `meta`, `players`, `live` y en `hands/$uid` (solo el dueño).
- Ningún cliente escribe en `rooms`, `matches` ni `active`: solo las funciones (Admin SDK).
- Firestore: validación de campos con `hasOnly`, sin borrados desde el cliente, historial de solo lectura.
- 25 pruebas de reglas ([`backend/rules-tests`](backend/rules-tests)).

### 5.5 Estadísticas y fin de partida

Al terminar, `finishMatch` libera la sala, vuelve a `WAITING` y guarda el resultado. Las estadísticas por usuario y modo (partidas, victorias, ternas, victorias por 7, racha actual y mejor racha) se escriben en una transacción **idempotente**: reprocesar la misma partida no suma dos veces.

---

## 6. App Android

### 6.1 Módulos

| Módulo | Contenido |
|---|---|
| `:app` | `MainActivity`, `TernaNavHost`, Hilt (`AppModule`), flavors `dev`/`prod` |
| `:core:model` | Modelos puros de Kotlin (`Room`, `MatchState`, `GameAction`, `Session`…) |
| `:core:data` | Repositorios sobre Firebase (`RoomRepository`, `MatchRepository`, auth, usuario), `ServerException` + mensajes en español |
| `:core:designsystem` | Tema, botones, campos, diálogos, fondos, carta (`PlayingCard`), assets de la edición Colombia |
| `:feature:auth` | Splash y Login (invitado / Google) |
| `:feature:home` | Home, Perfil, Ajustes, Créditos |
| `:feature:room` | Crear sala, Unirse, Sala de espera |
| `:feature:game` | Mesa: mano en abanico, centro, panel de acciones, diálogos y resultado |

Patrón: **MVI ligero** — `ViewModel` con `StateFlow<UiState>`, repositorios con `callbackFlow` sobre los listeners de RTDB, navegación tipada con Navigation Compose.

### 6.2 Flujo de pantallas

```
Splash ─▶ Login ─▶ Home ─▶ Crear sala ─┐
   │                  └──▶ Unirse ──────┼─▶ Sala de espera ─▶ Mesa ─▶ (volver a la sala / lobby)
   └─ si ya estabas en una sala ────────┘
              Home ─▶ Perfil · Ajustes · Créditos
```

### 6.3 La mesa (`:feature:game`)

- **`GameLogic`**: decide qué botones mostrar y qué opciones ofrecer (el servidor valida todo de nuevo). Tiene pruebas unitarias.
- **`GameViewModel`**: estado de la mesa, banners narrados desde el punto de vista de quien mira, diálogos paso a paso, aviso de tiempo agotado escalonado por asiento y manejo de "ocupado".
- **Acciones del jugador:** *Revelar centro*, *Preguntar* (menor/mayor de alguien), *Pedir carta* (número → a quién), *¡Hacer terna!* y *Pasar turno*. También se puede **tocar a un rival** en la mesa para preguntarle o pedirle una carta directamente.
- **Feedback:** las cartas vuelan desde donde salen (la mano del rival, el centro o la tuya) hasta la mesa y se voltean en el aire; al empezar se reparten; un foco amarillo viaja entre jugadores al cambiar el turno; al completar una terna las cartas vuelan al montón de quien la hizo con confeti; "-1000 AURA" sube sobre quien la pierde y la pantalla destella en rojo si fallas tú; la bandeja se sacude al fallar; la barra de tiempo se pone roja al final; aparece "¡TU TURNO!"; tus cartas se agrandan al tocarlas; y el teléfono vibra en los momentos clave.
- **Reloj:** usa la hora estimada del servidor (`.info/serverTimeOffset`); después de que vence el turno ya no se aceptan toques.

---

## 7. Poner todo a funcionar

### 7.1 Requisitos

- **Android Studio** (AGP 9.4.1, Kotlin 2.4.20, `compileSdk`/`targetSdk` 37, `minSdk` 26) y JDK 17+.
- **Node 22** y npm.
- Acceso al proyecto Firebase `terna-dev-718c7` (pídelo al equipo) y el archivo `android/app/src/dev/google-services.json`.
- Firebase CLI: ya viene en `backend/rules-tests` (`npx --prefix backend/rules-tests firebase …`), así que no hace falta instalarlo aparte.

### 7.2 Primera vez

```bash
# 1. Iniciar sesión en Firebase (una vez por equipo)
npx --prefix backend/rules-tests firebase login

# 2. Dependencias del backend
npm --prefix backend/functions install
npm --prefix backend/rules-tests install
```

Los pasos de la consola de Firebase (Auth, bases de datos, huellas SHA, plan Blaze…) están en [`docs/FIREBASE_SETUP.md`](docs/FIREBASE_SETUP.md).

### 7.3 Correr la app

**Contra el Firebase real (dev)** — lo más simple, y permite jugar entre teléfonos en redes distintas:

```bash
cd android
./gradlew :app:assembleDevDebug
# APK: android/app/build/outputs/apk/dev/debug/app-dev-debug.apk
```

También se puede abrir `android/` en Android Studio y ejecutar la variante `devDebug`.

**Contra emuladores locales** (sin gastar cuota, ideal para probar reglas):

```bash
# Terminal 1 — emuladores de Auth, RTDB, Firestore y Functions
cd backend
firebase emulators:start --project dev

# Terminal 2 — app apuntando a 10.0.2.2
cd android
./gradlew :app:assembleDevDebug -PuseEmulators=true
```

> En Windows, si las funciones tardan en cargar, define antes `FUNCTIONS_DISCOVERY_TIMEOUT=180`.
> El tráfico HTTP sin cifrar hacia el emulador solo está permitido en el build **debug**.

### 7.4 Jugar entre varios dispositivos

Hacen falta **3 jugadores como mínimo**. Puedes combinar teléfonos, emuladores de Android Studio y sesiones de invitado. Uno crea la sala, los demás entran con el código `TRN-XXXX` (o desde la lista de salas públicas) y el anfitrión pulsa **Iniciar**.

---

## 8. Pruebas

| Qué | Comando | Cantidad |
|---|---|:-:|
| Motor y utilidades (rápidas, sin Firebase) | `cd backend/functions && npx vitest run` | 64 |
| Reglas de seguridad (emuladores RTDB + Firestore) | `cd backend/rules-tests && npm test` | 25 |
| Salas y partidas contra emuladores (Auth/RTDB/Firestore) | `cd backend/rules-tests && npm run test:functions` | 42 |
| Lógica de la mesa en Android | `cd android && ./gradlew :feature:game:testDebugUnitTest` | 11 |

El motor incluye una **simulación aleatoria** que juega partidas enteras y comprueba que **ninguna carta aparece ni desaparece**, además de pruebas del generador aleatorio real (`secureRng`).

> Lección: una prueba con el generador de semilla fija ocultó un fallo del generador real en producción. Por eso `rng.test.ts` prueba `secureRng`.

---

## 9. Despliegue

Siempre desde `backend/`:

```bash
# Todo (reglas, índices y funciones) al proyecto dev
firebase deploy --project dev

# Solo funciones (build automático con tsc)
firebase deploy --only functions --project terna-dev-718c7 --force

# Solo reglas
firebase deploy --only database,firestore:rules --project dev
```

La producción se desplegará desde CI al crear un *tag* (aún sin configurar).

### Lista de comprobación tras el **primer** despliegue de un entorno

Estos tres puntos no los detectan los emuladores y, si fallan, la app dice "sesión expirada" o "sin conexión" aunque todo esté bien:

1. **Funciones públicas.** Cada servicio de Cloud Run debe tener `allUsers` → *Invocador de Cloud Run*. Verifica:
   ```bash
   curl -s -X POST -H "Content-Type: application/json" -d '{"data":{}}' \
     https://us-central1-terna-dev-718c7.cloudfunctions.net/ping
   ```
   ✅ Correcto: JSON `{"error":{"message":"Inicia sesión para usar TERNA!","status":"UNAUTHENTICATED"}}`
   ❌ Incorrecto: página HTML "403 Forbidden".
2. **Permisos de ejecución.** La cuenta `NÚMERO-compute@developer.gserviceaccount.com` necesita *Administrador de Firebase Realtime Database* y *Usuario de Cloud Datastore*.
3. **Permiso de compilación.** Esa misma cuenta necesita *Cuenta de servicio de Cloud Build* en proyectos nuevos.

Detalles y capturas de cada paso en [`docs/FIREBASE_SETUP.md`](docs/FIREBASE_SETUP.md).

---

## 10. Problemas conocidos y lecciones aprendidas

| Síntoma | Causa | Solución |
|---|---|---|
| "Tu sesión expiró" al crear sala (con sesión válida) | Funciones desplegadas **privadas** en Cloud Run; Google rechaza el token de Firebase | Dar `allUsers` → *Invocador de Cloud Run* a las 8 funciones |
| "Sin conexión" al crear sala, con internet | La cuenta de las funciones no tenía permisos sobre RTDB; la conexión se cuelga hasta el límite de tiempo | Roles *Realtime Database Admin* y *Datastore User* |
| El despliegue falla por permiso de compilación | Falta *Cloud Build Service Account* en proyectos nuevos | Otorgar el rol a la cuenta de cómputo |
| Funciones "Timeout after 10000" al desplegar en Windows | Análisis de código lento | `FUNCTIONS_DISCOVERY_TIMEOUT=180` |
| `startMatch` fallaba al barajar | `crypto.randomInt` admite rangos < 2⁴⁸ y se pedía 2⁴⁸ exactos | Se piden 2⁴⁷ (suficiente) y hay prueba del generador real |
| Error falso "faltan jugadores" / jugada rechazada | Una transacción abortaba leyendo la caché del Admin SDK, posiblemente desactualizada | `transact` confirma todo rechazo con una lectura fresca y reintenta |
| Emulador `Medium_Phone` (API 37) no arranca | Imagen del sistema pesada | Usar `New_Device` (API 35) |
| Turnos que parecen superponerse tras agotarse el temporizador | **Sin reproducir.** El servidor está probado (incluso con avisos simultáneos); se sospecha de la app | Se bloquean los toques tras vencer el turno y cada tiempo agotado deja una línea en los logs (`Tiempo agotado aplicado`). Cuando vuelva a pasar, anota qué se ve y consulta `firebase functions:log --only timeoutTurn` |

### Costos

El plan **Blaze** es necesario para Cloud Functions, pero tiene cuota gratuita amplia. Para un proyecto universitario con poco tráfico el gasto esperado es mínimo; conviene un presupuesto con alertas. Un `minInstances` mayor que 0 en `gameAction` quitaría el arranque en frío (esperas ocasionales de unos segundos) a cambio de unos pocos dólares al mes. Ver [`docs/FIREBASE_SETUP.md`](docs/FIREBASE_SETUP.md) §2.1.

---

## 11. Hoja de ruta

1. **Bots** en el servidor: memoria imperfecta según dificultad (fácil/medio/difícil), juegan con retardo humano.
2. **Partida rápida** (`queue/`): emparejar y rellenar con bots tras ~20 s.
3. **Presencia**: marcar desconectados y que un bot tome su puesto; devolverlo si vuelve.
4. **Revancha** y mejoras al fin de partida.
5. **Modo CUARTA**: conjuntos de 4 con 48 cartas (nuevo `RuleSet`).
6. **Producción**: proyecto `terna-prod`, App Check (Play Integrity), Remote Config, App Links, borrado de cuenta, CI.
7. **Edición México** de las cartas y modo "Aprendiz" con pistas.

---

## 12. Documentación adicional

| Documento | Para qué |
|---|---|
| [`docs/CONTEXTO_Y_PROXIMOS_PASOS.md`](docs/CONTEXTO_Y_PROXIMOS_PASOS.md) | Memoria del proyecto: qué se hizo, herramientas, decisiones, incidencias y plan detallado de próximos pasos |
| [`docs/ARQUITECTURA.md`](docs/ARQUITECTURA.md) | Reglas completas, stack, modelo de datos, decisiones abiertas |
| [`docs/ESQUEMA_BASE_DATOS_FIREBASE.md`](docs/ESQUEMA_BASE_DATOS_FIREBASE.md) | Esquema exacto de RTDB/Firestore, reglas de seguridad, API de funciones y notas para los DTO de Kotlin |
| [`docs/FIREBASE_SETUP.md`](docs/FIREBASE_SETUP.md) | Guía paso a paso de configuración de Firebase y tabla de errores típicos |

**Diseño:** archivo de Figma "Terna" (páginas *Wireframes* y *Diseño de la app*); las exportaciones están en `diseño/diseño app/`.

---

<sub>Basado en *Trio* (Kaya Miyano, Cocktail Games). TERNA! es un proyecto académico sin fines comerciales.</sub>
