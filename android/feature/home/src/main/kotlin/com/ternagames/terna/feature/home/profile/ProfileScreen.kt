package com.ternagames.terna.feature.home.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ternagames.terna.core.designsystem.component.PlayerAvatar
import com.ternagames.terna.core.designsystem.theme.TernaColors
import com.ternagames.terna.core.designsystem.theme.TernaTheme
import com.ternagames.terna.core.model.Avatars
import com.ternagames.terna.core.model.ModeStats
import com.ternagames.terna.core.model.PlayerProfile
import com.ternagames.terna.feature.home.ui.SubScreenScaffold

/** Sombra suave para que el texto blanco se lea sobre el atardecer. */
private val TextShadow = Shadow(Color(0x99000000), Offset(0f, 3f), blurRadius = 8f)

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCredits: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.signedIn) { if (!state.signedIn) onSignedOut() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    ProfileContent(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        onOpenCredits = onOpenCredits,
        onSelectAvatar = viewModel::selectAvatar,
    )
}

@Composable
internal fun ProfileContent(
    state: ProfileUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCredits: () -> Unit,
    onSelectAvatar: (String) -> Unit,
) {
    val profile = state.profile
    var picking by rememberSaveable { mutableStateOf(false) }

    SubScreenScaffold(
        title = null,
        onBack = onBack,
        cornerActions = listOf("AJUSTES" to onOpenSettings, "CRÉDITOS" to onOpenCredits),
        snackbar = snackbar,
    ) {
        // El avatar crece con la pantalla pero deja sitio al nombre y las estadísticas.
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
            val avatarSize = (maxHeight - 96.dp).coerceIn(96.dp, 168.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                EditableAvatar(
                    avatarId = profile?.avatar ?: Avatars.DEFAULT,
                    photoUrl = state.photoUrl,
                    size = avatarSize,
                    onEdit = { if (profile != null) picking = true },
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    profile?.displayName ?: "Jugador",
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 38.sp, shadow = TextShadow),
                    color = TernaColors.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    // "Ternas Ganadas" son partidas ganadas (docs/ARQUITECTURA.md §11, decisión 12).
                    "Partidas Jugadas: ${profile?.gamesPlayed ?: 0} | Ternas Ganadas: ${profile?.gamesWon ?: 0}",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp, fontWeight = FontWeight.Medium, shadow = TextShadow),
                    color = TernaColors.White,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (picking && profile != null) {
        AvatarPickerDialog(
            current = profile.avatar,
            photoUrl = state.photoUrl,
            onDismiss = { picking = false },
            onConfirm = {
                picking = false
                onSelectAvatar(it)
            },
        )
    }
}

/** Avatar grande con el lápiz amarillo del diseño. Tocar cualquiera de los dos abre el selector. */
@Composable
private fun EditableAvatar(avatarId: String, photoUrl: String?, size: Dp, onEdit: () -> Unit) {
    val pencil = (size.value * 0.26f).dp
    Box(Modifier.size(size)) {
        PlayerAvatar(
            avatarId = avatarId,
            size = size,
            photoUrl = photoUrl,
            borderWidth = 0.dp,
            modifier = Modifier.clickable(role = Role.Button, onClickLabel = "Cambiar avatar", onClick = onEdit),
        )
        // El centro del lápiz cae sobre el borde del círculo, abajo a la derecha (como en el Figma).
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = -size * 0.04f, y = -size * 0.04f)
                .size(pencil)
                .clip(CircleShape)
                .background(TernaColors.Yellow)
                .clickable(role = Role.Button, onClickLabel = "Cambiar avatar", onClick = onEdit),
        ) {
            Icon(Icons.Rounded.Edit, contentDescription = "Cambiar avatar", tint = TernaColors.OnYellow, modifier = Modifier.size(pencil * 0.5f))
        }
    }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun ProfilePreview() = TernaTheme {
    ProfileContent(
        state = ProfileUiState(
            guest = false,
            profile = PlayerProfile(
                "u1", "Parra", "oso_01", anonymous = false, level = 3, xp = 0, coins = 0,
                stats = mapOf("TERNA_CLASSIC" to ModeStats(games = 30, wins = 11), "TERNA_SPICY" to ModeStats(games = 12, wins = 4)),
            ),
        ),
        snackbar = remember { SnackbarHostState() },
        onBack = {}, onOpenSettings = {}, onOpenCredits = {}, onSelectAvatar = {},
    )
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun GuestProfilePreview() = TernaTheme {
    ProfileContent(
        state = ProfileUiState(profile = PlayerProfile("u2", "Guest001", Avatars.DEFAULT, anonymous = true, level = 1, xp = 0, coins = 0)),
        snackbar = remember { SnackbarHostState() },
        onBack = {}, onOpenSettings = {}, onOpenCredits = {}, onSelectAvatar = {},
    )
}
