# TERNA! — Contexto del proyecto, lo hecho y próximos pasos

> **Para qué sirve este documento.** Es la memoria del proyecto: qué se construyó, con qué herramientas, por qué se decidió cada cosa, qué se rompió y cómo se arregló, y qué sigue con un plan detallado. Está pensado para que cualquier persona del equipo (o un asistente de IA) pueda retomar el trabajo sin haber estado en las conversaciones anteriores.
>
> **Última actualización:** 2026-10-28 · **Estado:** MVP jugable de 3 a 6 personas contra Firebase *dev*; sin bots todavía.
>
> Documentos hermanos: [`README.md`](../README.md) (visión general y cómo correr) · [`ARQUITECTURA.md`](ARQUITECTURA.md) (diseño completo) · [`ESQUEMA_BASE_DATOS_FIREBASE.md`](ESQUEMA_BASE_DATOS_FIREBASE.md) (datos, reglas y API) · [`FIREBASE_SETUP.md`](FIREBASE_SETUP.md) (configuración de la consola).

---

## Índice

1. [Resumen ejecutivo](#1-resumen-ejecutivo)
2. [Línea de tiempo](#2-línea-de-tiempo)
3. [Herramientas y versiones](#3-herramientas-y-versiones)
4. [Lo que se construyó — Backend](#4-lo-que-se-construyó--backend)
5. [Lo que se construyó — App Android](#5-lo-que-se-construyó--app-android)
6. [Decisiones de diseño y por qué](#6-decisiones-de-diseño-y-por-qué)
7. [Reglas del juego tal como están implementadas](#7-reglas-del-juego-tal-como-están-implementadas)
8. [Entornos, cuentas y permisos](#8-entornos-cuentas-y-permisos)
9. [Incidencias y cómo se resolvieron](#9-incidencias-y-cómo-se-resolvieron)
10. [Estado de las pruebas](#10-estado-de-las-pruebas)
11. [Trabajo en equipo y reglas de colaboración](#11-trabajo-en-equipo-y-reglas-de-colaboración)
12. [Próximos pasos (plan detallado)](#12-próximos-pasos-plan-detallado)
13. [Preguntas abiertas](#13-preguntas-abiertas)
14. [Anexos](#14-anexos)

---

## 1. Resumen ejecutivo

**Qué es.** TERNA! es una app Android multijugador basada en el juego de mesa *Trio*, con arte colombiano y un flujo tipo *UNO! Mobile*: entras (invitado o Google) → Home → creas/te unes a una sala → mesa.

**Qué hay hoy.**

- Un **backend completo** en Firebase (Cloud Functions 2.ª gen + Realtime Database + Firestore) con un **motor de reglas puro y probado** que decide todo en el servidor.
- Una **app Android** con login, Home, salas (crear, unirse, sala de espera) y la **mesa de juego** funcionando contra el backend real.
- **Partidas reales entre teléfonos en redes distintas**: ya se jugó (3 jugadores, invitado y registrado).
- Documentación de arquitectura, esquema de datos, guía de Firebase y este documento.

**Qué falta, en una frase.** Los **bots** (y con ellos la partida rápida y el reemplazo por desconexión), cerrar el bug de turnos superpuestos, y todo lo necesario para producción.

**Cifras.** 4 documentos · 8 Cloud Functions · 142 pruebas automáticas (64 motor + 25 reglas de seguridad + 42 integración + 11 lógica de mesa) · 8 módulos Gradle.

---

## 2. Línea de tiempo

| Fase | Qué pasó | Resultado |
|---|---|---|
| **0. Concepto** (24-oct) | Se definió el juego a partir de *Trio* + regla propia "deducir"; se eligió el stack (Kotlin + Firebase) y la arquitectura servidor-autoritativo. | `ARQUITECTURA.md` v0.1 |
| **1. Diseño** | Se revisó el Figma "Terna" (wireframes y diseño de la app) y los PNG exportados. Se detectaron problemas de assets (ver ARQUITECTURA §8). | Mapa de pantallas y flujos 1.x / 2.x |
| **2. Motor de reglas** | Motor puro en TypeScript con reglas configurables, victoria clásica/picante/777, reparto por nº de jugadores, vistas públicas. | `backend/functions/src/engine` + tests |
| **3. Firebase** | Guía de configuración, proyecto `terna-dev-718c7`, plan Blaze, Auth (anónimo y Google), RTDB y Firestore, reglas de seguridad con pruebas, `google-services.json`, huellas SHA. | `FIREBASE_SETUP.md`, `rules-tests` |
| **4. Esquema y API** | Se rediseñó el modelo de datos de salas y partidas y la API de funciones. | `ESQUEMA_BASE_DATOS_FIREBASE.md` |
| **5. App base** | Proyecto Android modular, tema, Splash, Login (invitado/Google), Home. Primer APK corriendo en emulador. | Módulos `:app`, `:core:*`, `:feature:auth/home` |
| **6. Pull del equipo** | Se integró el trabajo de Hugo (perfil, diseño). Hubo conflictos de fusión que se resolvieron conservando el perfil de Hugo y las salas. | Commit `47f6fa9` |
| **7. Salas y partidas (backend)** | `createRoom`, `joinRoom`, `leaveRoom`, `kickPlayer`, `startMatch`, `gameAction`, `timeoutTurn`; transacciones; historial y estadísticas en Firestore. Despliegue a `dev`. | 8 funciones en producción-dev |
| **8. Salas y mesa (Android)** | Pantallas de Crear/Unirse/Sala de espera y la Mesa completa con diálogos, banners, reloj y resultado. | `:feature:room`, `:feature:game` |
| **9. Puesta en marcha real** | Primeras pruebas en teléfono: aparecieron tres fallos de despliegue (funciones privadas, permisos de la cuenta de servicio, bug del barajado). Se corrigieron y ya se jugó. | Sección 9 |
| **10. Pulido de juego** | Terna automática con la propia mano, bloqueo de toques tras vencer el turno, endurecimiento de transacciones. | Sección 7.4 |
| **10b. Información controlada** (28-oct) | Carta equivocada oculta, una pregunta por rival, pasar turno, mesa más visible e interactiva (cartas que giran, rivales tocables, temporizador, vibración). | Sección 7.4 |
| **11. Documentación** | README y este documento. | — |

---

## 3. Herramientas y versiones

### 3.1 Android

| Pieza | Versión / detalle | Para qué |
|---|---|---|
| Android Gradle Plugin | **9.4.1** | Compilación |
| Kotlin / KSP | **2.4.20** / 2.3.12 | Lenguaje y procesadores de anotaciones |
| `compileSdk` / `targetSdk` / `minSdk` | 37 / 37 / **26** | Soporte desde Android 8 |
| Compose BoM | 2026.09.00 | UI declarativa (Material 3) |
| Navigation Compose | 2.10.2 | Navegación **tipada** (rutas `@Serializable`) |
| Hilt | 2.60.1 + `androidx.hilt` 1.4.0 | Inyección de dependencias (`hiltViewModel` viene de `androidx.hilt.lifecycle.viewmodel.compose`) |
| Coroutines / Serialization | 1.11.0 / 1.8.1 | Asincronía; `callbackFlow` sobre listeners de RTDB |
| Firebase BoM | **35.1.0** | Auth, Realtime Database, Firestore, Functions, Analytics |
| Credential Manager + GoogleId | 1.6.0 / 1.2.1 | "Iniciar sesión con Google" |
| Coil | 3.6.2 | Imágenes (foto de perfil; lo usa el equipo de perfil) |
| Pruebas | JUnit 4.13.2, MockK, Turbine | Lógica de mesa |

Variantes: *flavors* `dev` (id `com.ternagames.terna.dev`, nombre "TERNA Dev") y `prod` (desactivado hasta que exista `src/prod/google-services.json`). BuildConfig: `USE_EMULATORS` y `EMULATOR_PORT_OFFSET`.

### 3.2 Backend

| Pieza | Versión / detalle |
|---|---|
| Cloud Functions | **2.ª generación**, Node **22**, región **us-central1**, `maxInstances: 10` |
| `firebase-functions` / `firebase-admin` | ^6.3.0 / ^13.1.0 |
| TypeScript | ^7.0.2 (`tsconfig.build.json` excluye las pruebas de integración) |
| Pruebas | `vitest` ^5.0.5, `@firebase/rules-unit-testing` ^6.0.0 |
| Firebase CLI | ^15.34.1 (instalada en `backend/rules-tests`, se usa con `npx --prefix backend/rules-tests firebase …`) |
| Bases de datos | **Realtime Database** (vivo) + **Firestore** (permanente) |

### 3.3 Herramientas de apoyo usadas durante el desarrollo

- **Firebase Emulator Suite** (Auth, RTDB, Firestore, Functions) y `emulators:exec` para pruebas.
- **Android Emulator**: `New_Device` (API 35, imagen ligera). `Medium_Phone` (API 37) no arranca en este equipo.
- **adb** para instalar el APK y capturar pantalla (`adb exec-out screencap -p`).
- **Figma** (vista, no edición) para leer el diseño.
- **Consola de Google Cloud** (IAM y Cloud Run) para permisos que Firebase no configura solo.
- **Git/GitHub**: repo `juanjoal25/Terna`, rama `main`.

---

## 4. Lo que se construyó — Backend

### 4.1 Motor de reglas (`backend/functions/src/engine/`)

Es una **función pura**: `applyAction(state, action) → { ok:true, state, events } | { ok:false, error }`. No usa Firebase, red ni relojes, no muta el estado y es determinista dado el generador aleatorio inyectado.

| Archivo | Responsabilidad |
|---|---|
| `types.ts` | `GameState`, `Action` (`REVEAL_HAND`, `REVEAL_CENTER`, `CLAIM{count?}`, `PASS`, `TIMEOUT`), eventos (`CARD_REVEALED`, `CLAIM_FAILED`, `TURN_FAILED`, `TURN_PASSED`, `TURN_TIMEOUT`, `SET_COMPLETED`, `TURN_STARTED`, `GAME_OVER`) y códigos de error |
| `rulesets.ts` | `TERNA_CLASSIC`, `TERNA_SPICY`, `TRIO_ORIGINAL`, `withOptions(modo, opciones)`, `validateRuleSet` |
| `setup.ts` | Crea el mazo, baraja y reparte según nº de jugadores (manos ordenadas; centro boca abajo) |
| `reducer.ts` | Revelar, **deducir con cantidad**, pasar, tiempo agotado, completar ternas y **terna automática** |
| `queries.ts` | `availableActions`, extremos ocultos, `planHandCards` (cartas iguales seguidas), `ownCardsToComplete` |
| `victory.ts` | 3 ternas, terna de 7, ternas enlazadas (picante), mazo agotado |
| `views.ts` | `publicView`, `toPublicEvent`: lo único que se publica a la mesa (jamás valores del centro ni manos ajenas) |
| `rng.ts` | `secureRng()` (producción, `crypto.randomInt` con 47 bits) y `seededRng()` (solo pruebas) |

**Conceptos clave del motor**

- Las manos se guardan **ordenadas**; una carta revelada conserva su `index` en la mano hasta que se forma la terna (así "pedir la menor" dos veces da la siguiente menor oculta).
- `Reveal` guarda `source` (`HAND`/`CENTER`), dueño, extremo (`LOW`/`HIGH`), valor y si fue `claimed`.
- `seq` es monotónico por evento: ordena animaciones y protege contra jugadas con estado viejo.
- Todo el motor está **parametrizado por `RuleSet`** (`maxNumber`, `copies`, `setSize`, `deal`, `win`, `options`) para soportar el futuro modo CUARTA.

### 4.2 Servicios (`rooms/`, `match/`, `lib/`)

| Archivo | Qué hace |
|---|---|
| `rooms/roomService.ts` | `createRoom`, `joinRoom` (por código o por id), `leaveRoom` (traspaso de anfitrión), `kickPlayer` (con veto), `syncOpenRoom` (índice de salas públicas) |
| `rooms/codes.ts` | Códigos `TRN-XXXX` de 4 caracteres sin ambiguos (sin `0/O/1/I`), reservados con transacción en `roomCodes/` |
| `rooms/validation.ts` | Valida jugadores (3–6), privacidad, segundos por turno, modo y opciones; `playerRange` sale del reparto del `RuleSet` |
| `match/matchService.ts` | `startMatch` (reserva `STARTING`, baraja, publica todo en un solo guardado), `gameAction`, `timeoutTurn`, `mutateMatch` compartido, `finishMatch` |
| `match/archive.ts` | Historial `matchArchive/` y estadísticas por usuario/modo en Firestore (transacción **idempotente**) |
| `lib/tx.ts` | `transact()`: patrón correcto de transacciones del Admin SDK + confirmación de rechazos con lectura fresca |
| `lib/errors.ts` | `GameError(code, reason, message)` → `HttpsError` con `details.reason` |
| `db.ts` | Dependencias reales creadas de forma perezosa (para no necesitar credenciales al analizar el despliegue) |
| `index.ts` | Las 8 callables y `setGlobalOptions` |

### 4.3 Modelo de datos (resumen; detalle en `ESQUEMA_BASE_DATOS_FIREBASE.md`)

**Realtime Database**

```
rooms/{roomId}            tipo, estado, anfitrión, config {ruleSet, options, turnSeconds}, members, banned
openRooms/{roomId}        índice de salas públicas con puestos
roomCodes/{TRN-XXXX}      código → roomId
active/{uid}              {roomId} de la sala en la que está cada usuario (reanudar al abrir)
matches/{matchId}/
   meta                   roomId, ruleSet, estado, turnSeconds, setSize, allowPass, allowClaim, allowSelfReveal, minRevealsBeforePass, askOncePerTurn, hideMismatch, allowMultiClaim
   players/{uid}          seat, name, avatar, isBot, connected, handCount, sets
   live                   seq, turn{player, number, startsAt, deadline}, revealed, center[bool], lastEvents, result
   hands/{uid}            mano privada (solo la lee su dueño)
   secret/state           JSON del motor (solo servidor)
```

**Firestore**: `users/{uid}` (perfil y `stats.<modo>`) y `matchArchive/{matchId}` (índice `playerUids + endedAt`).

### 4.4 API (Cloud Functions callable)

| Función | Entrada | Salida |
|---|---|---|
| `createRoom` | `{ maxPlayers, isPrivate, turnSeconds?, ruleSet?, options? }` | `{ roomId, code }` |
| `joinRoom` | `{ code }` o `{ roomId }` | `{ roomId }` |
| `leaveRoom` | — | — |
| `kickPlayer` | `{ targetUid }` | — |
| `startMatch` | — | `{ matchId }` |
| `gameAction` | `{ matchId, expectedSeq, action }` | `{ seq, finished }` |
| `timeoutTurn` | `{ matchId, expectedSeq }` | `{ seq, finished }` |
| `ping` | — | `{ status, region, timestamp }` |

Todas exigen sesión. Errores en `details.reason` (p. ej. `ROOM_FULL`, `NOT_YOUR_TURN`, `STALE_STATE`, `TOO_EARLY`, `NOT_TIMED_OUT`, `INVALID_COUNT`). La tabla completa de motivos y su traducción está en el [Anexo A](#anexo-a--códigos-de-error-y-mensajes).

### 4.5 Seguridad

- **RTDB** (`database.rules.json`): lectura por rama (las reglas se heredan hacia abajo, por eso `matches/$matchId` **no** tiene `.read`); `hands/$uid` solo para su dueño; nadie del cliente escribe `rooms`, `matches`, `active`.
- **Firestore** (`firestore.rules`): perfiles legibles con sesión, creación/actualización con `hasOnly` y validación, **sin borrados** desde el cliente, historial de solo lectura.
- 25 pruebas de reglas con el emulador.

---

## 5. Lo que se construyó — App Android

### 5.1 Módulos

| Módulo | Contenido relevante |
|---|---|
| `:app` | `MainActivity`, `di/AppModule.kt` (`FirebaseConfig`), `navigation/TernaNavHost.kt` |
| `:core:model` | `Session`, `Room`, `Match` (`MatchState`, `MatchEvent`, `GameAction`, `RevealedCard`…) |
| `:core:data` | `FirebaseModule` (emuladores opcionales), `RoomRepository`, `MatchRepository`, repos de auth y usuario, `ServerException` + `userMessage()` en español |
| `:core:designsystem` | Tema `TernaColors`, `Buttons`, `Inputs` (`TernaTextField`, `TernaSwitch`, `TernaDialog`…), `Surfaces` (`TernaBackdrop`), `Brand` (`CARD_ASPECT_RATIO`), carta (`PlayingCard`) y assets (cartas 1–12 + dorso, edición Colombia) |
| `:feature:auth` | `SplashRoute` (reanuda sala activa), `LoginRoute` |
| `:feature:home` | Home, Perfil, Ajustes, Créditos |
| `:feature:room` | `CreateRoomRoute`, `JoinRoomRoute`, `WaitingRoomRoute(roomId)` |
| `:feature:game` | `GameRoute(matchId)` — la mesa |

### 5.2 Navegación

`Splash → Login → Home → (Crear | Unirse) → Sala de espera → Mesa`. Al entrar/salir de sesión se limpia la pila (`resetTo`). Si al abrir la app ya estás en una sala (`active/{uid}`), el splash te lleva directo a ella. La sala de espera llama a `onMatchStarted(matchId)` para pasar a la mesa.

### 5.3 La mesa

- **`GameLogic`** (pura, con pruebas): `isMyTurn`, `canAct`, `chaseValue`, `hiddenCards`, `otherTargets`, `revealTargets`, `remainingToSet`, `maxClaimCount`, `completeEnd`, `tray` (cartas en mesa durante la pausa) y `faceUpCenter`.
- **`GameViewModel`**: combina `meta + players + live + hands/{yo}` en un `MatchState`; banners narrados desde el punto de vista del jugador; diálogos paso a paso (`PickRevealTarget → PickEnd`, `PickNumber → PickClaimTarget → PickClaimCount`); reloj de mesa con la hora estimada del servidor; aviso de **tiempo agotado escalonado por asiento** (el dueño del turno avisa primero, los demás esperan 1 s + 0,7 s por asiento); manejo de `busy`; salida a sala o lobby.
- **Panel de acciones**: *Revelar centro*, *Preguntar*, *Pedir carta*, *¡Hacer terna!*, *Pasar turno* (si la sala lo permite), temporizador y la línea **"Buscando el N · x de 3 en la mesa"**.
- **Resultado**: diálogo de victoria (con variante épica para la terna de 7).

### 5.4 Cómo conecta la app con el backend

- Auth: `FirebaseAuth` (anónimo o Google vía Credential Manager).
- Lecturas: `callbackFlow` sobre `ValueEventListener` de RTDB.
- Escrituras: **siempre** `functions.getHttpsCallable(name).call(data)`, con errores traducidos a `ServerException(reason)` y luego a texto en español.
- Con `-PuseEmulators=true` todos los SDK apuntan a `10.0.2.2` (puertos 9099, 9000, 8080, 5001 + `emulatorPortOffset`).

---

## 6. Decisiones de diseño y por qué

| # | Decisión | Alternativa descartada | Razón |
|:-:|---|---|---|
| 1 | **Servidor autoritativo**: el cliente envía intenciones | Lógica en el cliente | En un juego de memoria con cartas ocultas, cualquier dato en el cliente es trampa potencial |
| 2 | **Kotlin nativo** (app) + **TypeScript** (backend) | Híbrido Java/TS, Flutter | Compose da la mejor UI Android; TS comparte tipos con el motor y es el runtime natural de Functions |
| 3 | **Motor puro** separado de Firebase | Lógica dentro de las funciones | Testeable al 100 %, reproducible, reutilizable por los bots |
| 4 | **RTDB para lo vivo, Firestore para lo permanente** | Solo Firestore | RTDB es más barata y rápida para escrituras frecuentes; Firestore da consultas para historial/perfil |
| 5 | **Nodo de partida actualizado en UNA transacción** | Varias escrituras | Evita estados mezclados |
| 6 | **`expectedSeq`** | Confiar en el orden de llegada | Descarta jugadas obsoletas y dobles toques |
| 7 | **Estado del motor como JSON string** en `secret/state` | Guardarlo como árbol | RTDB elimina arrays vacíos y `null` |
| 8 | **`secureRng` (47 bits de `crypto.randomInt`)** | Semilla de 32 bits | Una semilla pequeña permite deducir manos ajenas desde la propia |
| 9 | **Tiempo agotado dirigido por clientes** (`timeoutTurn`) | Cloud Tasks | Sin infraestructura extra; el servidor valida con su propio reloj; el servidor ya está probado con avisos simultáneos |
| 10 | **Pausas `startsAt`** tras fallo/terna | Avance instantáneo | Todos ven lo ocurrido; la UI muestra "…" |
| 11 | **Código `TRN-XXXX`** (4 caracteres) | `TRN-749` del diseño (3 dígitos) | 1.000 combinaciones eran adivinables/agotables |
| 12 | **Reglas como `TurnOptions` configurables** | Reglas fijas | "Flexible y divertido"; permite casas de reglas y comparar con Trio original |
| 13 | **"Pasar turno" activado, con mínimo 1 carta destapada** (antes apagado) | Siempre disponible / apagado | Con la carta equivocada oculta y una pregunta por rival, ya no regala información; y sin jugadas posibles siempre se puede pasar |
| 16 | **La carta que no coincide no se muestra** (`hideMismatch`) | Mostrar todas, como en Trio | Se pedía "que no se dé tanta información tan rápido"; sigue siendo opción de sala |
| 17 | **"Preguntar" (revelar la mano de un rival) una vez por turno** (`askOncePerTurn`) y **sin pedir varias a la vez** (`allowMultiClaim` apagado) | Límite por rival / límite en "Pedir carta" | Cada jugada saca una carta; con dos iguales seguidas se le pide el número dos veces. (Se probó límite por rival y límite en "Pedir carta"; el equipo aclaró que el límite es de "Preguntar") |
| 14 | **Perfil fuera de mi alcance** | — | Lo desarrolla otro compañero; evita conflictos |
| 15 | **Variantes `prod` desactivadas sin `google-services.json`** | Compilar siempre | "Build APK(s)" fallaba sin ese archivo |

---

## 7. Reglas del juego tal como están implementadas

### 7.1 Base

Mazo 1–12 × 3 copias (36 cartas), 3–6 jugadores. Reparto (mano/centro): 3→9/9, 4→7/8, 5→6/6, 6→5/6. Turno: revelar menor/mayor de cualquiera (incluida la propia mano) o una carta del centro; si coincide con lo revelado, sigue; si no, **todo vuelve a su sitio** y pasa el turno. Tres iguales = terna.

### 7.2 Deducir ("Pedir carta") — regla propia

Afirmar que un rival tiene el número N como carta **menor o mayor** (entre las ocultas). Acierto: la entrega y cuenta como revelada. Fallo: **"-1000 de aura"** y pierdes el turno (`claimFailEndsTurn`). Si ya hay cartas reveladas, solo vale ese mismo número (`CLAIM_VALUE_MISMATCH`, sin penalización).

### 7.3 Victoria

| Modo | Condición |
|---|---|
| Clásico | 3 ternas o terna de 7 (instantánea) |
| Picante | 2 ternas enlazadas (suman o restan 7) o terna de 7 |
| Mazo agotado | Gana quien tenga más ternas (puede haber empate) |

### 7.4 Información controlada (reglas del 28-oct, segunda tanda)

Pedido del equipo: que no se obtenga tanta información de la partida tan rápido en cada turno.

1. **Carta equivocada oculta** (`hideMismatch`). Al destapar algo que no coincide con el número perseguido, el motor no publica su valor: emite `TURN_FAILED { revealed: [las que sí coincidían], mismatch: {dónde} }`. Solo la primera carta del turno se ve siempre. La app pinta una carta boca abajo marcada en rojo y avisa *"No es la carta correcta: …"*.
2. **"Preguntar" una vez por turno** (`askOncePerTurn`): `REVEAL_HAND` sobre un rival. `turn.askUsed` lleva la cuenta; se reinicia al cambiar el turno o al completar una terna con `continueAfterSet`. Repetir responde `ASK_ALREADY_USED`. Revelar tu propia mano no cuenta (hace falta para "¡Hacer terna!"). **"Pedir carta" no tiene límite**, también con la misma persona: si un rival tiene dos cartas iguales seguidas, se le pide el número dos veces.
3. **Sin pedir varias de golpe** (`allowMultiClaim` apagado): `count > 1` responde `INVALID_COUNT`. El código de `count` sigue en el motor por si una sala lo activa. Sin `count`, pedir carta no sirve para vaciar una mano de golpe.
4. **Carta en el medio**: ya no se podía sacar (solo cuentan la menor y la mayor); ahora el aviso lo dice con claridad y hay pruebas explícitas.
5. **Pasar turno activado** (mínimo 1 carta destapada) y siempre visible en el panel, con el motivo cuando está apagado. Si no queda ninguna jugada (`isDeadEnd`) se puede pasar siempre, aunque la sala lo tenga apagado.
6. **Terna automática con la propia mano** (sin cambios): tras destapar una carta, si tus cartas iguales seguidas en un extremo completan la terna, el servidor las destapa solo.
7. **Más vida en la mesa**: el rival cuya carta está a la vista se resalta ("ENSEÑA UN 5"), confeti al completar una terna y al ganar, clasificación al terminar, aviso de quién juega después y vibración en los últimos 5 segundos del turno.

Las partidas en curso guardan su `RuleSet` al empezar: las que ya estaban jugándose antes de este cambio siguen con las reglas viejas.

### 7.5 Tiempo por turno

Cada acción reinicia `deadline = startsAt + turnSeconds`. Al vencer, cualquier cliente llama a `timeoutTurn`; el servidor comprueba **su** reloj y, si es cierto, pasa el turno sin penalizar las cartas (evento `TURN_TIMEOUT`). Tras vencer, la app ya no acepta toques del jugador.

---

## 8. Entornos, cuentas y permisos

| Dato | Valor |
|---|---|
| Proyecto dev | `terna-dev-718c7` (número `858929285767`) |
| Proyecto prod | `terna-prod` — **no existe todavía** (alias en `.firebaserc`) |
| Región | `us-central1` (RTDB y Functions) |
| RTDB dev | `https://terna-dev-718c7-default-rtdb.firebaseio.com` |
| Funciones | `https://us-central1-terna-dev-718c7.cloudfunctions.net/<nombre>` |
| Cuenta de servicio que ejecuta y compila | `858929285767-compute@developer.gserviceaccount.com` |
| Plan | **Blaze** (necesario para Functions; hay cuota gratuita) |

### Permisos que hubo que dar a mano (no los crea Firebase solo)

| # | Dónde | Qué | Por qué |
|:-:|---|---|---|
| 1 | IAM → cuenta `…-compute@…` | **Cuenta de servicio de Cloud Build** | Sin esto el despliegue falla con "missing permission on the build service account" |
| 2 | Cloud Run → las 8 funciones | **`allUsers` → Invocador de Cloud Run** | Sin esto Google rechaza el token de Firebase (401) antes de ejecutar el código |
| 3 | IAM → cuenta `…-compute@…` | **Administrador de Firebase Realtime Database** y **Usuario de Cloud Datastore** | Sin esto la función se cuelga al abrir RTDB/Firestore (timeout → "sin conexión") |

Los tres se repiten al crear `terna-prod`. Están en la lista de comprobación de `FIREBASE_SETUP.md` §14.

---

## 9. Incidencias y cómo se resolvieron

### 9.1 Infraestructura y despliegue

| Síntoma | Causa | Solución |
|---|---|---|
| `keytool` falla en Java 25 con configuración regional en español | `MissingFormatArgumentException` | Ejecutar con `-J-Duser.language=en` |
| Despliegue: "Timeout after 10000" al analizar | Windows lento | `FUNCTIONS_DISCOVERY_TIMEOUT=180` |
| Despliegue: permiso de compilación | Cuenta de servicio sin rol (proyecto nuevo) | Rol Cloud Build (tabla 8) |
| Predeploy falla en Windows | `$RESOURCE_DIR` no se expande | `npm --prefix functions run build` en `firebase.json` |
| Hosting: directorio inexistente | Faltaba `hosting/public` | Se creó con `index.html` y `sala/index.html` |
| Índice de Firestore inválido | Índice de un solo campo | Se eliminó (los simples son automáticos) |
| **"Tu sesión expiró" al crear sala** | Funciones privadas en Cloud Run (401 de Google, no de nuestro código) | `allUsers` → Invocador (tabla 8, #2). Diagnóstico: `curl` sin token devolvía página HTML "403" |
| **"Sin conexión" con internet** | Cuenta de ejecución sin permisos sobre RTDB; log: *"authentication credentials … are invalid"* + timeout | Roles de la tabla 8, #3 |
| **`startMatch` fallaba** | `randomInt(0, 2**48)` excede el límite (2⁴⁸−1) | `secureRng` usa 2⁴⁷ + prueba del generador real (`rng.test.ts`) |

### 9.2 Reglas y datos

| Síntoma | Causa | Solución |
|---|---|---|
| Reglas de Firestore con hueco | `hasAll` dejaba pasar campos extra | `hasOnly` + validación |
| Arrays vacíos desaparecían en RTDB | RTDB descarta `null`/arrays vacíos | Estado del motor como JSON string |
| Error "faltan jugadores" con 3 presentes (una vez) | Posible lectura de caché desactualizada en una transacción abortada | `transact()` ahora confirma todo rechazo con `ref.get()` y reintenta hasta 3 veces |

### 9.3 Android

| Síntoma | Solución |
|---|---|
| `firebase-auth` sin versión en módulos dependientes | `api(platform(libs.firebase.bom))` |
| `hiltViewModel` deprecado | Import de `androidx.hilt.lifecycle.viewmodel.compose` |
| Build prod falla sin `google-services.json` | Variantes prod deshabilitadas hasta que exista el archivo |
| Error de compilación `Dp * Int` en la mano | `val fanW = (screenW - 464.dp).coerceAtLeast(cardW * 2f)` |
| Menú "Generate App Bundles or APKs" deshabilitado en Android Studio | Se usó la variante `devDebug` desde la terminal (`./gradlew :app:assembleDevDebug`) y las variantes `prod` quedaron desactivadas hasta tener su `google-services.json` |
| Emulador `Medium_Phone` no arranca | Usar `New_Device` (API 35) |

### 9.4 Colaboración

- Un *merge* se commiteó con **marcadores de conflicto** en `TernaNavHost.kt`, `HomeNavigation.kt` y `HomeScreen.kt`. Se resolvió conservando la pantalla de perfil de Hugo y las salas (commit `47f6fa9`).
- **Pendiente de vigilar:** hacer `git pull` antes de empezar cada sesión de trabajo y revisar conflictos antes de commitear.

### 9.5 Abierto: turnos superpuestos tras agotarse el temporizador

**Reporte (28-oct):** *"cuando se acaba el temporizador y el jugador no hizo una acción se pasa turno pero en realidad se superponen los turnos y se bugea."*

**Lo investigado.** El servidor se revisó a fondo: `timeoutTurn` valida con su reloj, usa `expectedSeq` (solo un aviso se aplica; los demás reciben `STALE_STATE`), reinicia `startsAt/deadline` y hay pruebas de integración con **avisos simultáneos de varios jugadores** (`match.int.test.ts`). Sin reproducción por inspección.

**Cambios hechos como mitigación**

1. La app ya no deja actuar después de `deadline` (`GameUiState.canAct`), para no competir con el aviso de tiempo agotado.
2. `transact()` confirma los rechazos con datos frescos (posible fuente de errores falsos).
3. Cada tiempo agotado deja un log: `Tiempo agotado aplicado { matchId, reportedBy, seq }`.

**Cómo diagnosticarlo si vuelve** (ver también §12.1): anotar qué se ve exactamente (¿dos jugadores con botones activos?, ¿el reloj salta?, ¿cartas del turno anterior que se quedan en la mesa?, ¿alguien con la app en segundo plano?), la hora aproximada y ejecutar:

```bash
cd backend
npx --prefix rules-tests firebase functions:log --project terna-dev-718c7 --only timeoutTurn -n 50
```

**Hipótesis aún sin descartar**

- Mezcla transitoria de estados en el cliente: `observeMatch` combina cuatro listeners (`meta`, `players`, `live`, `hands/{yo}`) con `combine`; por un instante `live` puede ser nuevo y `hands` viejo.
- Diferencia de reloj entre teléfono y servidor mientras llega `.info/serverTimeOffset`.
- Arranque en frío de las funciones (varios segundos) que hace que una jugada hecha con poco tiempo llegue tarde y pierda la carrera contra el aviso de timeout; la jugada se descarta en silencio (`STALE_STATE`).

---

## 10. Estado de las pruebas

| Suite | Dónde | Comando | Resultado (28-oct) |
|---|---|---|:-:|
| Motor y generador aleatorio | `backend/functions/src/engine/*.test.ts` | `cd backend/functions && npx vitest run` | 64 ✅ |
| Reglas de seguridad | `backend/rules-tests` | `npm test` | 25 ✅ |
| Integración (Auth/RTDB/Firestore + servicios) | `backend/functions/src/integration` | `cd backend/rules-tests && npm run test:functions` | 42 ✅ |
| Lógica de la mesa (Android) | `feature/game/src/test` | `cd android && ./gradlew :feature:game:testDebugUnitTest` | 11 ✅ |

**Qué cubren los tests importantes**

- `simulation.test.ts`: partidas aleatorias completas; comprueba que **ninguna carta se crea ni se pierde** y que toda partida termina.
- `reducer.test.ts`: cada regla, incluidas "una pregunta por rival y turno", la carta equivocada oculta (y que su valor no aparece en el evento público), pasar turno con salida de emergencia, cartas en el medio y las pruebas de `allowMultiClaim`/terna automática.
- `views.test.ts`: la vista pública jamás filtra valores ocultos.
- `*.int.test.ts`: creación/unión/expulsión, concurrencia en las plazas, inicio exclusivo del anfitrión, `TOO_EARLY`, `STALE_STATE`, tiempo agotado concurrente, fin de partida y estadísticas idempotentes.

**No cubierto todavía**: pruebas de UI en Android (Compose), pruebas contra el proyecto real (solo manuales), pruebas de carga.

---

## 11. Trabajo en equipo y reglas de colaboración

| Área | Responsable |
|---|---|
| Desarrollo y lógica | Hugo, Espinosa (créditos de la app) |
| Diseño y arte | Miguel |
| Salas, partidas y backend (este trabajo) | Juan, con asistencia de Claude Code |
| **Perfil** (pantalla, chip del Home, estadísticas, foto, editar nombre) | **Otro compañero — no tocar** |

Reglas acordadas durante el trabajo:

1. **No modificar** archivos del perfil (`feature/home/profile`, chip del Home, `UserRepository`, `PlayerProfile`, fotos con Coil). Solo cablear lo estrictamente necesario (los botones Crear/Unirse del Home).
2. **Nunca** matar procesos de forma amplia (`taskkill /IM java.exe`, matar PIDs por puerto). Lanzar emuladores/servidores en segundo plano para poder detenerlos con la herramienta de tareas; cerrar solo los procesos propios y con permiso explícito.
3. Responder y documentar **en español**.
4. Las reglas del juego deben ser **flexibles y divertidas**: las desviaciones de Trio van como opciones (`TurnOptions`) y no se endurecen sin preguntar.
5. Mantener el motor **parametrizado** para el futuro modo CUARTA.
6. `git pull` antes de empezar; revisar conflictos antes de commitear.
7. Actividad reciente del equipo en `main`: perfil, foto de Google en alta resolución, huella SHA-1 de desarrollo, música y efectos de sonido.

---

## 12. Próximos pasos (plan detallado)

Prioridad: **P0** (ya) → **P1** (siguiente hito) → **P2** (antes de abrir a más gente) → **P3** (producto) → **P4** (producción). Las estimaciones son orientativas para una persona.

### P0 — Cerrar lo abierto

#### 12.1 Reproducir y cerrar el bug de turnos superpuestos *(≈ 0,5–1 día)*

1. Jugar con 3 dispositivos y `turnSeconds` bajo (p. ej. 15 s) hasta que se agote un turno sin acción.
2. Anotar qué se ve y revisar `timeoutTurn` en los logs (comando en §9.5).
3. Comprobar las hipótesis en este orden: (a) estado mezclado entre listeners → combinar `live`+`players`+`hands` en un solo listener sobre `matches/{id}` con filtro, o ignorar emisiones donde `live.seq` ≠ `hands` esperada; (b) reloj/offset; (c) arranque en frío → considerar `minInstances: 1` para `gameAction` y `timeoutTurn`.
4. Agregar una prueba de integración o de Android que reproduzca el caso una vez entendido.

**Criterio de cierre:** 10 timeouts seguidos sin turnos dobles y sin jugadas fantasma.

#### 12.2 Higiene del repositorio *(≈ 1 h)*

- Commitear `README.md` y `docs/` (este archivo incluido).
- Confirmar que `main` compila (`./gradlew :app:assembleDevDebug`) y que los 4 sets de pruebas pasan antes de cada push.
- Añadir al `.gitignore` los logs de emuladores si aparecen (`backend/*-debug.log`).

### P1 — Bots, partida rápida y presencia *(el siguiente hito grande; ≈ 4–6 días)*

Es lo que convierte el juego en jugable sin 3 humanos y habilita la **Partida rápida**.

#### 12.3 Bots en el servidor

**Objetivo.** Poder llenar una sala con bots (anfitrión elige cuántos y dificultad) y que jueguen con un comportamiento humano, sin trampa.

**Diseño propuesto**

- **Módulo puro** `backend/functions/src/bots/`:
  - `memory.ts`: memoria de cada bot. Registra los hechos *públicos* ("el jugador X tenía el valor V como carta menor/mayor cuando se reveló") que vienen de los eventos `CARD_REVEALED` / `TURN_FAILED`. Cada hecho se **olvida** con probabilidad según dificultad (fácil ≈ 40 % de recuerdo, medio ≈ 70 %, difícil ≈ 95 %). Los hechos se invalidan cuando cambia el extremo de esa mano (carta retirada por una terna).
  - `strategy.ts`: `chooseAction(state, botId, memory, level, rng)`. Solo puede mirar: **su propia mano**, **el estado público** (`publicView`) y **su memoria**. Orden de preferencia: (1) si la propia mano completa la terna, hacerlo; (2) si recuerda con certeza dónde están las cartas que faltan, `CLAIM` con `count` correcto; (3) si va persiguiendo un valor y recuerda una carta de ese valor en un extremo, revelarla; (4) revelar un extremo del que no tenga información (preferir extremos con más probabilidad de ser números ya vistos); (5) como último recurso, el centro.
  - Para la dificultad fácil, permitir `CLAIM` arriesgados (más fallos); para difícil, solo deducir cuando la memoria es fiable.
- **Identidad**: uid sintético `bot_<n>` en `matches/{id}/players/{botUid}` con `isBot: true`, nombre y avatar propios; `hands/{botUid}` **no** se publica (la mano vive en `secret/state`). La memoria de los bots se guarda en `secret/state` bajo `bots`.
- **Configuración de sala** (`rooms/{id}/config`): `botFill` (0–N), `botLevel` (`EASY`/`MEDIUM`/`HARD`). Validar que humanos + bots esté en [3, 6]. `startMatch` crea los bots.
- **Quién ejecuta el turno del bot** (decisión a tomar, recomendado A):
  - **A. Dirigido por clientes** (coherente con `timeoutTurn`): nueva callable `botTurn({ matchId, expectedSeq })`. Cualquier humano de la mesa la llama cuando `turn.player` es un bot y ya pasó `startsAt + retardo humano` (0,8–1,5 s). El servidor verifica que el jugador en turno es bot, calcula la jugada y la aplica con `mutateMatch`. Duplicados se descartan por `expectedSeq`. *Sin infraestructura nueva.* Límite: si no queda ningún humano conectado, la partida se detiene (aceptable).
  - **B. Cloud Tasks**: más robusto (juega aunque no haya humanos) pero añade cola, IAM (`cloudtasks.enqueuer`, `iam.serviceAccountUser`, ya previstos en la guía) y costo/complejidad.
- **Retardo humano**: el servidor guarda `botReadyAt` en `timing` y rechaza `botTurn` antes de esa hora (`TOO_EARLY`), con jitter para que no parezcan relojes.
- **Fin de turno del bot**: cuando el turno pasa a un humano, el bot deja de ser llamado; la pausa `startsAt` se respeta.

**Pruebas obligatorias**

1. Simulación bot-vs-bot (3–6 jugadores, las 3 dificultades) hasta terminar: no se crean/pierden cartas; no hay bucles infinitos.
2. "No-trampa": inyectar un estado y comprobar que `chooseAction` **no depende** de manos ajenas (mismo resultado si se cambian las cartas ocultas ajenas).
3. Integración: sala con bots → `startMatch` → humano juega → `botTurn` responde → el turno vuelve al humano.
4. Fuerza relativa (informativa): difícil gana más que fácil en N partidas simuladas.

**App**: en Crear sala, controles de "Rellenar con bots" y dificultad; bots con insignia en la sala de espera y la mesa; el cliente llama a `botTurn` desde el `GameViewModel` con el mismo mecanismo escalonado del timeout.

**Criterio de cierre:** una persona sola puede crear sala con 2 bots y terminar una partida completa.

#### 12.4 Partida rápida (`queue/`)

1. Callable `quickPlay({ players })`: busca en `queue/{modo}_{n}` una sala pública `WAITING` con plaza (transacción, sin carreras por el último puesto); si no hay, la crea y la publica.
2. Arranca al llenarse **o** a los ~20 s completando con bots (callable `startWithBots`, dirigida por cliente igual que el timeout).
3. App: botón "Partida rápida" del Home → modal "Buscando oponentes…" con cancelar (`leaveRoom`) → mesa.
4. Pruebas de integración con N búsquedas simultáneas (ninguna sala sobrepasa su aforo).

#### 12.5 Presencia y desconexión

1. Cliente: `.info/connected` + `onDisconnect().set(false)` en `presence/{uid}` (y `connected` en `matches/{id}/players/{uid}`).
2. Detección: si un jugador lleva ~30 s desconectado, cualquier cliente llama a `replaceWithBot` (misma idea dirigida por clientes); el bot toma su mano y su asiento. Si el jugador vuelve, recupera el puesto (el servidor decide).
3. Reglas RTDB para `presence/` (cada usuario solo escribe el suyo).
4. Tratar también **timeouts repetidos**: tras N timeouts seguidos de un jugador, reemplazarlo por un bot.

#### 12.6 Revancha

`rematch` desde el diálogo de resultado: vuelve a la sala con los mismos jugadores y arranca otra partida (el anfitrión decide) — hoy ya existe "Volver a la sala".

### P2 — Robustez antes de abrir a más gente *(≈ 3–4 días)*

| Tema | Detalle |
|---|---|
| **App Check** (Play Integrity) | Obligatorio en todas las callables; tokens de depuración para el emulador y dev |
| **Límite de frecuencia** | Por UID en `gameAction`/`createRoom`/`joinRoom` |
| **Arranque en frío** | Evaluar `minInstances: 1` en `gameAction` y `timeoutTurn` (parámetro `GAME_MIN_INSTANCES`: 0 en dev, 1 en prod; ~unos pocos USD/mes) |
| **Limpieza de salas abandonadas** | Función programada cada 15 min que borra salas `WAITING` viejas y libera `active/` huérfanos |
| **Presupuesto y alertas** | Presupuesto en Blaze con alertas por correo |
| **Observabilidad** | Logs estructurados, Crashlytics y Performance en la app |
| **Pruebas Android** | Pruebas de Compose para Crear/Unirse/Sala/Mesa; CI que corra los 4 sets de pruebas |
| **Revisión de seguridad** | Revisar reglas y callables con `/security-review` antes de producción |

### P3 — Producto

| Tema | Detalle |
|---|---|
| **Modo CUARTA** | Nuevo `RuleSet` (`setSize: 4`, `copies: 4`, 48 cartas), nuevas tablas de reparto y diseño de la carta de 4 copias; la UI debe leer `setSize` (hoy `PlayingCard` y `SetsPile` pintan 3) |
| **Modo Picante en la UI** | El motor ya lo soporta; falta el selector en Crear sala y mostrar los "enlaces" de cada carta |
| **Modo Aprendiz** | Pistas visibles (solo en privadas o contra bots) |
| **Facebook Login** | Pantalla lista, falta configurar Meta (hash de clave, app id) |
| **Edición México** | Segundo set de cartas |
| **Salas públicas** | Pantalla "Salas disponibles" con búsqueda y filtros |
| **Sonido y música** | Ya añadidos por el equipo; revisar volumen/ajustes |
| **Enlaces de sala** | App Links `https://terna.web.app/sala/TRN-XXXX` (falta `assetlinks.json`) |

### P4 — Producción

1. Crear `terna-prod`, repetir la guía y los **tres permisos manuales** de §8.
2. Añadir `android/app/src/prod/google-services.json` (activa las variantes prod).
3. Huella SHA de **Play App Signing** en Firebase, Meta y `assetlinks.json`.
4. CI (GitHub Actions): pruebas en cada PR; despliegue a `prod` al crear un *tag* con cuenta de servicio dedicada.
5. Política de privacidad y borrado de cuenta (`deleteAccount`) — requisito de Google Play.
6. Formularios de Play Console (seguridad de datos; clasificación de contenido: cuidado con "juegos de azar simulados" por el "aura/777").
7. Remote Config (segundos de turno, espera de partida rápida, textos).

---

## 13. Preguntas abiertas

| # | Pregunta | Quién | Impacto |
|:-:|---|---|---|
| 1 | ¿Los bots corren dirigidos por clientes (A) o con Cloud Tasks (B)? | Equipo / Juan | Arquitectura de bots y costos |
| 2 | ¿Cuántos segundos por turno por defecto? Hoy lo define la sala | Producto | Ritmo del juego y timeouts |
| 3 | Modal "Ingresar código" vs. campo de código en pantalla | Miguel | Pantalla Unirse |
| 4 | Semántica del chip del Perfil ("42 de 15") | Equipo de perfil | Home |
| 5 | Logo en PNG y tipografías oficiales | Miguel | Marca |
| 6 | ¿Se publica en Play Store? Define los requisitos legales de P4 | Equipo | Alcance de producción |
| 7 | ¿El "-1000 de aura" tiene efecto en estadísticas/perfil o es solo mensaje? | Equipo | Modelo de datos |
| 8 | ¿Qué hacer si queda un solo humano y el resto son bots que se desconectan? | Producto | Presencia |

---

## 14. Anexos

### Anexo A — Códigos de error y mensajes

| `reason` | Mensaje en la app |
|---|---|
| `ROOM_NOT_FOUND` | No existe una sala con ese código. |
| `INVALID_CODE` | El código no es válido. Se ve así: TRN-7K4Q. |
| `ROOM_FULL` | La sala está llena. |
| `ROOM_STARTED` | La partida de esa sala ya empezó. |
| `ROOM_PRIVATE` | Esa sala es privada: necesitas el código. |
| `BANNED` | El anfitrión te sacó de esa sala. |
| `ALREADY_IN_ROOM` | Ya estás en una sala. |
| `NOT_ENOUGH_PLAYERS` | Se necesitan al menos 3 jugadores para empezar. |
| `NOT_HOST` | Solo el anfitrión puede hacer eso. |
| `ROOM_IN_MATCH` | No puedes salir mientras la partida está en curso. |
| `NOT_YOUR_TURN` | Todavía no es tu turno. |
| `TOO_EARLY` | Espera un momento: todos están viendo las cartas. |
| `STALE_STATE` | La mesa cambió, inténtalo de nuevo. *(la app lo ignora en silencio)* |
| `GAME_OVER` | La partida ya terminó. |
| `NETWORK` | Sin conexión. Revisa tu internet e inténtalo de nuevo. |
| `UNAUTHENTICATED` | Tu sesión expiró. Vuelve a iniciar sesión. |
| otros | Algo salió mal. Inténtalo de nuevo. |

También tienen mensaje: `ASK_ALREADY_USED` ("Solo puedes preguntar una vez por turno…"), `PASS_TOO_EARLY`, `PASS_DISABLED`, `CLAIM_VALUE_MISMATCH`, `INVALID_COUNT` y `NO_HIDDEN_CARDS`.

Códigos solo del motor/servidor que aún no tienen mensaje propio: `NOT_TIMED_OUT`, `INVALID_ACTION`, `INVALID_SLOT`, `SLOT_EMPTY`, `SLOT_ALREADY_REVEALED`.

### Anexo B — Comandos que se usan a diario

```bash
# Pruebas
cd backend/functions   && npx vitest run                       # motor (64)
cd backend/rules-tests && npm test                             # reglas de seguridad (25)
cd backend/rules-tests && npm run test:functions               # integración (42)
cd android             && ./gradlew :feature:game:testDebugUnitTest   # mesa (11)

# Compilar la app (APK en android/app/build/outputs/apk/dev/debug/)
cd android && ./gradlew :app:assembleDevDebug
cd android && ./gradlew :app:assembleDevDebug -PuseEmulators=true      # contra emuladores

# Backend
cd backend/functions && npm run build                          # tsc (comprobación de tipos y compilación)
cd backend
export FUNCTIONS_DISCOVERY_TIMEOUT=180                          # en Windows
npx --prefix rules-tests firebase deploy --only functions --project terna-dev-718c7 --force
npx --prefix rules-tests firebase deploy --only database,firestore:rules --project terna-dev-718c7

# Diagnóstico
npx --prefix rules-tests firebase functions:log --project terna-dev-718c7 --only createRoom -n 30
npx --prefix rules-tests firebase functions:list --project terna-dev-718c7
curl -s -X POST -H "Content-Type: application/json" -d '{"data":{}}' \
  https://us-central1-terna-dev-718c7.cloudfunctions.net/ping       # debe dar JSON UNAUTHENTICATED
```

### Anexo C — Glosario

| Término | Significado |
|---|---|
| **Terna** | Tres cartas iguales (en CUARTA, cuatro) |
| **Deducir / Pedir carta** | Afirmar que un rival tiene un número como carta menor o mayor |
| **Aura** | El "-1000 de aura" es el mensaje de burla cuando se falla una deducción |
| **Jackpot 777** | Terna de 7: victoria instantánea |
| **Picante** | Modo con ternas enlazadas (suman o restan 7) |
| **Servidor autoritativo** | Solo el servidor decide y conoce las cartas ocultas |
| **`seq`** | Contador de eventos; protege contra jugadas con estado viejo |
| **`startsAt` / `deadline`** | Cuándo se puede jugar tras la pausa / cuándo vence el turno |
| **`expectedSeq`** | El `seq` que vio la app al mandar su jugada |
| **Callable** | Cloud Function invocable desde la app con autenticación |
| **RuleSet / TurnOptions** | Datos que definen un modo de juego / reglas de turno ajustables |
| **Casa de reglas (house rules)** | Variantes configurables por sala |

### Anexo D — Mapa mental para retomar el trabajo

1. Lee el [`README.md`](../README.md) y la §1 y §12 de este documento.
2. `git pull`, compila y corre los 4 sets de pruebas.
3. Si tocas reglas: empieza por `engine/` y sus pruebas; **luego** el servicio; **luego** la app.
4. Si tocas datos: actualiza `ESQUEMA_BASE_DATOS_FIREBASE.md`, `database.rules.json` y `rules-tests` en el mismo cambio.
5. Después de **desplegar a un entorno nuevo**, ejecuta la comprobación de funciones públicas (Anexo B, `curl`) antes de culpar a la app.
6. No toques el perfil. Documenta en español.
