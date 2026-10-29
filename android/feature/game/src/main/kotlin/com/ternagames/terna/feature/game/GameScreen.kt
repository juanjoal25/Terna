package com.ternagames.terna.feature.game

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ternagames.terna.core.designsystem.audio.TernaSfx
import com.ternagames.terna.core.designsystem.audio.rememberTernaAudio
import com.ternagames.terna.core.designsystem.component.CARD_ASPECT_RATIO
import com.ternagames.terna.core.designsystem.component.PlayerAvatar
import com.ternagames.terna.core.designsystem.component.PlayingCard
import com.ternagames.terna.core.designsystem.component.TernaBackdrop
import com.ternagames.terna.core.designsystem.component.TernaBackground
import com.ternagames.terna.core.designsystem.theme.TernaColors
import com.ternagames.terna.core.model.HandEnd
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
    val onEnd: (target: String, end: HandEnd) -> Unit,
    val onClaimTarget: (target: String, value: Int) -> Unit,
    val onClaimCount: (target: String, value: Int, count: Int) -> Unit,
    val onRivalTap: (String) -> Unit,
    val onPickNumberFor: (String) -> Unit,
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
    val haptic = LocalHapticFeedback.current

    // Durante la partida, "atrás" no la abandona por accidente.
    BackHandler(enabled = state.match?.finished != true) {
        Toast.makeText(context, "Termina la partida para salir.", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(state.soundEffect?.first) {
        val sfx = state.soundEffect?.second ?: return@LaunchedEffect
        audio.play(sfx)
        // Vibración corta en los momentos que importan: fallar duele, completar una terna se siente.
        when (sfx) {
            TernaSfx.TurnFail, TernaSfx.AuraLoss -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            TernaSfx.TernaComplete, TernaSfx.VictoryNormal, TernaSfx.VictoryJackpot -> haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            else -> Unit
        }
    }
    LaunchedEffect(state.myTurnAlert) {
        if (state.myTurnAlert > 0) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    // Últimos 5 segundos de mi turno: una vibración corta por segundo.
    val tickSecond = state.match?.let { m ->
        if (m.finished || m.turn.player != state.myUid || state.serverNow < m.turn.startsAt) -1L
        else ((m.turn.deadline - state.serverNow + 999) / 1000).takeIf { it in 1..5 } ?: -1L
    } ?: -1L
    LaunchedEffect(tickSecond) {
        if (tickSecond > 0) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
            onRivalTap = viewModel::onRivalTapped,
            onPickNumberFor = viewModel::pickNumberFor,
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
private val Panel = Color(0xE61C1412)
private val PanelSoft = Color(0xCC1C1412)
private val Green = Color(0xFF4ADE80)

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
            TopBar(match)
            Opponents(match, state, tray, rivalBackH, actions.onRivalTap)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Table(match, state, tray, actions, Modifier.fillMaxSize())
            }
            Spacer(Modifier.height(handCardH * 0.62f + HAND_TOP_ROOM + 22.dp))
        }

        // Parte baja: ternas · mi mano · acciones
        Box(Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 6.dp)) {
            SetsPile(match.player(me), match.rules.setSize, (h * 0.12f).coerceIn(34.dp, 52.dp))
        }
        MyHand(match, state, handCardH, w, Modifier.align(Alignment.BottomCenter))
        ActionPanel(match, state, myTurn, actions, Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 6.dp))

        TurnAnnouncement(state.myTurnAlert, "¡TU TURNO!", Modifier.align(Alignment.Center).offset(y = (-30).dp))

        // Confeti al completar una terna y al ganar.
        val iWon = match.finished && match.result?.winners?.contains(me) == true
        ConfettiBurst(
            trigger = when {
                iWon -> "win"
                tray.kind == GameLogic.TrayKind.Completed -> match.seq
                else -> null
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Espacio sobre las cartas de la mano para las etiquetas "MENOR" y "MAYOR". */
private val HAND_TOP_ROOM = 16.dp

@Composable
private fun TopBar(match: MatchState) {
    Box(Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 10.dp)) {
        Text("TERNA", style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold), color = TernaColors.White, modifier = Modifier.align(Alignment.CenterStart))
        Text(
            if (match.finished) "PARTIDA TERMINADA" else "● PARTIDA EN CURSO · TURNO ${match.turn.number}",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = TernaColors.TextMuted,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

// ───────────────────────── Rivales ─────────────────────────

@Composable
private fun Opponents(match: MatchState, state: GameUiState, tray: GameLogic.Tray, backH: Dp, onRivalTap: (String) -> Unit) {
    val me = state.myUid
    val mySeat = match.player(me)?.seat ?: 0
    val n = match.players.size
    // En el orden en que van jugando después de mí.
    val rivals = match.players.filter { it.uid != me }.sortedBy { (it.seat - mySeat + n) % n }
    val selectable = GameLogic.otherTargets(match, me).map { it.uid }.toSet()
    val failedPick = tray.mismatch?.takeIf { tray.kind == GameLogic.TrayKind.Failed }

    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
        rivals.forEach { rival ->
            val canTap = state.canAct && !match.finished
            Opponent(
                player = rival,
                active = !match.finished && match.turn.player == rival.uid,
                selectable = canTap && rival.uid in selectable,
                showing = GameLogic.cardShownBy(tray, rival.uid)?.value,
                mismatchChase = if (failedPick?.owner == rival.uid) GameLogic.failedChase(tray) else null,
                backH = backH,
                onClick = if (canTap) ({ onRivalTap(rival.uid) }) else null,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Opponent(
    player: MatchPlayer,
    active: Boolean,
    selectable: Boolean,
    showing: Int?,
    mismatchChase: Int?,
    backH: Dp,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val pulse by rememberPulse(0.4f, 1f, 800)
    val dots = if (active) rememberDots() else ""
    val borderColor = when {
        mismatchChase != null -> TernaColors.Danger
        showing != null -> Green.copy(alpha = 0.6f + 0.4f * pulse)
        active -> TernaColors.Yellow.copy(alpha = 0.55f + 0.45f * pulse)
        selectable -> TernaColors.Yellow.copy(alpha = 0.35f + 0.5f * pulse)
        else -> Color(0x66FDE047)
    }
    val borderWidth = if (active || mismatchChase != null || showing != null) 2.5.dp else if (selectable) 2.dp else 1.dp
    val status = when {
        !player.connected -> "SIN CONEXIÓN"
        mismatchChase != null -> "✗ NO ERA EL $mismatchChase"
        showing != null -> "ENSEÑA UN $showing"
        active -> "PENSANDO$dots"
        selectable -> "TOCA PARA PREGUNTAR"
        else -> "${player.handCount} CARTAS"
    }
    val statusColor = when {
        mismatchChase != null -> TernaColors.Danger
        showing != null -> Green
        active || selectable -> TernaColors.Yellow
        else -> TernaColors.TextMuted
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .scale(if (active) 1f + 0.03f * pulse else 1f)
                .clip(shape)
                .background(Panel)
                .border(borderWidth, borderColor, shape)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = "Preguntar a ${player.name}", onClick = onClick) else Modifier)
                .padding(horizontal = 7.dp, vertical = 4.dp),
        ) {
            PlayerAvatar(player.avatar, 28.dp)
            Spacer(Modifier.width(7.dp))
            Column {
                Text(player.name, style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp), color = TernaColors.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 96.dp))
                Text(status, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = statusColor, maxLines = 1)
            }
        }
        Spacer(Modifier.height(3.dp))
        // Su mano, boca abajo.
        val cardW = backH * CARD_ASPECT_RATIO
        val step = min(cardW.value * 0.6f, 120f / max(1, player.handCount))
        Box(Modifier.height(backH).width((cardW.value + step * max(0, player.handCount - 1)).dp)) {
            repeat(player.handCount) { i -> PlayingCard(null, backH, Modifier.offset(x = (step * i).dp)) }
        }
        if (player.sets.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) { player.sets.forEach { PlayingCard(it, 22.dp) } }
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
        val trayH = (maxHeight * 0.42f).coerceIn(52.dp, 74.dp)
        val faceUp = GameLogic.faceUpCenter(tray)
        val chase = GameLogic.chaseValue(match)

        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.selectingCenter) {
                Text("TOCA UNA CARTA DEL CENTRO", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = TernaColors.Yellow)
                Spacer(Modifier.height(2.dp))
            }
            CenterGrid(match, cols, rows, cardH, gap, faceUp, tray.mismatch?.slot, state.selectingCenter, actions.onCenterSlot)
        }

        // Qué número se está buscando y cuántas cartas van, a la izquierda del centro.
        if (chase != null && tray.kind == GameLogic.TrayKind.Live && !match.finished) {
            ChaseIndicator(chase, match.revealed.size, match.rules.setSize, Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 2.dp))
        }

        // Cartas destapadas este turno, a la derecha del centro.
        Tray(tray, match, trayH, Modifier.align(Alignment.TopEnd).padding(end = 14.dp, top = 2.dp))

        BannerView(state, Modifier.align(Alignment.TopCenter).padding(top = 2.dp))
    }
}

/** "BUSCANDO [7] ●●○": el número que se persigue y cuántas cartas faltan para la terna. */
@Composable
private fun ChaseIndicator(value: Int, have: Int, need: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clip(shape).background(PanelSoft).border(1.5.dp, TernaColors.Yellow, shape).padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        PopIn(delayMs = 0) { PlayingCard(value, 46.dp) }
        Spacer(Modifier.width(8.dp))
        Column {
            Text("BUSCANDO", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = TernaColors.Yellow)
            Text("el $value", style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp), color = TernaColors.White)
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(need) { i ->
                    val filled = i < have
                    val dot by animateFloatAsState(if (filled) 1f else 0.35f, spring(Spring.DampingRatioMediumBouncy), label = "dot")
                    Box(Modifier.size(10.dp).scale(0.7f + 0.3f * dot).clip(CircleShape).background(if (filled) TernaColors.Yellow else Color(0x55FFFFFF)))
                }
            }
        }
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
    mismatchSlot: Int?,
    selecting: Boolean,
    onSlot: (Int) -> Unit,
) {
    val cardW = cardH * CARD_ASPECT_RATIO
    val shape = RoundedCornerShape(5.dp)
    val pulse by rememberPulse()
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        repeat(rows) { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                repeat(cols) { c ->
                    val slot = r * cols + c
                    val hidden = match.center.getOrNull(slot) == true
                    val value = faceUp[slot]
                    val pickable = selecting && hidden && value == null
                    // Un hueco vacío conserva su sitio: así se puede recordar dónde estaba cada carta.
                    Box(Modifier.size(cardW, cardH)) {
                        if (hidden || value != null) {
                            val outline = when {
                                mismatchSlot == slot -> TernaColors.Danger
                                pickable -> TernaColors.Yellow
                                else -> null
                            }
                            FlipCard(
                                value = value,
                                height = cardH,
                                faceUp = value != null,
                                modifier = Modifier
                                    .then(if (outline != null) Modifier.border(2.5.dp, outline.copy(alpha = 0.3f + 0.7f * pulse), shape) else Modifier)
                                    .then(if (pickable) Modifier.clickable(role = Role.Button, onClickLabel = "Revelar esta carta") { onSlot(slot) } else Modifier),
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
    if (tray.isEmpty) return
    val chase = GameLogic.failedChase(tray)
    val color = when (tray.kind) {
        GameLogic.TrayKind.Failed -> TernaColors.Danger
        GameLogic.TrayKind.Passed, GameLogic.TrayKind.TimedOut -> TernaColors.TextMuted
        GameLogic.TrayKind.Completed -> Green
        GameLogic.TrayKind.Live -> TernaColors.Yellow
    }
    val label = when (tray.kind) {
        GameLogic.TrayKind.Failed -> if (tray.mismatch != null && chase != null) "NO ERA EL $chase" else "NO COINCIDEN"
        GameLogic.TrayKind.Passed -> "PASÓ EL TURNO"
        GameLogic.TrayKind.TimedOut -> "SE ACABÓ EL TIEMPO"
        GameLogic.TrayKind.Completed -> "¡TERNA!"
        GameLogic.TrayKind.Live -> "EN MESA"
    }
    val celebrate by rememberPulse(1f, 1.07f, 420)
    val grow = if (tray.kind == GameLogic.TrayKind.Completed) celebrate else 1f

    // Cada fallo sacude la bandeja una vez (el `seq` solo cambia con una jugada nueva).
    Shaking(trigger = if (tray.kind == GameLogic.TrayKind.Failed) match.seq else null, modifier = modifier.scale(grow)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = color,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(PanelSoft).border(1.dp, color, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 1.dp),
            )
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tray.cards.forEachIndexed { i, card ->
                    key("${card.owner}|${card.end}|${card.slot}|${card.value}|$i") {
                        PopIn {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val shape = RoundedCornerShape(6.dp)
                                RevealingCard(card.value, cardH, Modifier.border(2.dp, color, shape).shadow(4.dp, shape))
                                Text(cardCaption(card, match), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TernaColors.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 60.dp))
                            }
                        }
                    }
                }
                tray.mismatch?.let { pick ->
                    key("mismatch") {
                        PopIn(delayMs = 120) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                MismatchCard(cardH)
                                val where = if (pick.fromCenter) "Centro" else "${match.nameOf(pick.owner.orEmpty())} · ${if (pick.end == HandEnd.High) "mayor" else "menor"}"
                                Text(where, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TernaColors.Danger, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 70.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** La carta que no coincidía: boca abajo y marcada. Nadie ve su número. */
@Composable
private fun MismatchCard(height: Dp) {
    val shape = RoundedCornerShape(6.dp)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.border(2.dp, TernaColors.Danger, shape).shadow(4.dp, shape)) {
        PlayingCard(null, height)
        Box(Modifier.size(height * 0.5f).clip(CircleShape).background(Color(0xCC1C1412)).border(2.dp, TernaColors.Danger, CircleShape), contentAlignment = Alignment.Center) {
            Text("✗", color = TernaColors.Danger, fontSize = (height.value * 0.3f).sp, fontWeight = FontWeight.ExtraBold)
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
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it / 2 },
        modifier = modifier,
    ) {
        val banner = state.banner ?: return@AnimatedVisibility
        val color = when (banner.tone) {
            BannerTone.Success -> Green
            BannerTone.Error -> TernaColors.Danger
            BannerTone.Info -> TernaColors.Yellow
        }
        val icon = when (banner.tone) {
            BannerTone.Success -> "✓"
            BannerTone.Error -> "✗"
            BannerTone.Info -> "i"
        }
        val shape = RoundedCornerShape(12.dp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .widthIn(max = 400.dp)
                .shadow(6.dp, shape)
                .clip(shape)
                .background(Color(0xF01C1412))
                .border(2.dp, color, shape)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                Text(icon, color = TernaColors.OnYellow, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(8.dp))
            Text(banner.text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = TernaColors.White, textAlign = TextAlign.Start)
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
    val fanW = (screenW - 500.dp).coerceAtLeast(cardW * 2f)
    val step = if (hand.size <= 1) 0.dp else min(cardW.value * 0.72f, (fanW - cardW).value / (hand.size - 1)).dp
    val totalW = cardW + step * max(0, hand.size - 1)
    val mid = (hand.size - 1) / 2f

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(PanelSoft)
                .border(1.dp, Color(0x66FDE047), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Text("${me?.name ?: "Tú"} (Tú)", style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp), color = TernaColors.White)
            Spacer(Modifier.width(6.dp))
            Text("TU MANO · ${hand.size} CARTAS", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = TernaColors.TextMuted)
        }
        // Una parte del abanico queda fuera de la pantalla, como en el diseño.
        Box(Modifier.width(totalW + 8.dp).height(cardH * 0.62f + HAND_TOP_ROOM)) {
            hand.forEachIndexed { i, value ->
                // Cada carta conserva su identidad (número + nº de copia): al salir una, las demás se deslizan en vez de cambiar de valor.
                val copy = hand.take(i).count { it == value }
                key(value * 10 + copy) {
                    val lift = abs(i - mid) * abs(i - mid) * 0.8f
                    HandCard(
                        value = value,
                        cardH = cardH,
                        x = step * i + 4.dp,
                        y = HAND_TOP_ROOM + lift.dp,
                        tilt = (i - mid) * 3.2f,
                        highlighted = chase != null && value == chase,
                    )
                    if (hand.size > 1 && i == 0) EndBadge("MENOR", Modifier.offset(x = step * i + 4.dp + cardW / 2 - 20.dp, y = HAND_TOP_ROOM + lift.dp - 14.dp))
                    if (hand.size > 1 && i == hand.lastIndex) EndBadge("MAYOR", Modifier.offset(x = step * i + 4.dp + cardW / 2 - 20.dp, y = HAND_TOP_ROOM + lift.dp - 14.dp))
                }
            }
        }
    }
}

@Composable
private fun HandCard(value: Int, cardH: Dp, x: Dp, y: Dp, tilt: Float, highlighted: Boolean) {
    val animX by animateDpAsState(x, spring(stiffness = Spring.StiffnessMediumLow), label = "handX")
    val animY by animateDpAsState(if (highlighted) y - 10.dp else y, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "handY")
    val animTilt by animateFloatAsState(tilt, spring(stiffness = Spring.StiffnessMediumLow), label = "handTilt")
    val pulse = if (highlighted) rememberPulse(0.45f, 1f, 600).value else 1f
    PlayingCard(
        value = value,
        height = cardH,
        modifier = Modifier
            .offset(x = animX, y = animY)
            .rotate(animTilt)
            .then(if (highlighted) Modifier.border(3.dp, TernaColors.Yellow.copy(alpha = pulse), RoundedCornerShape(7.dp)) else Modifier),
    )
}

@Composable
private fun EndBadge(text: String, modifier: Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
        color = TernaColors.OnYellow,
        textAlign = TextAlign.Center,
        modifier = modifier.clip(RoundedCornerShape(50)).background(TernaColors.Yellow.copy(alpha = 0.92f)).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Composable
private fun SetsPile(me: MatchPlayer?, setSize: Int, cardH: Dp) {
    val sets = me?.sets.orEmpty()
    if (sets.isEmpty()) return
    Column {
        Text(
            "${sets.size} ${if (sets.size == 1) "Terna completada" else "Ternas completadas"}",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = TernaColors.Yellow,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(PanelSoft)
                .border(1.dp, TernaColors.Yellow, RoundedCornerShape(50))
                .padding(horizontal = 7.dp, vertical = 1.dp),
        )
        Spacer(Modifier.height(3.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            sets.forEach { value ->
                PopIn {
                    Box(Modifier.width(cardH * CARD_ASPECT_RATIO + 5.dp * (setSize - 1)).height(cardH)) {
                        repeat(setSize) { i -> PlayingCard(value, cardH, Modifier.offset(x = (i * 5).dp)) }
                    }
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
    val shape = RoundedCornerShape(14.dp)
    val completeEnd = GameLogic.completeEnd(match, me)
    val waiting = state.serverNow < match.turn.startsAt - 300
    val millisLeft = max(0L, match.turn.deadline - state.serverNow)
    val secondsLeft = (millisLeft + 999) / 1000
    val urgent = secondsLeft <= 5 && !waiting
    val fraction = if (waiting) 1f else millisLeft / (match.turnSeconds * 1000f).coerceAtLeast(1f)
    val pulse by rememberPulse(0.4f, 1f, 700)
    val chase = GameLogic.chaseValue(match)
    val borderColor = if (myTurn && enabled) TernaColors.Yellow.copy(alpha = 0.5f + 0.5f * pulse) else TernaColors.Yellow.copy(alpha = 0.45f)

    val canPass = GameLogic.canPass(match, me)
    val hint = when {
        !myTurn -> "Juega ${match.nameOf(match.turn.player)}" + if (GameLogic.nextPlayer(match)?.uid == me) " · ¡después juegas tú!" else ""
        waiting -> "Mira las cartas de la mesa…"
        state.busy -> "Enviando tu jugada…"
        state.selectingCenter -> "Toca una carta del centro"
        GameLogic.isDeadEnd(match, me) -> "No quedan jugadas: pasa el turno"
        completeEnd != null -> "¡Te falta una carta y la tienes en la mano!"
        chase != null && GameLogic.claimUsedUp(match) -> "Busca otro $chase · pregunta o pasa el turno"
        chase != null -> "Busca otro $chase · pregunta, pide o pasa"
        else -> "Toca a un rival o elige una acción"
    }

    Column(
        modifier = modifier
            .width(240.dp)
            .shadow(8.dp, shape)
            .clip(shape)
            .background(Panel)
            .border(2.dp, borderColor, shape)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (myTurn) "ES TU TURNO" else "TURNO DE ${match.nameOf(match.turn.player).uppercase()}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = if (myTurn) TernaColors.Yellow else TernaColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (waiting) "…" else "0:%02d".format(min(secondsLeft, 59L)),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                color = if (urgent) TernaColors.Danger else TernaColors.White,
            )
        }
        TimerBar(fraction, urgent)
        Text(hint, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = if (myTurn) TernaColors.Yellow else TernaColors.TextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)

        if (completeEnd != null && myTurn) {
            GameButton("¡HACER TERNA!", actions.onComplete, Modifier.fillMaxWidth(), enabled = enabled, emphasized = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            GameButton(
                if (state.selectingCenter) "CANCELAR" else "REVELAR CENTRO",
                actions.onRevealCenter,
                Modifier.weight(1f),
                enabled = enabled && GameLogic.canRevealCenter(match),
            )
            GameButton("PREGUNTAR", actions.onAsk, Modifier.weight(1f), enabled = enabled && GameLogic.revealTargets(match, me).isNotEmpty())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (match.rules.allowClaim) {
                GameButton("PEDIR CARTA", actions.onClaim, Modifier.weight(1f), enabled = enabled && GameLogic.canClaim(match, me))
            }
            GameButton("PASAR TURNO", actions.onPass, Modifier.weight(1f), enabled = enabled && canPass, outlined = true)
        }
        // Por qué un botón está apagado: nada de adivinar.
        val note = if (!enabled) null else listOfNotNull(
            "Ya pediste una carta en este turno".takeIf { match.rules.allowClaim && GameLogic.claimUsedUp(match) },
            GameLogic.passBlockedReason(match, me),
        ).joinToString(" · ").ifEmpty { null }
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = TernaColors.TextMuted, maxLines = 2)
        }
    }
}

/** Botón de la mesa: se hunde al tocarlo, y el importante ("¡Hacer terna!") late. */
@Composable
private fun GameButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    outlined: Boolean = false,
) {
    val audio = rememberTernaAudio()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed && enabled) 0.93f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "press")
    val pulse = if (emphasized && enabled) rememberPulse(1f, 1.05f, 450).value else 1f
    val shape = RoundedCornerShape(10.dp)

    val background = when {
        !enabled -> Color(0x26FFFFFF)
        outlined -> Color.Transparent
        emphasized -> Green
        else -> TernaColors.Yellow
    }
    val textColor = when {
        !enabled -> Color(0x99CFC6BE)
        outlined -> TernaColors.Yellow
        else -> TernaColors.OnYellow
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .scale(press * pulse)
            .defaultMinSize(minHeight = 38.dp)
            .clip(shape)
            .background(background)
            .then(if (outlined && enabled) Modifier.border(2.dp, TernaColors.Yellow, shape) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    audio.play(TernaSfx.UiClick)
                    onClick()
                },
            )
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
