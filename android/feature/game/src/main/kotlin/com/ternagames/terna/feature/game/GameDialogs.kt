package com.ternagames.terna.feature.game

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ternagames.terna.core.designsystem.R as DesignR
import com.ternagames.terna.core.designsystem.audio.TernaSfx
import com.ternagames.terna.core.designsystem.audio.rememberTernaAudio
import com.ternagames.terna.core.designsystem.component.DialogTitle
import com.ternagames.terna.core.designsystem.component.PlayerAvatar
import com.ternagames.terna.core.designsystem.component.TernaDialog
import com.ternagames.terna.core.designsystem.component.TernaOutlineButton
import com.ternagames.terna.core.designsystem.component.TernaPrimaryButton
import com.ternagames.terna.core.designsystem.theme.TernaColors
import com.ternagames.terna.core.model.GameResult
import com.ternagames.terna.core.model.HandEnd
import com.ternagames.terna.core.model.MatchPlayer
import com.ternagames.terna.core.model.MatchState

@Composable
internal fun GameDialogs(match: MatchState, state: GameUiState, actions: GameActions) {
    val me = state.myUid

    // Al terminar la partida solo se muestra el resultado.
    val result = match.result
    if (match.finished && result != null) {
        ResultDialog(match, result, me, actions)
        return
    }

    when (val dialog = state.dialog) {
        null -> Unit

        GameDialog.PickRevealTarget -> TernaDialog(onDismiss = actions.onDismissDialog) {
            Header("¿A quién deseas preguntar?", actions.onDismissDialog)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GameLogic.revealTargets(match, me).forEach { player ->
                    val label = if (player.uid == me) "Yo (${GameLogic.hiddenCards(match, me)} Cartas)" else "${player.name} (${GameLogic.hiddenCards(match, player.uid)} Cartas)"
                    PlayerChoice(player.avatar, label) { actions.onRevealTarget(player.uid) }
                }
            }
        }

        is GameDialog.PickEnd -> TernaDialog(onDismiss = actions.onDismissDialog) {
            val target = if (dialog.target == me) "ti" else match.nameOf(dialog.target)
            Text("Preguntando a $target…", style = MaterialTheme.typography.labelSmall, color = TernaColors.TextMuted)
            Header("¿Qué carta deseas revelar?", actions.onDismissDialog)
            MismatchNote(match)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TernaPrimaryButton("CARTA MAYOR", { actions.onEnd(dialog.target, HandEnd.High) }, Modifier.weight(1f))
                TernaPrimaryButton("CARTA MENOR", { actions.onEnd(dialog.target, HandEnd.Low) }, Modifier.weight(1f))
            }
        }

        is GameDialog.RivalMenu -> TernaDialog(onDismiss = actions.onDismissDialog) {
            val rival = match.player(dialog.target)
            val chase = GameLogic.chaseValue(match)
            Header(rival?.name ?: "Rival", actions.onDismissDialog)
            Text(
                "${GameLogic.hiddenCards(match, dialog.target)} cartas ocultas",
                style = MaterialTheme.typography.bodySmall,
                color = TernaColors.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TernaPrimaryButton("CARTA MENOR", { actions.onEnd(dialog.target, HandEnd.Low) }, Modifier.weight(1f), enabled = !GameLogic.askUsedUp(match))
                TernaPrimaryButton("CARTA MAYOR", { actions.onEnd(dialog.target, HandEnd.High) }, Modifier.weight(1f), enabled = !GameLogic.askUsedUp(match))
            }
            if (match.rules.allowClaim) {
                Spacer(Modifier.height(8.dp))
                TernaOutlineButton(
                    if (chase != null) "PEDIRLE EL $chase" else "PEDIRLE UN NÚMERO",
                    { if (chase != null) actions.onClaimTarget(dialog.target, chase) else actions.onPickNumberFor(dialog.target) },
                    Modifier.fillMaxWidth(),
                    enabled = GameLogic.canClaim(match, me),
                )
            }
            Text(
                if (GameLogic.askUsedUp(match)) "Ya preguntaste en este turno: ahora puedes pedir carta." else "Preguntar solo se puede una vez por turno; pedir carta no tiene límite.",
                style = MaterialTheme.typography.bodySmall,
                color = TernaColors.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
            MismatchNote(match)
        }

        is GameDialog.PickNumber -> TernaDialog(onDismiss = actions.onDismissDialog) {
            Header(if (dialog.target != null) "¿Qué número le pides a ${match.nameOf(dialog.target)}?" else "¿Qué número pides?", actions.onDismissDialog)
            Text(
                "Dile a un rival que su carta mayor o menor es este número.",
                style = MaterialTheme.typography.bodySmall,
                color = TernaColors.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
            (1..MAX_CARD_NUMBER).chunked(6).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    row.forEach { number -> NumberChip(number) { actions.onNumber(number) } }
                }
            }
        }

        is GameDialog.PickClaimCount -> TernaDialog(onDismiss = actions.onDismissDialog) {
            val max = GameLogic.maxClaimCount(match, dialog.target)
            Header("¿Cuántos ${dialog.value} tiene ${match.nameOf(dialog.target)}?", actions.onDismissDialog)
            Text(
                "Si tiene varios iguales seguidos (por ejemplo dos 5 como cartas menores), pídelos todos de una vez. Si te equivocas: -1000 de aura.",
                style = MaterialTheme.typography.bodySmall,
                color = TernaColors.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                (1..max).forEach { count ->
                    TernaPrimaryButton(
                        if (count == 1) "1 CARTA" else "$count CARTAS",
                        { actions.onClaimCount(dialog.target, dialog.value, count) },
                        Modifier.weight(1f),
                    )
                }
            }
        }

        is GameDialog.PickClaimTarget -> TernaDialog(onDismiss = actions.onDismissDialog) {
            Header("¿A quién le pides el ${dialog.value}?", actions.onDismissDialog)
            Text(
                "Debe ser su carta menor o su carta mayor (una en el medio no cuenta). Si no la tiene: -1000 de aura y pierdes el turno.",
                style = MaterialTheme.typography.bodySmall,
                color = TernaColors.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GameLogic.otherTargets(match, me).forEach { player ->
                    PlayerChoice(player.avatar, "${player.name} (${GameLogic.hiddenCards(match, player.uid)} Cartas)") { actions.onClaimTarget(player.uid, dialog.value) }
                }
            }
        }
    }
}

/** Recuerda la regla que protege la información: la carta equivocada no se muestra. */
@Composable
private fun MismatchNote(match: MatchState) {
    val chase = GameLogic.chaseValue(match) ?: return
    if (!match.rules.hideMismatch) return
    Text(
        "Si no es el $chase, no se muestra: solo se avisa y pierdes el turno.",
        style = MaterialTheme.typography.bodySmall,
        color = TernaColors.TextMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
private fun Header(title: String, onClose: () -> Unit) {
    val audio = rememberTernaAudio()
    Box(Modifier.fillMaxWidth()) {
        DialogTitle(title, Modifier.padding(horizontal = 24.dp))
        Icon(
            Icons.Rounded.Close,
            contentDescription = "Cerrar",
            tint = TernaColors.TextMuted,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(22.dp)
                .clickable(
                    role = Role.Button,
                    onClick = {
                        audio.play(TernaSfx.UiClick)
                        onClose()
                    },
                ),
        )
    }
}

@Composable
private fun PlayerChoice(avatar: String, label: String, onClick: () -> Unit) {
    val audio = rememberTernaAudio()
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(TernaColors.PanelVariant)
            .border(1.dp, TernaColors.Yellow.copy(alpha = 0.6f), shape)
            .clickable(
                role = Role.Button,
                onClick = {
                    audio.play(TernaSfx.UiClick)
                    onClick()
                },
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        PlayerAvatar(avatar, 26.dp)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = TernaColors.White)
    }
}

@Composable
private fun NumberChip(number: Int, onClick: () -> Unit) {
    val audio = rememberTernaAudio()
    val shape = RoundedCornerShape(10.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(shape)
            .background(TernaColors.Yellow)
            .clickable(
                role = Role.Button,
                onClickLabel = "Pedir el $number",
                onClick = {
                    audio.play(TernaSfx.UiClick)
                    onClick()
                },
            ),
    ) {
        Text("$number", style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp), color = TernaColors.OnYellow)
    }
}

// ───────────────────────── Resultado ─────────────────────────

@Composable
private fun ResultDialog(match: MatchState, result: GameResult, me: String, actions: GameActions) {
    val iWon = me in result.winners
    val epic = iWon && result.reason == "INSTANT"
    val mySets = match.player(me)?.sets?.size ?: 0
    val winnerNames = result.winners.joinToString(" y ") { match.nameOf(it) }

    TernaDialog(onDismiss = {}, dismissible = false) {
        if (epic) {
            Image(
                painterResource(DesignR.drawable.img_ganar_7),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.height(8.dp))
        } else {
            Text(if (iWon) "👑" else "🎴", fontSize = 28.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        DialogTitle(
            when {
                epic -> "¡VICTORIA ÉPICA!"
                iWon -> "¡VICTORIA!"
                else -> "PARTIDA TERMINADA"
            },
        )
        Text(
            when {
                epic -> "¡Has logrado la Terna de la suerte (777) y ganaste automáticamente!"
                iWon && result.reason == "SETS" -> "¡Has completado $mySets Ternas y dominado la mesa!"
                iWon && result.reason == "LINKED" -> "¡Completaste dos ternas enlazadas y dominaste la mesa!"
                iWon -> "¡Ganaste la partida!"
                else -> "$winnerNames ${reasonText(result.reason)}."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TernaColors.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )
        Standings(match, result, me)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TernaPrimaryButton("VOLVER A LA SALA", actions.onBackToRoom, Modifier.weight(1f))
            TernaOutlineButton("IR AL LOBBY", actions.onGoLobby, Modifier.weight(1f))
        }
    }
}

/** Quién quedó dónde: primero los ganadores, luego por número de ternas. */
@Composable
private fun Standings(match: MatchState, result: GameResult, me: String) {
    val ranking = match.players.sortedWith(compareByDescending<MatchPlayer> { it.uid in result.winners }.thenByDescending { it.sets.size })
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().heightIn(max = 104.dp).verticalScroll(rememberScrollState()),
    ) {
        ranking.forEachIndexed { index, p ->
            val winner = p.uid in result.winners
            PopIn(Modifier.fillMaxWidth(), delayMs = 200L + index * 110L) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (winner) TernaColors.Yellow.copy(alpha = 0.2f) else TernaColors.PanelVariant)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                PlayerAvatar(p.avatar, 24.dp, borderWidth = if (winner) 2.dp else 0.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    p.name + if (p.uid == me) " (tú)" else "",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp),
                    color = TernaColors.White,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${p.sets.size} ${if (p.sets.size == 1) "terna" else "ternas"}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = if (winner) TernaColors.Yellow else TernaColors.TextMuted,
                )
                if (winner) Text(" 👑", fontSize = 14.sp)
            }
            }
        }
    }
}

private fun reasonText(reason: String) = when (reason) {
    "SETS" -> "completó 3 ternas y dominó la mesa"
    "INSTANT" -> "logró la terna de la suerte (777)"
    "LINKED" -> "completó dos ternas enlazadas"
    "EXHAUSTED" -> "ganó cuando se acabaron las cartas"
    else -> "ganó la partida"
}
