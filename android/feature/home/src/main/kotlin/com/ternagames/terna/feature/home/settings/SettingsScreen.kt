package com.ternagames.terna.feature.home.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ternagames.terna.core.designsystem.R as DesignR
import com.ternagames.terna.core.designsystem.audio.AudioSettings
import com.ternagames.terna.core.designsystem.audio.rememberTernaAudio
import com.ternagames.terna.core.designsystem.component.TernaChunkyButton
import com.ternagames.terna.core.designsystem.component.TernaPanel
import com.ternagames.terna.core.designsystem.component.TernaProviderButton
import com.ternagames.terna.core.designsystem.component.TernaSquareIconButton
import com.ternagames.terna.core.designsystem.component.TernaSwitch
import com.ternagames.terna.core.designsystem.theme.TernaColors
import com.ternagames.terna.core.designsystem.theme.TernaTheme
import com.ternagames.terna.core.designsystem.theme.TitleFont
import com.ternagames.terna.feature.home.ui.SubScreenScaffold

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenCredits: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val audio = rememberTernaAudio()
    val audioSettings by audio.settings.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val snackbar = remember { SnackbarHostState() }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.signedIn) { if (!state.signedIn) onSignedOut() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    SettingsContent(
        state = state,
        audioSettings = audioSettings,
        snackbar = snackbar,
        onBack = onBack,
        onOpenCredits = onOpenCredits,
        onMusicVolumeChange = audio::setMusicVolume,
        onDecreaseMusicVolume = { audio.adjustMusicVolume(-AudioSettings.VOLUME_STEP) },
        onIncreaseMusicVolume = { audio.adjustMusicVolume(AudioSettings.VOLUME_STEP) },
        onMusicEnabledChange = audio::setMusicEnabled,
        onSfxEnabledChange = audio::setSfxEnabled,
        onLinkGoogle = { activity?.let(viewModel::linkGoogle) },
        onSignOutClick = { if (state.guest) confirmSignOut = true else viewModel.signOut() },
    )

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            icon = { Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null) },
            title = { Text("¿Cerrar sesión de invitado?") },
            text = { Text("Las cuentas de invitado no se pueden recuperar: perderás tu nivel y tus estadísticas. Vincula Google antes si quieres conservarlos.") },
            confirmButton = {
                TextButton(onClick = { confirmSignOut = false; viewModel.signOut() }) {
                    Text("Cerrar sesión", color = TernaColors.Danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancelar") } },
            containerColor = TernaColors.Field,
        )
    }
}

@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    audioSettings: AudioSettings,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpenCredits: () -> Unit,
    onMusicVolumeChange: (Float) -> Unit,
    onDecreaseMusicVolume: () -> Unit,
    onIncreaseMusicVolume: () -> Unit,
    onMusicEnabledChange: (Boolean) -> Unit,
    onSfxEnabledChange: (Boolean) -> Unit,
    onLinkGoogle: () -> Unit,
    onSignOutClick: () -> Unit,
) {
    SubScreenScaffold(
        title = "AJUSTES",
        onBack = onBack,
        cornerActions = listOf("CRÉDITOS" to onOpenCredits),
        snackbar = snackbar,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize(),
        ) {
            // Panel izquierdo: Sonido y Música (volumen de banda sonora + efectos)
            TernaPanel(Modifier.weight(1.15f).fillMaxHeight(), contentPadding = 14.dp) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (audioSettings.musicPercent > 0) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                            contentDescription = null,
                            tint = TernaColors.Yellow,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "SONIDO Y MÚSICA",
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = TitleFont, fontSize = 20.sp),
                            color = TernaColors.White,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${audioSettings.musicPercent}%",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = TernaColors.Yellow,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(TernaColors.PanelVariant)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }

                    // Control de volumen de la música (subir / bajar + barra deslizante)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = TernaColors.TextMuted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Volumen de la Música", style = MaterialTheme.typography.bodyLarge, color = TernaColors.White)
                            Text(
                                "Banda sonora en menú y sala de espera",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = TernaColors.TextMuted,
                            )
                        }
                        TernaSwitch(
                            checked = audioSettings.musicEnabled,
                            onCheckedChange = onMusicEnabledChange,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TernaSquareIconButton(
                            icon = Icons.Rounded.Remove,
                            description = "Bajar volumen de la música",
                            onClick = onDecreaseMusicVolume,
                            enabled = audioSettings.musicPercent > 0,
                            modifier = Modifier.size(40.dp),
                        )
                        Slider(
                            value = audioSettings.effectiveMusicVolume,
                            onValueChange = onMusicVolumeChange,
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = TernaColors.Yellow,
                                activeTrackColor = TernaColors.Yellow,
                                inactiveTrackColor = TernaColors.PanelVariant,
                            ),
                            modifier = Modifier.weight(1f).height(32.dp),
                        )
                        TernaSquareIconButton(
                            icon = Icons.Rounded.Add,
                            description = "Subir volumen de la música",
                            onClick = onIncreaseMusicVolume,
                            enabled = audioSettings.musicPercent < 100,
                            modifier = Modifier.size(40.dp),
                        )
                    }

                    HorizontalDivider(color = TernaColors.PanelVariant, thickness = 1.dp)

                    // Efectos de sonido (botones, naipes, ternas y victorias)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = TernaColors.TextMuted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Efectos de Sonido", style = MaterialTheme.typography.bodyLarge, color = TernaColors.White)
                            Text(
                                "Naipes, botones, ternas y victorias",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = TernaColors.TextMuted,
                            )
                        }
                        TernaSwitch(
                            checked = audioSettings.sfxEnabled,
                            onCheckedChange = onSfxEnabledChange,
                        )
                    }
                }
            }

            // Panel derecho: Cuenta y Sesión
            TernaPanel(Modifier.weight(0.85f).fillMaxHeight(), contentPadding = 14.dp) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        "CUENTA",
                        style = MaterialTheme.typography.titleLarge.copy(fontFamily = TitleFont, fontSize = 20.sp),
                        color = TernaColors.White,
                    )
                    Text(
                        if (state.guest) "Estás jugando como invitado. Vincula tu cuenta de Google para no perder tu progreso."
                        else "Tu progreso está guardado en tu cuenta.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TernaColors.White,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.weight(1f))
                    if (state.guest) {
                        TernaProviderButton(
                            text = "Vincular con Google",
                            logo = painterResource(DesignR.drawable.ic_google),
                            onClick = onLinkGoogle,
                            face = TernaColors.White,
                            base = TernaColors.WhiteShade,
                            content = Color(0xFF1F1F1F),
                            loading = state.linking,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    TernaChunkyButton(
                        onClick = onSignOutClick,
                        face = TernaColors.PanelVariant,
                        base = Color(0xFF15100E),
                        content = TernaColors.White,
                        enabled = !state.linking,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("CERRAR SESIÓN", style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
    }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun SettingsPreview() = TernaTheme {
    SettingsContent(
        state = SettingsUiState(guest = true),
        audioSettings = AudioSettings(musicVolume = 0.7f, musicEnabled = true, sfxEnabled = true),
        snackbar = remember { SnackbarHostState() },
        onBack = {},
        onOpenCredits = {},
        onMusicVolumeChange = {},
        onDecreaseMusicVolume = {},
        onIncreaseMusicVolume = {},
        onMusicEnabledChange = {},
        onSfxEnabledChange = {},
        onLinkGoogle = {},
        onSignOutClick = {},
    )
}
