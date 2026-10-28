package com.ternagames.terna.feature.room.join

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ternagames.terna.core.designsystem.component.TernaBackButton
import com.ternagames.terna.core.designsystem.component.TernaBackdrop
import com.ternagames.terna.core.designsystem.component.TernaBackground
import com.ternagames.terna.core.designsystem.component.TernaPanel
import com.ternagames.terna.core.designsystem.component.TernaPrimaryButton
import com.ternagames.terna.core.designsystem.component.TernaTextField
import com.ternagames.terna.core.designsystem.theme.TernaColors
import com.ternagames.terna.core.designsystem.theme.TernaTheme
import com.ternagames.terna.core.designsystem.theme.TitleFont
import com.ternagames.terna.core.model.OpenRoom

@Composable
fun JoinRoomScreen(
    onBack: () -> Unit,
    onJoined: (roomId: String) -> Unit,
    viewModel: JoinRoomViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.enterRoomId) { state.enterRoomId?.let(onJoined) }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    JoinRoomContent(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onCodeChange = viewModel::onCodeChange,
        onSearch = viewModel::joinByCode,
        onJoin = viewModel::joinOpenRoom,
    )
}

@Composable
internal fun JoinRoomContent(
    state: JoinRoomUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onCodeChange: (String) -> Unit,
    onSearch: () -> Unit,
    onJoin: (OpenRoom) -> Unit,
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
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Entrar con código
                TernaPanel(Modifier.weight(1f).fillMaxHeight(), contentPadding = 16.dp) {
                    Text("UNIRSE A SALA", style = MaterialTheme.typography.headlineMedium.copy(fontFamily = TitleFont, fontSize = 28.sp), color = TernaColors.White)
                    Text(
                        "Ingresa un código o elige una sala disponible.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TernaColors.TextMuted,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )
                    TernaTextField(
                        value = state.code,
                        onValueChange = onCodeChange,
                        placeholder = "Ingresa el código de la sala…",
                        capitalizeAll = true,
                        imeAction = ImeAction.Search,
                        onImeAction = onSearch,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    TernaPrimaryButton(
                        "BUSCAR",
                        onSearch,
                        Modifier.fillMaxWidth(),
                        enabled = state.canSearch,
                        loading = state.joining == JoinRoomUiState.BY_CODE,
                    )
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.VpnKey, contentDescription = null, tint = TernaColors.TextMuted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("El anfitrión comparte el código de la sala.", style = MaterialTheme.typography.bodySmall, color = TernaColors.TextMuted)
                    }
                }
                // Salas públicas
                TernaPanel(Modifier.weight(1f).fillMaxHeight(), contentPadding = 16.dp) {
                    Text("Salas disponibles", style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), color = TernaColors.White)
                    Text(
                        "Encuentra tu grupo y entra a jugar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TernaColors.TextMuted,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                    )
                    OpenRoomList(state, onJoin, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding())
    }
}

@Composable
private fun OpenRoomList(state: JoinRoomUiState, onJoin: (OpenRoom) -> Unit, modifier: Modifier = Modifier) {
    val rooms = state.rooms
    when {
        rooms == null -> Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = TernaColors.Yellow, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
        }
        rooms.isEmpty() -> Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                "Todavía no hay salas públicas.\nCrea una e invita a tus amigos.",
                style = MaterialTheme.typography.bodyMedium,
                color = TernaColors.TextMuted,
                textAlign = TextAlign.Center,
            )
        }
        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
            items(rooms, key = { it.id }) { room ->
                OpenRoomRow(room, joining = state.joining == room.id, enabled = state.joining == null, onJoin = { onJoin(room) })
            }
        }
    }
}

@Composable
private fun OpenRoomRow(room: OpenRoom, joining: Boolean, enabled: Boolean, onJoin: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TernaColors.PanelVariant)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(36.dp).clip(CircleShape).background(TernaColors.Yellow.copy(alpha = 0.15f)),
        ) {
            Icon(Icons.Rounded.Person, contentDescription = null, tint = TernaColors.Yellow, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(room.name, style = MaterialTheme.typography.titleMedium, color = TernaColors.White, maxLines = 1)
            Text("${room.players}/${room.max} Jugadores", style = MaterialTheme.typography.bodySmall, color = TernaColors.TextMuted)
        }
        TernaPrimaryButton("Unirse", onJoin, Modifier.width(96.dp), enabled = enabled, loading = joining)
    }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun JoinRoomPreview() = TernaTheme {
    JoinRoomContent(
        JoinRoomUiState(
            code = "TRN-7K",
            rooms = listOf(OpenRoom("1", "Sala de Miguel", "Miguel", 3, 6, "TRN-AAAA"), OpenRoom("2", "Sala de Parra", "Parra", 2, 6, "TRN-BBBB")),
        ),
        remember { SnackbarHostState() }, {}, {}, {}, {},
    )
}
