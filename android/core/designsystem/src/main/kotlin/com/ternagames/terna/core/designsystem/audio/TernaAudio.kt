package com.ternagames.terna.core.designsystem.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.util.Log
import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import com.ternagames.terna.core.designsystem.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/** Efectos de sonido de TERNA! precargados en [SoundPool] para latencia mínima. */
enum class TernaSfx(@RawRes val resId: Int, val baseVolume: Float = 1.0f) {
    /** Toque cálido en madera de chonta para botones y navegación. */
    UiClick(R.raw.sfx_ui_click_madera, 0.75f),

    /** Acorde de tiple al crear sala, unirse o cuando entra un jugador. */
    RoomJoin(R.raw.sfx_sala_crear_unirse, 0.90f),

    /** Rasgueo festivo y quena para el modal "¡Iniciando Partida!". */
    MatchStart(R.raw.sfx_partida_iniciando, 0.95f),

    /** Fricción de naipe y toque en mesa al voltear/revelar una carta. */
    CardFlip(R.raw.sfx_carta_voltear, 0.90f),

    /** Arpegio brillante cuando la carta revelada o pedida coincide. */
    CardMatch(R.raw.sfx_carta_acierto, 0.90f),

    /** Cuerda grave descendente al fallar y perder el turno. */
    TurnFail(R.raw.sfx_turno_error, 0.85f),

    /** Caída cromática y bombo seco para "-1000 de aura" al fallar deducción. */
    AuraLoss(R.raw.sfx_aura_menos_1000, 0.95f),

    /** Acorde triunfal de tiple y quena al completar una Terna. */
    TernaComplete(R.raw.sfx_hacer_terna, 0.95f),

    /** Celebración de tres tríos al ganar la partida normalmente (3 ternas). */
    VictoryNormal(R.raw.sfx_victoria_normal, 1.0f),

    /** Fanfarria épica El Dorado al ganar con el Jackpot 777. */
    VictoryJackpot(R.raw.sfx_victoria_jackpot_777, 1.0f),
}

data class AudioSettings(
    val musicVolume: Float = DEFAULT_MUSIC_VOLUME,
    val musicEnabled: Boolean = true,
    val sfxEnabled: Boolean = true,
) {
    val musicPercent: Int get() = if (musicEnabled) (musicVolume * 100f).roundToInt().coerceIn(0, 100) else 0
    val effectiveMusicVolume: Float get() = if (musicEnabled) musicVolume.coerceIn(0f, 1f) else 0f

    companion object {
        const val DEFAULT_MUSIC_VOLUME = 0.70f
        const val VOLUME_STEP = 0.10f
    }
}

/**
 * Gestor central de música de fondo (Home, Crear/Unirse a Sala, Sala de Espera) y efectos de sonido.
 * Guarda las preferencias de volumen en `SharedPreferences` para que persistan entre sesiones.
 */
class TernaAudio private constructor(context: Context?) {

    private val appContext = context?.applicationContext
    private val prefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AudioSettings> = _settings.asStateFlow()

    private var soundPool: SoundPool? = null
    private val soundIds = ConcurrentHashMap<TernaSfx, Int>()
    private val loadedSampleIds = ConcurrentHashMap.newKeySet<Int>()

    private var bgmPlayer: MediaPlayer? = null
    private var inMenuScene: Boolean = false
    private var inForeground: Boolean = true

    init {
        if (appContext != null) {
            initSoundPool(appContext)
        }
    }

    private fun loadSettings(): AudioSettings {
        val p = prefs ?: return AudioSettings()
        return AudioSettings(
            musicVolume = p.getFloat(KEY_MUSIC_VOLUME, AudioSettings.DEFAULT_MUSIC_VOLUME).coerceIn(0f, 1f),
            musicEnabled = p.getBoolean(KEY_MUSIC_ENABLED, true),
            sfxEnabled = p.getBoolean(KEY_SFX_ENABLED, true),
        )
    }

    private fun saveSettings(s: AudioSettings) {
        prefs?.edit()
            ?.putFloat(KEY_MUSIC_VOLUME, s.musicVolume)
            ?.putBoolean(KEY_MUSIC_ENABLED, s.musicEnabled)
            ?.putBoolean(KEY_SFX_ENABLED, s.sfxEnabled)
            ?.apply()
    }

    private fun initSoundPool(context: Context) {
        runCatching {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val pool = SoundPool.Builder()
                .setMaxStreams(6)
                .setAudioAttributes(attrs)
                .build()
            pool.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) loadedSampleIds.add(sampleId)
            }
            TernaSfx.entries.forEach { sfx ->
                val id = pool.load(context, sfx.resId, 1)
                soundIds[sfx] = id
            }
            soundPool = pool
        }.onFailure { e ->
            Log.w(TAG, "No se pudo inicializar SoundPool", e)
        }
    }

    /** Reproduce un efecto de sonido si los efectos están habilitados. */
    fun play(sfx: TernaSfx) {
        if (!_settings.value.sfxEnabled) return
        val pool = soundPool ?: return
        val id = soundIds[sfx] ?: return
        if (id in loadedSampleIds) {
            val vol = sfx.baseVolume.coerceIn(0f, 1f)
            pool.play(id, vol, vol, 1, 0, 1.0f)
        }
    }

    /** Actualiza si el usuario está en una pantalla con música de fondo (Home, Salas, Sala de espera). */
    @Synchronized
    fun setMenuMusicActive(active: Boolean) {
        if (inMenuScene == active) return
        inMenuScene = active
        syncBgmPlayback()
    }

    /** Llamado desde el ciclo de vida de la actividad para pausar la música al salir de la app. */
    @Synchronized
    fun setAppForeground(foreground: Boolean) {
        if (inForeground == foreground) return
        inForeground = foreground
        syncBgmPlayback()
    }

    /** Cambia el volumen de la música (0.0 a 1.0) en tiempo real. */
    @Synchronized
    fun setMusicVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _settings.update { current ->
            val next = current.copy(
                musicVolume = clamped,
                musicEnabled = if (clamped > 0f && !current.musicEnabled) true else current.musicEnabled,
            )
            saveSettings(next)
            next
        }
        syncBgmPlayback()
    }

    /** Sube o baja el volumen de la música en pasos (p. ej. +0.10f o -0.10f). */
    @Synchronized
    fun adjustMusicVolume(delta: Float) {
        val current = _settings.value
        val base = if (current.musicEnabled) current.musicVolume else 0f
        val nextVol = ((base + delta) * 10f).roundToInt() / 10f
        val clamped = nextVol.coerceIn(0f, 1f)
        _settings.update {
            val next = it.copy(
                musicVolume = if (clamped > 0f) clamped else it.musicVolume,
                musicEnabled = clamped > 0f,
            )
            saveSettings(next)
            next
        }
        syncBgmPlayback()
    }

    @Synchronized
    fun setMusicEnabled(enabled: Boolean) {
        _settings.update {
            val next = it.copy(
                musicEnabled = enabled,
                musicVolume = if (enabled && it.musicVolume <= 0.01f) AudioSettings.DEFAULT_MUSIC_VOLUME else it.musicVolume,
            )
            saveSettings(next)
            next
        }
        syncBgmPlayback()
    }

    @Synchronized
    fun setSfxEnabled(enabled: Boolean) {
        _settings.update {
            val next = it.copy(sfxEnabled = enabled)
            saveSettings(next)
            next
        }
    }

    @Synchronized
    private fun syncBgmPlayback() {
        val context = appContext ?: return
        val s = _settings.value
        val vol = s.effectiveMusicVolume
        val shouldPlay = inMenuScene && inForeground && vol > 0f

        if (!shouldPlay) {
            runCatching {
                if (bgmPlayer?.isPlaying == true) bgmPlayer?.pause()
            }
            return
        }

        runCatching {
            val player = bgmPlayer ?: MediaPlayer.create(context, R.raw.bgm_menu)?.apply {
                isLooping = true
            }?.also { bgmPlayer = it } ?: return

            player.setVolume(vol, vol)
            if (!player.isPlaying) {
                player.start()
            }
        }.onFailure { e ->
            Log.w(TAG, "No se pudo reproducir la música de fondo", e)
        }
    }

    companion object {
        private const val TAG = "TernaAudio"
        private const val PREFS_NAME = "terna_audio_prefs"
        private const val KEY_MUSIC_VOLUME = "music_volume"
        private const val KEY_MUSIC_ENABLED = "music_enabled"
        private const val KEY_SFX_ENABLED = "sfx_enabled"

        @Volatile
        private var instance: TernaAudio? = null
        private val previewNoOp by lazy { TernaAudio(null) }

        fun get(context: Context): TernaAudio {
            return instance ?: synchronized(this) {
                instance ?: TernaAudio(context.applicationContext).also { instance = it }
            }
        }

        internal fun noOp(): TernaAudio = previewNoOp
    }
}

/** Obtiene la instancia de [TernaAudio] segura para Previews de Compose. */
@Composable
fun rememberTernaAudio(): TernaAudio {
    val inspection = LocalInspectionMode.current
    val context = LocalContext.current
    return remember(inspection, context) {
        if (inspection) TernaAudio.noOp() else TernaAudio.get(context)
    }
}
