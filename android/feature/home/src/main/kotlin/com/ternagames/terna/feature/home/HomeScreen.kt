package com.ternagames.terna.feature.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ternagames.terna.core.designsystem.R as DesignR
import com.ternagames.terna.core.designsystem.component.PlayerAvatar
import com.ternagames.terna.core.designsystem.component.TernaBackdrop
import com.ternagames.terna.core.designsystem.component.TernaBackground
import com.ternagames.terna.core.designsystem.component.TernaPanel
import com.ternagames.terna.core.designsystem.component.TernaPrimaryButton
import com.ternagames.terna.core.designsystem.component.TernaRoundIconButton
import com.ternagames.terna.core.designsystem.theme.TernaColors
import com.ternagames.terna.core.designsystem.theme.TernaTheme
import com.ternagames.terna.core.designsystem.theme.TitleFont
import com.ternagames.terna.core.model.Avatars
import com.ternagames.terna.core.model.PlayerProfile
import kotlinx.coroutines.launch

private const val COMING_SOON = "La partida rápida llega muy pronto. Por ahora crea una sala o únete con un código."

@Composable
fun HomeScreen(
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCredits: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) { if (state == HomeUiState.SignedOut) onSignedOut() }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val comingSoon: () -> Unit = { scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(COMING_SOON) } }

    HomeContent(
        profile = (state as? HomeUiState.Ready)?.profile,
        snackbar = snackbar,
        onCreateRoom = onCreateRoom,
        onJoinRoom = onJoinRoom,
        onQuickPlay = comingSoon,
        onOpenSettings = onOpenSettings,
        onOpenCredits = onOpenCredits,
    )
}

@Composable
internal fun HomeContent(
    profile: PlayerProfile?,
    snackbar: SnackbarHostState,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onQuickPlay: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCredits: () -> Unit,
) {
    TernaBackground(TernaBackdrop.Home) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            // Barra superior: jugador · título · ajustes y créditos
            Box(Modifier.fillMaxWidth()) {
                PlayerChip(profile, Modifier.align(Alignment.CenterStart))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.Center)) {
                    Text("TERNA", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp), color = TernaColors.White)
                    Text("MENÚ PRINCIPAL", style = MaterialTheme.typography.labelSmall, color = TernaColors.TextMuted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.align(Alignment.CenterEnd)) {
                    TernaRoundIconButton(Icons.Outlined.Settings, "AJUSTES", onOpenSettings)
                    TernaRoundIconButton(Icons.Outlined.Info, "CRÉDITOS", onOpenCredits)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
                ModeCard(Modifier.weight(1f)) {
                    CardTitle("CREAR SALA")
                    CardImage(DesignR.drawable.img_crear_sala, Modifier.weight(1f))
                    CardText("Crea una partida e invita a tus amigos")
                    TernaPrimaryButton("CREAR", onCreateRoom, Modifier.fillMaxWidth())
                }
                ModeCard(Modifier.weight(1f)) {
                    CardImage(DesignR.drawable.img_unirse_sala, Modifier.weight(1f))
                    CardTitle("UNIRSE A SALA")
                    CardText("Ingresa el código de una sala activa")
                    TernaPrimaryButton("UNIRSE", onJoinRoom, Modifier.fillMaxWidth())
                }
                ModeCard(Modifier.weight(1f)) {
                    CardTitle("PARTIDA RÁPIDA")
                    CardText("Juega al instante")
                    CardImage(DesignR.drawable.img_partida_rapida, Modifier.weight(1f))
                    TernaPrimaryButton("¡JUGAR!", onQuickPlay, Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow)
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding())
    }
}

@Composable
private fun PlayerChip(profile: PlayerProfile?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .width(220.dp)
            .clip(shape)
            .background(TernaColors.Panel)
            .border(1.5.dp, TernaColors.Yellow, shape)
            .padding(6.dp),
    ) {
        PlayerAvatar(profile?.avatar ?: Avatars.DEFAULT, 44.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                profile?.displayName ?: "Jugador",
                style = MaterialTheme.typography.titleMedium,
                color = TernaColors.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Nivel ${profile?.level ?: 1}",
                style = MaterialTheme.typography.labelSmall,
                color = TernaColors.Yellow,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(TernaColors.PanelVariant)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun ModeCard(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    TernaPanel(modifier.fillMaxHeight(), contentPadding = 12.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium.copy(fontFamily = TitleFont, fontSize = 22.sp), color = TernaColors.White)
}

@Composable
private fun CardText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = TernaColors.TextMuted, maxLines = 2)
}

@Composable
private fun CardImage(@DrawableRes image: Int, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(image),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp)),
    )
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun HomePreview() = TernaTheme {
    HomeContent(
        profile = PlayerProfile("u1", "Guest001", Avatars.DEFAULT, anonymous = true, level = 1, xp = 0, coins = 0),
        snackbar = remember { SnackbarHostState() },
        onCreateRoom = {}, onJoinRoom = {}, onQuickPlay = {}, onOpenSettings = {}, onOpenCredits = {},
    )
}
