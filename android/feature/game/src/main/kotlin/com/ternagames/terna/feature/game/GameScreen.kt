package com.ternagames.terna.feature.game

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ternagames.terna.core.designsystem.audio.rememberTernaAudio
import com.ternagames.terna.core.designsystem.component.CARD_ASPECT_RATIO
import com.ternagames.terna.core.designsystem.component.PlayerAvatar
import com.ternagames.terna.core.designsystem.component.PlayingCard
import com.ternagames.terna.core.designsystem.component.TernaBackdrop
import com.ternagames.terna.core.designsystem.component.TernaBackground
import com.ternagames.terna.core.designsystem.component.TernaCompactButton
import com.ternagames.terna.core.designsystem.theme.TernaColors
import com.ternagames.terna.core.model.MatchPlayer
import com.ternagames.terna.core.model.MatchState
import com.ternagames.terna.core.model.RevealedCard
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Todo lo que la pantalla puede pedirle al ViewModel. */
internal class GameActions(
    val onRevealCenter: () -> Unit,
    val onAsk: () -> Unit,
    val onClaim: () -> Unit,
    val onComplete: () -> Unit,
    val onPass: () -> Unit,
    val onCenterSlot: (Int) -> Unit,
    val onNumber: (Int) -> Unit,
    val onRevealTarget: (String) -> Unit,
    val onEnd: (target: String, end: com.ternagames.terna.core.model.HandEnd) -> Unit,
    val onClaimTarget: (target: String, value: Int) -> Unit,
    val onClaimCount: (target: String, value: Int, count: Int) -> Unit,
    val onDismissDialog: () -> Unit,
    val onBackToRoom: () -> Unit,
    val onGoLobby: () -> Unit,
)

@Composable
fun GameScreen(
    onBackToRoom: (roomId: String) -> Unit,
    onGoHome: () -> Unit,
    viewModel: GameViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val audio = rememberTernaAudio()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Durante la partida, "atrás" no la abandona por accidente.
    BackHandler(enabled = state.match?.finished != true) {
        Toast.makeText(context, "Termina la partida para salir.", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(state.soundEffect?.first) {
        state.soundEffect?.second?.let(audio::play)
    }

    LaunchedEffect(state.exit) {
        when (val exit = state.exit) {
            is GameExit.BackToRoom -> onBackToRoom(exit.roomId)
            GameExit.Lobby -> onGoHome()
            null -> Unit
        }
    }
    LaunchedEffect(state.gone) {
        if (state.gone) {
            Toast.makeText(context, "Esa partida ya no está disponible.", Toast.LENGTH_LONG).show()
            onGoHome()
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    val actions = remember(viewModel) {
        GameActions(
            onRevealCenter = viewModel::selectCenter,
            onAsk = viewModel::ask,
            onClaim = viewModel::claim,
            onComplete = viewModel::completeSet,
            onPass = viewModel::pass,
            onCenterSlot = viewModel::onCenterSlot,
            onNumber = viewModel::onNumberPicked,
            onRevealTarget = viewModel::onRevealTargetPicked,
            onEnd = viewModel::onEndPicked,
            onClaimTarget = viewModel::onClaimTargetPicked,
            onClaimCount = viewModel::onClaimCountPicked,
            onDismissDialog = viewModel::dismissDialog,
            onBackToRoom = viewModel::backToRoom,
            onGoLobby = viewModel::goToLobby,
        )
    }

    val match = state.match
    TernaBackground(TernaBackdrop.Table, scrim = NO_SCRIM) {
        if (match == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = TernaColors.Yellow) }
        } else {
            GameContent(match, state, actions)
            GameDialogs(match, state, actions)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding())
    }
}

private val NO_SCRIM = Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))

// ───────────────────────── Mesa ─────────────────────────

@Composable
internal fun GameContent(match: MatchState, state: GameUiState, actions: GameActions) {
    val me = state.myUid
    val tray = GameLogic.tray(match, state.serverNow)
    val myTurn = GameLogic.isMyTurn(match, me)

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val h = maxHeight
        val w = maxWidth
        val rivalBackH = (h * 0.07f).coerceIn(18.dp, 30.dp)
        val handCardH = (h * 0.36f).coerceIn(80.dp, 132.dp)

        Column(Modifier.fillMaxSize()) {
            TopBar()
            Opponents(match, state, rivalBackH)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Table(match, state, tray, actions, Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(handCardH * 0.62f + 22.dp))
        }

        // Parte baja: ternas · mi mano · acciones
        Box(Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 6.dp)) { SetsPile(match.player(me), (h * 0.12f).coerceIn(34.dp, 52.dp)) }
        MyHand(match, state, handCardH, w, Modifier.align(Alignment.BottomCenter))
        ActionPanel(match, state, myTurn, actions, Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 6.dp))
    }
}

@Composable
private fun TopBar() {
    Box(Modifier.fillMaxWidth().height(22.dp).padding(horizontal = 10.dp)) {
        Text("TERNA", style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold), color = TernaColors.White, modifier = Modifier.align(Alignment.CenterStart))
        Text("● PARTIDA EN CURSO", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = TernaColors.TextMuted, modifier = Modifier.align(Alignment.Center))
    }
}

// ───────────────────────── Rivales ─────────────────────────

@Composable
private fun Opponents(match: MatchState, state: GameUiState, backH: Dp) {
    val me = state.myUid
    val mySeat = match.player(me)?.seat ?: 0
    val n = match.players.size
    // En el orden en que van jugando después de mí.
    val rivals = match.players.filter { it.uid != me }.sortedBy { (it.seat - mySeat + n) % n }

    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
        rivals.forEach { rival ->
            Opponent(rival, active = !match.finished && match.turn.player == rival.uid, backH = backH, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Opponent(player: MatchPlayer, active: Boolean, backH: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(shape)
                .background(Color(0xCC1C1412))
                .border(if (active) 2.dp else 1.dp, if (active) TernaColors.Yellow else Color(0x66FDE047), shape)
                .padding(horizontal = 6.dp, vertical = 3.dp),
        ) {
            PlayerAvatar(player.avatar, 22.dp)
            Spacer(Modifier.width(6.dp))
            Column {
                Text(player.name, style = MaterialTheme.typography.titleMedium.copy(fontSize = 11.sp), color = TernaColors.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 84.dp))
                Text(
                    if (!player.connected) "SIN CONEXIÓN" else if (active) "PENSANDO…" else "${player.handCount} CARTAS",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                    color = if (active) TernaColors.Yellow else TernaColors.TextMuted,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        // Su mano, boca abajo.
        val cardW = backH * CARD_ASPECT_RATIO
        val step = min(cardW.value * 0.6f, 120f / max(1, player.handCount))
        Box(Modifier.height(backH).width((cardW.value + step * max(0, player.handCount - 1)).dp)) {
            repeat(player.handCount) { i -> PlayingCard(null, backH, Modifier.offset(x = (step * i).dp)) }
        }
        if (player.sets.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) { player.sets.forEach { PlayingCard(it, 20.dp) } }
        }
    }
}

// ───────────────────────── Centro y cartas en mesa ─────────────────────────

@Composable
private fun Table(match: MatchState, state: GameUiState, tray: GameLogic.Tray, actions: GameActions, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val n = match.center.size
        val cols = when (n) { 8 -> 4; 9, 6 -> 3; else -> max(1, ceil(kotlin.math.sqrt(n.toFloat())).toInt()) }
        val rows = ceil(n / cols.toFloat()).toInt().coerceAtLeast(1)
        val gap = 4.dp
        val cardH = ((maxHeight - gap * (rows - 1) - 8.dp) / rows).coerceIn(30.dp, 86.dp)
        val faceUp = GameLogic.faceUpCenter(tray)

        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.selectingCenter) {
                Text("SELECCIONA UNA CARTA DEL CENTRO", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = TernaColors.Yellow)
                Spacer(Modifier.height(2.dp))
            }
            CenterGrid(match, cols, rows, cardH, gap, faceUp, state.selectingCenter, actions.onCenterSlot)
        }

        // Cartas destapadas este turno, a la derecha del centro.
        Tray(tray, match, cardH, Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 2.dp))

        BannerView(state, Modifier.align(Alignment.TopCenter).padding(top = 2.dp))
    }
}

@Composable
private fun CenterGrid(
    match: MatchState,
    cols: Int,
    rows: Int,
    cardH: Dp,
    gap: Dp,
    faceUp: Map<Int, Int>,
    selecting: Boolean,
    onSlot: (Int) -> Unit,
) {
    val cardW = cardH * CARD_ASPECT_RATIO
    val shape = RoundedCornerShape(4.dp)
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        repeat(rows) { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                repeat(cols) { c ->
                    val slot = r * cols + c
                    val hidden = match.center.getOrNull(slot) == true
                    val value = faceUp[slot]
                    // Un hueco vacío conserva su sitio: así se puede recordar dónde estaba cada carta.
                    Box(Modifier.size(cardW, cardH)) {
                        if (hidden || value != null) {
                            PlayingCard(
                                value = value,
                                height = cardH,
                                modifier = Modifier
                                    .then(if (selecting && hidden && value == null) Modifier.border(2.dp, TernaColors.Yellow, shape).clickable(role = Role.Button, onClickLabel = "Revelar esta carta") { onSlot(slot) } else Modifier),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tray(tray: GameLogic.Tray, match: MatchState, cardH: Dp, modifier: Modifier) {
    if (tray.cards.isEmpty()) return
    val color = when (tray.kind) {
        GameLogic.TrayKind.Failed -> TernaColors.Danger
        GameLogic.TrayKind.Completed -> Color(0xFF4ADE80)
        GameLogic.TrayKind.Live -> TernaColors.Yellow
    }
    val label = when (tray.kind) {
        GameLogic.TrayKind.Failed -> "NO COINCIDEN"
        GameLogic.TrayKind.Completed -> "¡TERNA!"
        GameLogic.TrayKind.Live -> "EN MESA"
    }
    val h = (cardH * 1.15f).coerceAtMost(72.dp)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = color)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tray.cards.forEach { card ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val shape = RoundedCornerShape(6.dp)
                    PlayingCard(card.value, h, Modifier.border(2.dp, color, shape).shadow(4.dp, shape))
                    Text(cardCaption(card, match), style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp), color = TernaColors.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 52.dp))
                }
            }
        }
    }
}

private fun cardCaption(card: RevealedCard, match: MatchState): String = when {
    card.fromCenter -> "Centro"
    else -> match.nameOf(card.owner.orEmpty())
}

@Composable
private fun BannerView(state: GameUiState, modifier: Modifier) {
    AnimatedVisibility(
        visible = state.banner != null,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
        modifier = modifier,
    ) {
        val banner = state.banner ?: return@AnimatedVisibility
        val color = when (banner.tone) {
            BannerTone.Success -> Color(0xFF4ADE80)
            BannerTone.Error -> TernaColors.Danger
            BannerTone.Info -> TernaColors.Yellow
        }
        val shape = RoundedCornerShape(10.dp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .widthIn(max = 360.dp)
                .clip(shape)
                .background(Color(0xE61C1412))
                .border(1.5.dp, color, shape)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            Text(banner.text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold), color = TernaColors.White, textAlign = TextAlign.Start)
        }
    }
}

// ───────────────────────── Mi mano y mis ternas ─────────────────────────

@Composable
private fun MyHand(match: MatchState, state: GameUiState, cardH: Dp, screenW: Dp, modifier: Modifier) {
    val hand = match.myHand
    val me = match.player(state.myUid)
    val chase = GameLogic.chaseValue(match)
    val cardW = cardH * CARD_ASPECT_RATIO
    val fanW = (screenW - 464.dp).coerceAtLeast(cardW * 2f)
    val step = if (hand.size <= 1) 0.dp else min(cardW.value * 0.72f, (fanW - cardW).value / (hand.size - 1)).dp
    val totalW = cardW + step * max(0, hand.size - 1)
    val mid = (hand.size - 1) / 2f

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xCC1C1412))
                .border(1.dp, Color(0x66FDE047), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Text("${me?.name ?: "Tú"} (Tú)", style = MaterialTheme.typography.titleMedium.copy(fontSize = 11.sp), color = TernaColors.White)
            Spacer(Modifier.width(6.dp))
            Text("TU MANO · ${hand.size} CARTAS", style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp), color = TernaColors.TextMuted)
        }
        // Una parte del abanico queda fuera de la pantalla, como en el diseño.
        Box(Modifier.width(totalW + 8.dp).height(cardH * 0.62f)) {
            hand.forEachIndexed { i, value ->
                val tilt = (i - mid) * 3.2f
                val lift = abs(i - mid) * abs(i - mid) * 0.8f
                val highlighted = chase != null && value == chase
                PlayingCard(
                    value = value,
                    height = cardH,
                    modifier = Modifier
                        .offset(x = step * i + 4.dp, y = lift.dp - if (highlighted) 8.dp else 0.dp)
                        .rotate(tilt)
                        .then(if (highlighted) Modifier.border(2.dp, TernaColors.Yellow, RoundedCornerShape(6.dp)) else Modifier),
                )
            }
        }
    }
}

@Composable
private fun SetsPile(me: MatchPlayer?, cardH: Dp) {
    val sets = me?.sets.orEmpty()
    if (sets.isEmpty()) return
    Column {
        Text(
            "${sets.size} ${if (sets.size == 1) "Terna completada" else "Ternas completadas"}",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            color = TernaColors.Yellow,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0xCC1C1412))
                .border(1.dp, TernaColors.Yellow, RoundedCornerShape(50))
                .padding(horizontal = 6.dp, vertical = 1.dp),
        )
        Spacer(Modifier.height(3.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            sets.forEach { value ->
                Box(Modifier.width(cardH * CARD_ASPECT_RATIO + 10.dp).height(cardH)) {
                    repeat(3) { i -> PlayingCard(value, cardH, Modifier.offset(x = (i * 5).dp)) }
                }
            }
        }
    }
}

// ───────────────────────── Panel de acciones ─────────────────────────

@Composable
private fun ActionPanel(match: MatchState, state: GameUiState, myTurn: Boolean, actions: GameActions, modifier: Modifier) {
    if (match.finished) return
    val me = state.myUid
    val enabled = state.canAct
    val shape = RoundedCornerShape(12.dp)
    val completeEnd = GameLogic.completeEnd(match, me)
    val secondsLeft = max(0L, (match.turn.deadline - state.serverNow + 999) / 1000)
    val waiting = state.serverNow < match.turn.startsAt - 300

    Column(
        modifier = modifier
            .width(212.dp)
            .clip(shape)
            .background(Color(0xE61C1412))
            .border(1.5.dp, TernaColors.Yellow, shape)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (myTurn) "ES TU TURNO" else "TURNO DE ${match.nameOf(match.turn.player).uppercase()}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = if (myTurn) TernaColors.Yellow else TernaColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (waiting) "…" else "0:%02d".format(min(secondsLeft, 59L)),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = if (secondsLeft <= 5 && !waiting) TernaColors.Danger else TernaColors.White,
            )
        }

        GameLogic.chaseValue(match)?.let { chase ->
            Text(
                "Buscando el $chase · ${match.revealed.size} de ${match.rules.setSize} en la mesa",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = TernaColors.Yellow,
                maxLines = 1,
            )
        }
        if (completeEnd != null && myTurn) {
            TernaCompactButton("¡HACER TERNA!", actions.onComplete, Modifier.fillMaxWidth(), enabled = enabled, loading = state.busy)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            TernaCompactButton("REVELAR CENTRO", actions.onRevealCenter, Modifier.weight(1f), enabled = enabled && GameLogic.canRevealCenter(match))
            TernaCompactButton("PREGUNTAR", actions.onAsk, Modifier.weight(1f), enabled = enabled && GameLogic.revealTargets(match, me).isNotEmpty())
        }
        if (match.rules.allowClaim || GameLogic.canPass(match)) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (match.rules.allowClaim) {
                    TernaCompactButton("PEDIR CARTA", actions.onClaim, Modifier.weight(1f), enabled = enabled && GameLogic.otherTargets(match, me).isNotEmpty())
                }
                if (match.rules.allowPass) {
                    TernaCompactButton("PASAR TURNO", actions.onPass, Modifier.weight(1f), enabled = enabled && GameLogic.canPass(match))
                }
            }
        }
    }
}
