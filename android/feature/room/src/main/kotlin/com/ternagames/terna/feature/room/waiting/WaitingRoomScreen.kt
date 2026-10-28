package com.ternagames.terna.feature.room.waiting

import android.content.ClipData
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ternagames.terna.core.designsystem.R as DesignR
import com.ternagames.terna.core.designsystem.audio.TernaSfx
import com.ternagames.terna.core.designsystem.audio.rememberTernaAudio
import com.ternagames.terna.core.designsystem.component.DialogTitle
import com.ternagames.terna.core.designsystem.component.TernaBackdrop
import com.ternagames.terna.core.designsystem.component.TernaBackground
import com.ternagames.terna.core.designsystem.component.TernaDialog
import com.ternagames.terna.core.designsystem.component.TernaOutlineButton
import com.ternagames.terna.core.designsystem.component.TernaPanel
import com.ternagames.terna.core.designsystem.component.TernaPrimaryButton
import com.ternagames.terna.core.designsystem.theme.TernaColors
import com.ternagames.terna.core.designsystem.theme.TernaTheme
import com.ternagames.terna.core.designsystem.theme.TitleFont
import com.ternagames.terna.core.model.Room
import com.ternagames.terna.core.model.RoomLimits
import com.ternagames.terna.core.model.RoomMember
import com.ternagames.terna.core.model.RoomStatus
import com.ternagames.terna.core.model.RoomType
import kotlinx.coroutines.launch

@Composable
fun WaitingRoomScreen(
    onExit: () -> Unit,
    onMatchStarted: (matchId: String) -> Unit,
    viewModel: WaitingRoomViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val audio = rememberTernaAudio()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var prevMemberCount by remember { mutableIntStateOf(-1) }
    var startSoundPlayed by remember { mutableStateOf(false) }

    BackHandler(enabled = state.exit == null) { viewModel.leave() }

    // Sonido cálido cuando otro jugador se une a la sala de espera.
    val memberCount = state.room?.members?.size ?: -1
    LaunchedEffect(memberCount) {
        if (memberCount > 0) {
            if (prevMemberCount in 1 until memberCount) {
                audio.play(TernaSfx.RoomJoin)
            }
            prevMemberCount = memberCount
        }
    }

    // Sonido festivo al iniciar la partida.
    LaunchedEffect(state.starting) {
        if (state.starting && !startSoundPlayed) {
            startSoundPlayed = true
            audio.play(TernaSfx.MatchStart)
        }
    }

    LaunchedEffect(state.exit) {
        val exit = state.exit ?: return@LaunchedEffect
        exit.message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        onExit()
    }
    LaunchedEffect(state.matchId) {
        state.matchId?.let { matchId ->
            if (!startSoundPlayed) {
                startSoundPlayed = true
                audio.play(TernaSfx.MatchStart)
            }
            onMatchStarted(matchId)
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    WaitingRoomContent(
        state = state,
        snackbar = snackbar,
        onLeave = viewModel::leave,
        onKick = viewModel::kick,
        onStart = viewModel::start,
    )
}

@Composable
internal fun WaitingRoomContent(
    state: WaitingRoomUiState,
    snackbar: SnackbarHostState,
    onLeave: () -> Unit,
    onKick: (String) -> Unit,
    onStart: () -> Unit,
) {
    TernaBackground(TernaBackdrop.Lobby, scrim = NoScrim) {
        val room = state.room
        if (room == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TernaColors.Yellow)
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Header(room, onLeave, enabled = !state.busy)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
                    PlayersPanel(room, state, onKick, Modifier.weight(1.1f).fillMaxHeight())
                    ActionPanel(room, state, onStart, Modifier.weight(1f).fillMaxHeight())
                }
                Footer(room)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding())
    }

    if (state.starting) {
        TernaDialog(onDismiss = {}, dismissible = false) {
            DialogTitle("¡Iniciando Partida!")
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(painterResource(DesignR.drawable.card_back), contentDescription = null, modifier = Modifier.height(72.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Preparando la mesa de juego…",
                style = MaterialTheme.typography.bodyMedium,
                color = TernaColors.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** El fondo azul de la sala de espera ya es oscuro: no necesita velo. */
private val NoScrim = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))

// ───────────── Encabezado: abandonar · título · código ─────────────

@Composable
private fun Header(room: Room, onLeave: () -> Unit, enabled: Boolean) {
    Box(Modifier.fillMaxWidth().height(52.dp)) {
        TernaOutlineButton("ABANDONAR", onLeave, Modifier.align(Alignment.CenterStart), enabled = enabled, icon = Icons.AutoMirrored.Rounded.ArrowBack)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.Center)) {
            Text("SALA DE ESPERA / LOBBY", style = MaterialTheme.typography.labelSmall, color = TernaColors.TextMuted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    room.name.uppercase(),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 26.sp),
                    color = TernaColors.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                PrivacyBadge(room.isPrivate)
            }
        }
        if (room.isPrivate) CodeChip(room.code, Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun PrivacyBadge(isPrivate: Boolean) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(shape).border(1.dp, TernaColors.Yellow, shape).padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        if (isPrivate) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = TernaColors.Yellow, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(3.dp))
        }
        Text(if (isPrivate) "PRIVADA" else "PÚBLICA", style = MaterialTheme.typography.labelSmall, color = TernaColors.Yellow)
    }
}

@Composable
private fun CodeChip(code: String, modifier: Modifier = Modifier) {
    val audio = rememberTernaAudio()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val shape = RoundedCornerShape(10.dp)
    Column(horizontalAlignment = Alignment.End, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(shape)
                .background(TernaColors.Field.copy(alpha = 0.85f))
                .border(1.5.dp, TernaColors.Yellow, shape)
                .clickable(onClickLabel = "Copiar código") {
                    audio.play(TernaSfx.UiClick)
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Código de sala", code))) }
                    Toast.makeText(context, "Código copiado", Toast.LENGTH_SHORT).show()
                }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text("CÓDIGO: $code", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = TernaColors.Yellow)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = TernaColors.Yellow, modifier = Modifier.size(16.dp))
        }
        Text("Comparte este código para que se unan", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = TernaColors.TextMuted)
    }
}

// ───────────── Panel izquierdo: jugadores ─────────────

@Composable
private fun PlayersPanel(room: Room, state: WaitingRoomUiState, onKick: (String) -> Unit, modifier: Modifier = Modifier) {
    TernaPanel(modifier, contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Jugadores Conectados - ${room.members.size}/${room.maxPlayers}",
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = TitleFont, fontSize = 17.sp),
                color = TernaColors.White,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(room.maxPlayers) { i ->
                    Box(
                        Modifier
                            .size(width = 6.dp, height = 14.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (i < room.members.size) TernaColors.Yellow else TernaColors.PanelVariant),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            room.members.forEach { member ->
                PlayerSlot(
                    member = member,
                    isYou = member.uid == state.myUid,
                    canKick = state.isHost && !member.host && !state.busy,
                    onKick = { onKick(member.uid) },
                )
            }
            repeat(room.freeSlots) { PlayerSlot(member = null, isYou = false, canKick = false, onKick = {}) }
        }
        if (room.freeSlots > 0) {
            Text(
                "¡Hay sitio para más! Invita a tus amigos con el código de la sala.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                color = TernaColors.TextMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun PlayerSlot(member: RoomMember?, isYou: Boolean, canKick: Boolean, onKick: () -> Unit) {
    val audio = rememberTernaAudio()
    val shape = RoundedCornerShape(10.dp)
    val occupied = member != null
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(shape)
            .background(if (occupied) TernaColors.PanelVariant else TernaColors.Field.copy(alpha = 0.5f))
            .border(1.dp, TernaColors.Yellow.copy(alpha = if (occupied) 1f else 0.45f), shape)
            .padding(horizontal = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x1FFFFFFF)),
        ) {
            when {
                member == null -> Icon(Icons.Rounded.Add, contentDescription = null, tint = TernaColors.TextMuted, modifier = Modifier.size(16.dp))
                member.host -> Text("👑", fontSize = 14.sp)
                else -> Icon(Icons.Rounded.Person, contentDescription = null, tint = TernaColors.Yellow, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            when {
                member == null -> "Esperando jugador…"
                isYou -> "${member.name} (Tú)"
                else -> member.name
            },
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
            color = if (occupied) TernaColors.White else TernaColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (member?.host == true) {
            Text(
                "[ANFITRIÓN]",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = TernaColors.Yellow,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0x33FDE047)).padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
        if (canKick) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(TernaColors.Danger)
                    .clickable(
                        onClickLabel = "Sacar a ${member?.name}",
                        onClick = {
                            audio.play(TernaSfx.UiClick)
                            onKick()
                        },
                    ),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = null, tint = TernaColors.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ───────────── Panel derecho: ajustes y acción ─────────────

@Composable
private fun ActionPanel(room: Room, state: WaitingRoomUiState, onStart: () -> Unit, modifier: Modifier = Modifier) {
    TernaPanel(modifier, contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("AJUSTES Y ACCIÓN", style = MaterialTheme.typography.labelSmall, color = TernaColors.TextMuted, modifier = Modifier.weight(1f))
            if (room.canStart) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = TernaColors.Yellow, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text("TODO LISTO", style = MaterialTheme.typography.labelSmall, color = TernaColors.Yellow)
            } else {
                Text("FALTAN JUGADORES", style = MaterialTheme.typography.labelSmall, color = TernaColors.TextMuted)
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(10.dp))) {
            Image(painterResource(DesignR.drawable.img_sala_espera), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(6.dp))
        val missing = RoomLimits.MIN_PLAYERS - room.members.size
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, TernaColors.Yellow.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = if (room.canStart) TernaColors.Yellow else TernaColors.TextMuted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (room.canStart) "Mínimo ${RoomLimits.MIN_PLAYERS} jugadores para iniciar. ¡Todo listo para la partida!"
                else "Mínimo ${RoomLimits.MIN_PLAYERS} jugadores para iniciar. Falta${if (missing == 1) "" else "n"} $missing.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = TernaColors.White,
            )
        }
        Spacer(Modifier.height(6.dp))
        if (state.isHost) {
            TernaPrimaryButton(
                "¡INICIAR PARTIDA!",
                onStart,
                Modifier.fillMaxWidth(),
                enabled = room.canStart && room.status == RoomStatus.Waiting && !state.busy,
                loading = state.starting,
                icon = Icons.AutoMirrored.Rounded.ArrowForward,
            )
        } else {
            Text(
                "Esperando a que el anfitrión inicie la partida",
                style = MaterialTheme.typography.bodySmall,
                color = TernaColors.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            )
        }
    }
}

// ───────────── Pie ─────────────

@Composable
private fun Footer(room: Room) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(20.dp)) {
        Text("● CONEXIÓN ESTABLE", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = TernaColors.TextMuted, modifier = Modifier.weight(1f))
        Text(
            "Una buena partida empieza con buena compañía.",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            color = TernaColors.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Text(
            "SALA ${if (room.isPrivate) "PRIVADA" else "PÚBLICA"} · ${room.maxPlayers} PLAZAS",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            color = TernaColors.TextMuted,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun WaitingRoomHostPreview() = TernaTheme {
    val room = Room(
        "r1", "Sala de Miguel", RoomType.Private, "u1", "TRN-7K4Q", 6, RoomStatus.Waiting, null, "TERNA_CLASSIC",
        listOf(RoomMember("u1", "Miguel", "jaguar_01", 1, true), RoomMember("u2", "Felipe", "jaguar_01", 2, false), RoomMember("u3", "Espinosa", "jaguar_01", 3, false)),
    )
    WaitingRoomContent(WaitingRoomUiState(room = room, myUid = "u1"), remember { SnackbarHostState() }, {}, {}, {})
}
