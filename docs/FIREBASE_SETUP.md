# TERNA! — Guía de configuración de Firebase

> Versión 0.1 · 2026-10-25 · Complementa a [ARQUITECTURA.md](ARQUITECTURA.md)
>
> Sigue los pasos **en orden** y repítelos para cada entorno: primero `terna-dev` y, cuando todo funcione, `terna-prod`. Marca cada casilla al terminar.

**Valores que se usan en toda la guía** (cámbialos aquí si el equipo decide otros):

| Dato | Valor |
|---|---|
| Proyectos | `terna-dev` (pruebas) · `terna-prod` (usuarios reales) |
| Paquete Android (prod) | `com.ternagames.terna` |
| Paquete Android (dev) | `com.ternagames.terna.dev` |
| Región de todo | **`us-central1`** (ver §3) |
| Dominio de enlaces | `terna.web.app` (Hosting de `terna-prod`) |

---

## 0. Antes de empezar

- [ ] **Cuentas Google** de cada integrante (Hugo, Espinosa, Miguel). Cada uno con la suya, nunca una cuenta compartida.
- [ ] Instalar en cada computador de desarrollo:
  - Node.js 22 (las funciones corren en Node 22)
  - **JDK 21** (lo necesitan los emuladores de Firebase)
  - Android Studio (versión estable más reciente)
  - Firebase CLI:
    ```bash
    npm install -g firebase-tools
    ```
    ```bash
    firebase login
    ```
- [ ] Una **tarjeta de crédito** para el plan Blaze (§2). Con el tráfico de un proyecto universitario lo normal es pagar 0 o pocos dólares al mes, pero Firebase exige el plan para usar Cloud Functions.

---

## 1. Crear los proyectos

Firebase console → **Agregar proyecto**.

- [ ] Nombre `terna-dev`. Si el ID `terna-dev` ya está ocupado, Firebase le agrega un sufijo. **El ID real de dev es `terna-dev-718c7`**: ese es el que va en `backend/.firebaserc`, no el nombre visible.
- [ ] Google Analytics: **activado** y crear una cuenta "TERNA". Lo usan Crashlytics y Remote Config.
- [ ] Repetir con `terna-prod`.

**Permisos del equipo** (⚙️ Configuración del proyecto → Usuarios y permisos):

| Persona | Rol |
|---|---|
| Hugo, Espinosa (desarrollo) | Editor |
| Miguel (diseño) | Visualizador (para ver Analytics y Crashlytics) |
| Un responsable | Propietario (solo una o dos personas) |

---

## 2. Plan Blaze y control de gastos

- [ ] En cada proyecto: ⚙️ → **Uso y facturación** → cambiar al plan **Blaze**.
- [ ] Google Cloud console → **Facturación → Presupuestos y alertas** → crear un presupuesto por proyecto:
  - `terna-dev`: USD 5, con alertas al 50 %, 90 % y 100 %.
  - `terna-prod`: USD 15, con las mismas alertas.

  ⚠️ El presupuesto **solo avisa, no corta el servicio**. Si una alerta llega, hay que revisar enseguida.
- [ ] Blaze mantiene las cuotas gratuitas del plan Spark (lecturas, escrituras, invocaciones…). La mayoría de las pruebas deberían caber ahí.

### 2.1 Costos estimados (verificar precios vigentes en firebase.google.com/pricing)

| Concepto | Costo |
|---|---|
| Cuenta de desarrollador de Google Play | **USD 25, un solo pago** |
| Uso normal de Firebase en desarrollo y beta (cientos de partidas al mes) | USD 0, dentro de las cuotas gratuitas |
| `minInstances: 1` en `gameAction` (función siempre encendida) | Unos USD 3–10 al mes. **Solo en prod, y solo cuando haya jugadores reales** |
| Dominio propio (opcional) | Unos USD 10–15 al año |
| Meta for Developers, Auth (anónimo/Google/Facebook), Crashlytics, Analytics, Remote Config, App Distribution, FCM | Gratis |

**Riesgos de gasto inesperado:** un trigger que se dispara a sí mismo en bucle, listeners de Firestore que releen colecciones enteras, o abuso de la API sin App Check. Las alertas de presupuesto avisan pero no cortan. Si quieren un corte automático, Google documenta cómo desactivar la facturación con una función que escucha la notificación del presupuesto (Pub/Sub). Ojo: al desactivarla, el proyecto deja de funcionar.

---

## 3. Elegir la región (decisión permanente)

⚠️ **La ubicación de Firestore y de Realtime Database no se puede cambiar después de crearlas.**

- **Realtime Database** solo se puede alojar en unas pocas ubicaciones. En América la única opción era `us-central1` (Iowa) al momento de escribir esta guía; verifícalo en la consola.
- La partida en vivo vive en RTDB y las funciones le escriben en cada jugada, así que **todo va en `us-central1`**:

| Servicio | Ubicación |
|---|---|
| Realtime Database | `us-central1` |
| Firestore | `us-central1` (o la multirregión `nam5`, que la incluye) |
| Cloud Functions y Cloud Tasks | `us-central1` |

> Esto corrige la recomendación de `us-east1` en ARQUITECTURA.md §4.1. Desde Colombia, `us-central1` da una latencia de unos 80–110 ms, suficiente para un juego por turnos.

---

## 4. Registrar la app Android

En cada proyecto: Descripción general → **Agregar app → Android**.

| Proyecto | Paquete | Apodo |
|---|---|---|
| `terna-dev` | `com.ternagames.terna.dev` | TERNA Dev |
| `terna-prod` | `com.ternagames.terna` | TERNA |

### 4.1 Huellas SHA (obligatorias)

Sin ellas fallan el inicio de sesión con Google, App Check y los App Links.

- [ ] **Debug** (la de cada computador): en la raíz del proyecto Android, ejecuta el comando de abajo y copia el `SHA1` y el `SHA-256` de la variante `debug`.
  ```bash
  ./gradlew signingReport
  ```
- [ ] **Release / subida**: la de la keystore con la que firmas los AAB.
- [ ] **Play App Signing**: Play Console → tu app → Configuración → **Integridad de la app** → copia el SHA-1 y el SHA-256 de la "clave de firma de apps". Google re-firma la app con esta clave, y es la que ven los usuarios.

Agrega todas en ⚙️ Configuración del proyecto → tu app → **Huellas digitales del certificado SHA**:
- `terna-dev`: la huella debug de cada integrante.
- `terna-prod`: la de release y la de Play App Signing (y la debug si quieres probar prod desde Android Studio).

### 4.2 `google-services.json` por entorno

- [ ] Descarga el archivo de cada proyecto y colócalo así:
  ```
  android/app/src/dev/google-services.json    ← de terna-dev
  android/app/src/prod/google-services.json   ← de terna-prod
  ```
- [ ] En `app/build.gradle.kts`, define los *flavors*:
  ```kotlin
  flavorDimensions += "env"
  productFlavors {
      create("dev")  { dimension = "env"; applicationIdSuffix = ".dev" }
      create("prod") { dimension = "env" }
  }
  ```
- [ ] Vuelve a descargar el `google-services.json` cada vez que actives un proveedor de login o agregues una huella SHA.

---

## 5. Authentication

Consola → **Authentication → Comenzar** → pestaña **Método de acceso**.

### 5.1 Anónimo (invitado)
- [ ] Habilitar **Anónimo**.

### 5.2 Google
- [ ] Habilitar **Google** y poner el correo de asistencia del proyecto.
- [ ] Al activarlo se crea un **cliente OAuth web**. Su ID llega a la app como `R.string.default_web_client_id` (vía `google-services.json`), y es el *server client ID* que pide **Credential Manager** para "Iniciar sesión con Google".
- [ ] Google Cloud → **API y servicios → Pantalla de consentimiento OAuth**: nombre "TERNA!", logo, correo de asistencia, y los enlaces a la política de privacidad y a los términos (§11).

### 5.3 Facebook
1. [ ] En **Meta for Developers** (developers.facebook.com), crea una app de tipo "Consumidor" con el caso de uso "Autenticar y solicitar datos con Facebook Login".
2. [ ] Copia el **App ID** y el **App Secret**.
3. [ ] En Firebase, habilita **Facebook** y pega ambos valores. Firebase muestra una **URI de redirección OAuth**: cópiala.
4. [ ] En Meta → Facebook Login → Configuración, pega esa URI en "URI de redireccionamiento de OAuth válidos".
5. [ ] En Meta → Configuración → Básica → **Agregar plataforma Android**:
   - Nombre del paquete y clase de la actividad principal.
   - **Hashes de clave**: Facebook usa el SHA-1 en Base64. Genera uno por cada keystore (debug, release y Play App Signing):
     ```bash
     keytool -exportcert -alias androiddebugkey -keystore ~/.android/debug.keystore -storepass android | openssl sha1 -binary | openssl base64
     ```
     Para la clave de Play App Signing, convierte su SHA-1 (hexadecimal) a Base64.
6. [ ] Para publicar la app de Meta (modo "Activo") necesitas URL de política de privacidad, URL de **instrucciones de eliminación de datos** (§11) e ícono. Mientras esté en modo desarrollo, solo pueden entrar los usuarios agregados como evaluadores.
7. [ ] Pide solo los permisos `public_profile` y `email`. No piden revisión avanzada.

### 5.4 Ajustes generales de Auth
- [ ] Authentication → Configuración → **Vinculación de cuentas de usuario**: deja "Vincular las cuentas que usan el mismo correo electrónico". Así, quien entra con Google y luego con Facebook usando el mismo correo no termina con dos cuentas.
- [ ] **Dominios autorizados**: agrega `terna.web.app` (y tu dominio propio, si lo usas).
- [ ] *(Opcional)* Para borrar automáticamente las cuentas anónimas inactivas hay que pasar a **Firebase Authentication con Identity Platform**. Revisa su precio antes. Alternativa sin costo: una función programada que borre las cuentas anónimas sin actividad en 30 días.

---

## 6. Realtime Database (partidas en vivo)

- [ ] Consola → **Realtime Database → Crear base de datos** → ubicación `us-central1` → **modo bloqueado**.
- [ ] Anota la URL (`https://terna-dev-default-rtdb.firebaseio.com`). El SDK la toma sola de `google-services.json`.
- [ ] **No edites las reglas en la consola.** Se versionan en el repositorio (`backend/database.rules.json`) y se despliegan con el CLI (§14), para que dev y prod queden iguales y revisadas en código.

---

## 7. Cloud Firestore (perfiles, estadísticas, historial)

- [ ] Consola → **Firestore Database → Crear base de datos**.
  - Si pregunta la edición, elige **Standard**.
  - ID de la base: `(default)`.
  - Ubicación: `us-central1` o `nam5`.
  - Modo: **producción** (todo bloqueado).
- [ ] Las reglas (`backend/firestore.rules`) y los índices (`backend/firestore.indexes.json`) se despliegan con el CLI.
- [ ] *(Opcional, en prod)* Firestore → **Recuperación ante desastres**: activa la recuperación de un momento determinado (PITR) cuando haya usuarios reales.

---

## 8. Cloud Functions y Cloud Tasks

- [ ] Consola → **Functions → Comenzar**. Esto activa las API necesarias (Cloud Functions, Cloud Build, Artifact Registry, Cloud Run, Eventarc). Si algún despliegue falla por una API desactivada, el error trae el enlace para activarla.
- [ ] Google Cloud → API y servicios → habilitar **Cloud Tasks API** (temporizador de turno).
- [ ] Desde `backend/`, configura cuánto tiempo se guardan las imágenes de cada despliegue. Sin esto se acumulan en Artifact Registry y cobran almacenamiento.
  ```bash
  firebase functions:artifacts:setpolicy
  ```
- [ ] **Permiso de compilación (obligatorio en proyectos nuevos o dentro de una organización).** Si `firebase deploy --only functions` falla con *"Could not build the function due to a missing permission on the build service account"*, a la cuenta de servicio de compilación le falta un rol. En Google Cloud → **IAM** → **Otorgar acceso**:
  - Principal: `NÚMERO_DEL_PROYECTO-compute@developer.gserviceaccount.com` (en `terna-dev` es `858929285767-compute@developer.gserviceaccount.com`).
  - Rol: **Cuenta de servicio de Cloud Build** (`roles/cloudbuild.builds.builder`).
  Si la organización lo bloquea, un administrador de la UPB debe otorgarlo.
- [ ] **Permisos de ejecución sobre las bases de datos (obligatorio en proyectos nuevos).** La misma cuenta (`…-compute@developer.gserviceaccount.com`) es la que *ejecuta* las funciones, y en proyectos nuevos no trae acceso a Realtime Database ni a Firestore. Síntoma: crear sala se queda cargando y termina en «sin conexión»; en el log de `createRoom` sale *"@firebase/database: FIREBASE WARNING: Provided authentication credentials … are invalid"* y luego *"maximum request timeout"*. En IAM → **Otorgar acceso**, a esa cuenta:
  - **Administrador de Firebase Realtime Database** (`roles/firebasedatabase.admin`)
  - **Usuario de Cloud Datastore** (`roles/datastore.user`, da acceso a Firestore)
- [ ] **Tiempo de análisis en Windows.** Si el despliegue o el emulador fallan con *"User code failed to load… Timeout after 10000"*, aunque el código cargue bien, amplía el plazo (en PowerShell, desde `backend/`):
  ```powershell
  $env:FUNCTIONS_DISCOVERY_TIMEOUT = 180
  ```
- [ ] **Permisos para encolar tareas**: la cuenta de servicio de las funciones (`PROJECT_NUMBER-compute@developer.gserviceaccount.com`) necesita, en Google Cloud → IAM, los roles:
  - **Encolador de Cloud Tasks** (`roles/cloudtasks.enqueuer`)
  - **Usuario de cuenta de servicio** (`roles/iam.serviceAccountUser`)
- [ ] **Parámetros por entorno** (en código, con `defineInt` de `firebase-functions/params`):
  - `GAME_MIN_INSTANCES`: `0` en dev, `1` en prod. Mantiene `gameAction` caliente; cuesta unos pocos dólares al mes, así que actívalo solo cuando haya jugadores reales.
  - `QUICKPLAY_WAIT_SECONDS`, `TURN_SECONDS`, si no se leen de Remote Config.
- [ ] No hacen falta secretos para el MVP. Si alguno aparece después, va en **Secret Manager** (`firebase functions:secrets:set NOMBRE`), nunca en el código.

---

## 9. App Check (que solo tu app hable con el backend)

- [ ] Play Console → tu app → Configuración → **Integridad de la app** → **API de Play Integrity** → vincular el proyecto de Google Cloud (`terna-prod`, y también `terna-dev` si lo distribuyes por Play).
- [ ] Firebase → **App Check** → tu app Android → proveedor **Play Integrity**. Pide la huella SHA-256 (§4.1).
- [ ] **Desarrollo**: en debug, la app usa `DebugAppCheckProviderFactory`. Al abrirla, imprime un token en Logcat: regístralo en App Check → tu app → **Administrar tokens de depuración**, uno por teléfono o emulador de cada integrante.
- [ ] Activa la aplicación obligatoria por fases:
  1. **Monitorear** 1–2 semanas, con la gráfica de "solicitudes verificadas" en App Check.
  2. **Aplicar** en Realtime Database y Firestore desde la consola de App Check.
  3. En las callables, `enforceAppCheck: true` en el código de cada función.

---

## 10. Remote Config

Consola → **Remote Config → Crear configuración**. Parámetros iniciales:

| Parámetro | Tipo | Valor por defecto | Uso |
|---|---|---|---|
| `turn_seconds` | número | `20` | Duración del turno |
| `quickplay_wait_seconds` | número | `20` | Espera antes de rellenar con bots |
| `quickplay_players` | número | `4` | Tamaño de la partida rápida |
| `default_ruleset` | texto | `TERNA_CLASSIC` | Modo por defecto |
| `allow_pass_default` | booleano | `false` | Valor inicial de "Pasar turno" en salas nuevas |
| `bot_difficulty` | texto | `MEDIUM` | Memoria de los bots |
| `min_supported_version_code` | número | `1` | Obliga a actualizar versiones viejas |
| `maintenance_mode` | booleano | `false` | Muestra un aviso y bloquea partidas |

- [ ] Publica la configuración. Pon los mismos valores como predeterminados en la app (`res/xml/remote_config_defaults.xml`), para que funcione sin conexión la primera vez.

---

## 11. Hosting (App Links, privacidad, borrado de cuenta)

- [ ] Consola → **Hosting → Comenzar** en `terna-prod`. El sitio por defecto será `terna-prod.web.app`.
  - Para tener `terna.web.app`, crea un **sitio adicional** con ese nombre (Hosting → Agregar otro sitio), si está libre.
  - O conecta un dominio propio.
- [ ] Publica en el Hosting (carpeta `backend/hosting/public/`):

| Ruta | Contenido | Quién lo exige |
|---|---|---|
| `/.well-known/assetlinks.json` | Paquete + SHA-256 de Play App Signing y de release | Android App Links (enlaces `/sala/TRN-XXXX`) |
| `/sala/*` | Página "Abre TERNA! o descárgala" con botón a Play Store | Invitaciones desde WhatsApp |
| `/privacidad` | Política de privacidad | Google Play, Meta, pantalla OAuth |
| `/terminos` | Términos de uso | Pantalla OAuth |
| `/eliminar-cuenta` | Cómo borrar la cuenta y los datos | **Google Play** (apps con cuentas) y **Meta** |

- [ ] Ejemplo de `assetlinks.json`:
  ```json
  [{
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "com.ternagames.terna",
      "sha256_cert_fingerprints": ["AA:BB:...:FF"]
    }
  }]
  ```
- [ ] Compruébalo después de publicarlo en: `https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https://terna.web.app&relation=delegate_permission/common.handle_all_urls`

> ⚠️ **Borrado de cuenta:** Google Play exige que las apps que crean cuentas ofrezcan borrar la cuenta **desde la app y desde la web**. Hay que planear una callable `deleteAccount` que borre Auth, el perfil de Firestore y las estadísticas.

---

## 12. Calidad y métricas

- [ ] **Crashlytics**: consola → Crashlytics → habilitar. En Gradle, plugin `com.google.firebase.crashlytics`. Fuerza un error de prueba en dev para ver que llega.
- [ ] **Analytics**: ya activo (§1). Eventos personalizados sugeridos: `quickplay_start`, `room_created`, `room_joined`, `match_start`, `set_completed`, `claim_failed` (el del "-1000 de aura"), `match_end`.
- [ ] **Performance Monitoring**: plugin `com.google.firebase.firebase-perf`. Traza personalizada `reveal_roundtrip` (desde que se toca la carta hasta que llega el evento), para medir la fluidez real.
- [ ] **App Distribution**: consola → App Distribution → crear el grupo `equipo-upb` con los correos de los testers. Desde Gradle o el CLI se sube cada build de dev.

---

## 13. Cloud Messaging (fase 2)

Todavía no hace falta. Cuando lleguen las invitaciones de amigos:
- [ ] La app pide el permiso `POST_NOTIFICATIONS` (Android 13+).
- [ ] Los tokens FCM se guardan en `users/{uid}/devices/{token}`.

---

## 14. Repositorio, CLI y despliegue

Estructura esperada en `backend/` (las funciones ya existen en `backend/functions/`):

```
backend/
├─ .firebaserc               alias dev / prod
├─ firebase.json             qué se despliega y emuladores
├─ database.rules.json       reglas RTDB (ARQUITECTURA §5.2)
├─ firestore.rules
├─ firestore.indexes.json
├─ hosting/public/           assetlinks, privacidad, eliminar-cuenta…
└─ functions/                motor + callables (TypeScript)
```

- [ ] Desde `backend/`, conectar los alias (elige `terna-dev` → alias `dev`; luego `terna-prod` → alias `prod`):
  ```bash
  firebase use --add
  ```
- [ ] `firebase.json` de referencia:
  ```json
  {
    "functions": [{ "source": "functions", "codebase": "default", "runtime": "nodejs22",
                    "predeploy": ["npm --prefix \"$RESOURCE_DIR\" run build"] }],
    "database": { "rules": "database.rules.json" },
    "firestore": { "rules": "firestore.rules", "indexes": "firestore.indexes.json" },
    "hosting": { "public": "hosting/public", "ignore": ["**/.*"],
                 "rewrites": [{ "source": "/sala/**", "destination": "/sala/index.html" }] },
    "emulators": {
      "auth": { "port": 9099 }, "functions": { "port": 5001 },
      "firestore": { "port": 8080 }, "database": { "port": 9000 },
      "hosting": { "port": 5000 }, "tasks": { "port": 9499 },
      "ui": { "enabled": true, "port": 4000 }, "singleProjectMode": true
    }
  }
  ```
- [ ] Desplegar en dev:
  ```bash
  firebase deploy --project dev
  ```
- [ ] Desplegar solo reglas (rápido, para iterar):
  ```bash
  firebase deploy --project dev --only database,firestore:rules
  ```
- [ ] Prod se despliega solo desde CI al crear un *tag* (ARQUITECTURA §9). Para CI, crea en Google Cloud una **cuenta de servicio de despliegue** con los roles "Administrador de Firebase" y "Usuario de cuenta de servicio", y guarda su clave JSON como secreto de GitHub (`FIREBASE_SERVICE_ACCOUNT_DEV` / `_PROD`). Nunca la subas al repositorio.

### Funciones públicas (verificar tras el primer despliegue de cada entorno)

Los callable se llaman desde la app con un token de **Firebase Auth**, no de Google IAM. La autenticación la valida el propio código (`request.auth`), así que el servicio de Cloud Run debe aceptar invocaciones de cualquiera (`allUsers` con el rol *Invocador de Cloud Run*). Firebase lo configura al **crear** una función, pero si el que despliega no puede cambiar permisos de IAM (o el primer despliegue falló a medias) las funciones quedan privadas y las actualizaciones posteriores no lo corrigen.

- [ ] Verifica con `curl` (sustituye el proyecto):
  ```bash
  curl -s -X POST -H "Content-Type: application/json" -d '{"data":{}}' https://us-central1-terna-dev-718c7.cloudfunctions.net/ping
  ```
  Correcto: JSON `{"error":{"message":"Inicia sesión para usar TERNA!","status":"UNAUTHENTICATED"}}`. Incorrecto: una página HTML «403 Forbidden».
- [ ] Si salen privadas: Consola de Google Cloud → **Cloud Run** (proyecto correcto) → marca las 8 funciones (`ping`, `createroom`, `joinroom`, `leaveroom`, `kickplayer`, `startmatch`, `gameaction`, `timeoutturn`) → **Permisos** → *Agregar principal* `allUsers`, rol **Invocador de Cloud Run** → Guardar. Hace falta un rol que pueda cambiar IAM (Propietario).

---

## 15. Emuladores (desarrollo diario)

- [ ] Levantar todo en local (UI en `http://localhost:4000`):
  ```bash
  firebase emulators:start --project dev --import=./emulator-data --export-on-exit
  ```
- [ ] Compila la app apuntando a los emuladores con la propiedad `useEmulators` (el flavor `dev` en debug ya permite HTTP hacia `10.0.2.2`):
  ```bash
  ./gradlew :app:assembleDevDebug -PuseEmulators=true
  ```
  Los SDK de Auth, Realtime Database, Firestore y Functions se conectan solos a `10.0.2.2` (el computador visto desde el emulador de Android). Desde un teléfono físico hay que cambiar `emulatorHost` en `FirebaseConfig` por la IP local del PC.
- [x] El tráfico HTTP sin cifrar está permitido **solo en debug** (`app/src/debug/AndroidManifest.xml`).
- [ ] Las reglas de seguridad tienen pruebas en `backend/rules-tests/`: levantan los emuladores de Firestore y Realtime Database con el proyecto `demo-terna`, que no necesita iniciar sesión. Corre esto **antes de publicar cualquier cambio de reglas**, desde `backend/rules-tests`:
  ```bash
  npm test              # reglas de seguridad
  npm run test:functions  # salas y partidas contra los emuladores
  ```

---

## 16. Google Play Console (lo que toca a Firebase)

- [ ] Crear la app con el paquete `com.ternagames.terna` y activar **Play App Signing**. Luego copiar sus SHA a Firebase (§4.1) y a `assetlinks.json` (§11).
- [ ] Vincular la **API de Play Integrity** (§9).
- [ ] Formulario de **Seguridad de los datos**. TERNA! recoge:
  - ID de usuario (Auth)
  - nombre y foto de perfil (Google/Facebook)
  - datos de uso y fallos (Analytics/Crashlytics)
- [ ] Cuestionario de **clasificación de contenido**: atención a la pregunta de "juegos de azar simulados" (ARQUITECTURA §11, punto 13).
- [ ] URL de política de privacidad y de borrado de cuenta (§11).

---

## 17. Checklist final por entorno

| # | Paso | dev | prod |
|---|---|:-:|:-:|
| 1 | Proyecto creado, equipo con permisos | ☐ | ☐ |
| 2 | Plan Blaze + presupuesto con alertas | ☐ | ☐ |
| 3 | App Android registrada + huellas SHA | ☐ | ☐ |
| 4 | `google-services.json` en su flavor | ☐ | ☐ |
| 5 | Auth: Anónimo, Google, Facebook | ☐ | ☐ |
| 6 | Realtime Database en `us-central1` (bloqueada) | ☐ | ☐ |
| 7 | Firestore Standard en `us-central1`/`nam5` (producción) | ☐ | ☐ |
| 8 | Functions activadas, Cloud Tasks API, roles IAM, limpieza de artefactos | ☐ | ☐ |
| 9 | App Check: Play Integrity + tokens de depuración | ☐ | ☐ |
| 10 | Remote Config publicado | ☐ | ☐ |
| 11 | Hosting: assetlinks, privacidad, eliminar cuenta | — | ☐ |
| 12 | Crashlytics, Performance, App Distribution | ☐ | ☐ |
| 13 | Alias del CLI y primer `firebase deploy` | ☐ | ☐ |
| 14 | Emuladores funcionando en cada PC | ☐ | — |
| 15 | Play Console: App Signing, Integrity, formularios | — | ☐ |

---

## Errores típicos

| Síntoma | Causa probable |
|---|---|
| Google Sign-In falla con código 10 / `DEVELOPER_ERROR` | Falta la huella SHA-1 de esa keystore o el `google-services.json` está desactualizado |
| Facebook: "Invalid key hash" | Falta el hash Base64 de esa keystore en Meta (§5.3) |
| Funciona en debug, falla la versión de Play | Falta la huella de **Play App Signing** (Firebase, Meta y assetlinks) |
| `PERMISSION_DENIED` en RTDB o Firestore | Esperado si el cliente intenta escribir partidas: solo escriben las funciones |
| Callable responde `unauthenticated` o App Check falla en el emulador | Falta registrar el token de depuración de App Check |
| En el teléfono, al crear sala: "Tu sesión expiró" (aunque haya sesión) y en el log de la función: *"The request was not authorized to invoke this service. The access token could not be verified"* | La función quedó **privada** en Cloud Run (falta `allUsers` → *Invocador de Cloud Run*) y Google rechaza el token de Firebase antes de que corra el código. El emulador no lo detecta. Ver «Funciones públicas» abajo |
| El despliegue falla por "API not enabled" | Abre el enlace del error y activa la API |
| Los enlaces `/sala/...` abren el navegador en vez de la app | `assetlinks.json` con SHA incorrecto, o falta `android:autoVerify="true"` en el *intent filter* |
