package com.ternagames.terna.feature.room.create

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ternagames.terna.core.designsystem.R as DesignR
import com.ternagames.terna.core.designsystem.audio.TernaSfx
import com.ternagames.terna.core.designsystem.audio.rememberTernaAudio
import com.ternagames.terna.core.designsystem.component.TernaBackButton
import com.ternagames.terna.core.designsystem.component.TernaBackdrop
import com.ternagames.terna.core.designsystem.component.TernaBackground
import com.ternagames.terna.core.designsystem.component.TernaPanel
import com.ternagames.terna.core.designsystem.component.TernaPrimaryButton
import com.ternagames.terna.core.designsystem.component.TernaSquareIconButton
import com.ternagames.terna.core.designsystem.component.TernaSwitch
import com.ternagames.terna.core.designsystem.theme.TernaColors
import com.ternagames.terna.core.designsystem.theme.TernaTheme
import com.ternagames.terna.core.designsystem.theme.TitleFont
import com.ternagames.terna.core.model.RoomLimits

@Composable
fun CreateRoomScreen(
    onBack: () -> Unit,
    onCreated: (roomId: String) -> Unit,
    viewModel: CreateRoomViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val audio = rememberTernaAudio()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.enterRoomId) {
        state.enterRoomId?.let { roomId ->
            audio.play(TernaSfx.RoomJoin)
            onCreated(roomId)
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    CreateRoomContent(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onDecrease = viewModel::decrease,
        onIncrease = viewModel::increase,
        onPrivateChange = viewModel::setPrivate,
        onCreate = viewModel::create,
    )
}

@Composable
internal fun CreateRoomContent(
    state: CreateRoomUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onPrivateChange: (Boolean) -> Unit,
    onCreate: () -> Unit,
) {
    TernaBackground(TernaBackdrop.Home) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            TernaBackButton(onBack)
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                TernaPanel(Modifier.weight(1f).fillMaxHeight(), contentPadding = 16.dp) {
                    Text("CREAR SALA", style = MaterialTheme.typography.headlineMedium.copy(fontFamily = TitleFont, fontSize = 28.sp), color = TernaColors.White)
                    Spacer(Modifier.height(10.dp))
                    Text("Número de Jugadores", style = MaterialTheme.typography.bodyLarge, color = TernaColors.White)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        TernaSquareIconButton(Icons.Rounded.Remove, "Menos jugadores", onDecrease, enabled = state.maxPlayers > RoomLimits.MIN_PLAYERS)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Groups, contentDescription = null, tint = TernaColors.TextMuted, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("${state.maxPlayers}", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 40.sp), color = TernaColors.White)
                        }
                        TernaSquareIconButton(Icons.Rounded.Add, "Más jugadores", onIncrease, enabled = state.maxPlayers < RoomLimits.MAX_PLAYERS)
                    }
                    Text(
                        "Mínimo ${RoomLimits.MIN_PLAYERS}, Máximo ${RoomLimits.MAX_PLAYERS}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TernaColors.TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Privacidad", style = MaterialTheme.typography.bodyLarge, color = TernaColors.White)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = TernaColors.TextMuted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Sala Privada", style = MaterialTheme.typography.bodyLarge, color = TernaColors.TextMuted)
                            Text(
                                if (state.isPrivate) "Solo entra quien tenga el código" else "Aparece en la lista de salas disponibles",
                                style = MaterialTheme.typography.bodySmall,
                                color = TernaColors.TextMuted.copy(alpha = 0.8f),
                            )
                        }
                        TernaSwitch(state.isPrivate, onPrivateChange)
                    }
                    Spacer(Modifier.weight(1f))
                    TernaPrimaryButton("¡CREAR PARTIDA!", onCreate, Modifier.fillMaxWidth(), loading = state.creating)
                }
                TernaPanel(Modifier.weight(1f).fillMaxHeight(), contentPadding = 12.dp) {
                    Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(10.dp))) {
                        Image(
                            painter = painterResource(DesignR.drawable.img_crear_partida),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding())
    }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun CreateRoomPreview() = TernaTheme {
    CreateRoomContent(CreateRoomUiState(), remember { SnackbarHostState() }, {}, {}, {}, {}, {})
}
