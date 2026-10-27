package com.ternagames.terna.feature.auth.login

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ternagames.terna.core.designsystem.R as DesignR
import com.ternagames.terna.core.designsystem.component.PlayingCard
import com.ternagames.terna.core.designsystem.component.TernaBackdrop
import com.ternagames.terna.core.designsystem.component.TernaBackground
import com.ternagames.terna.core.designsystem.component.TernaLogo
import com.ternagames.terna.core.designsystem.component.TernaPrimaryButton
import com.ternagames.terna.core.designsystem.component.TernaProviderButton
import com.ternagames.terna.core.designsystem.component.rememberAppVersion
import com.ternagames.terna.core.designsystem.theme.TernaColors
import com.ternagames.terna.core.designsystem.theme.TernaTheme

@Composable
fun LoginScreen(
    onSignedIn: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.signedIn) { if (state.signedIn) onSignedIn() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    LoginContent(
        state = state,
        snackbar = snackbar,
        onGoogle = { activity?.let(viewModel::signInWithGoogle) },
        onFacebook = viewModel::signInWithFacebook,
        onGuest = viewModel::playAsGuest,
    )
}

@Composable
internal fun LoginContent(
    state: LoginUiState,
    snackbar: SnackbarHostState,
    onGoogle: () -> Unit,
    onFacebook: () -> Unit,
    onGuest: () -> Unit,
) {
    val busy = state.inProgress != null
    // El login usa el valle con un velo violeta, como en el Figma.
    TernaBackground(
        backdrop = TernaBackdrop.Valley,
        scrim = Brush.verticalGradient(listOf(Color(0x664A2A7A), Color(0x99301C4F))),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).fillMaxWidth()) {
                CardFan()
                TernaLogo(text = "TERNA", fontSize = 80.sp, modifier = Modifier.offset(y = 52.dp))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.widthIn(max = 860.dp).fillMaxWidth(),
            ) {
                TernaProviderButton(
                    text = "Iniciar sesión con Google",
                    logo = painterResource(DesignR.drawable.ic_google),
                    onClick = onGoogle,
                    face = TernaColors.White,
                    base = TernaColors.WhiteShade,
                    content = Color(0xFF1F1F1F),
                    enabled = !busy,
                    loading = state.inProgress == LoginMethod.Google,
                    modifier = Modifier.weight(1f),
                )
                TernaProviderButton(
                    text = "Registro con Facebook",
                    logo = painterResource(DesignR.drawable.ic_facebook),
                    onClick = onFacebook,
                    face = TernaColors.Facebook,
                    base = TernaColors.FacebookDark,
                    content = TernaColors.White,
                    enabled = !busy,
                    loading = state.inProgress == LoginMethod.Facebook,
                    modifier = Modifier.weight(1f),
                )
                TernaPrimaryButton(
                    text = "Jugar como invitado",
                    icon = Icons.Rounded.SportsEsports,
                    onClick = onGuest,
                    enabled = !busy,
                    loading = state.inProgress == LoginMethod.Guest,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        Text(
            "Versión ${rememberAppVersion()}",
            style = MaterialTheme.typography.bodySmall,
            color = TernaColors.TextMuted,
            modifier = Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(6.dp),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).safeDrawingPadding())
    }
}

/** Abanico de cartas detrás del logo (7, 9, 1, 5, 6, 12, 4 como en el diseño). */
@Composable
private fun CardFan() {
    val values = listOf(7, 9, 1, 5, 6, 12, 4)
    Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.offset(y = (-6).dp)) {
        values.forEachIndexed { i, value ->
            val center = (values.size - 1) / 2f
            val angle = (i - center) * 11f
            PlayingCard(
                value = value,
                height = 132.dp,
                modifier = Modifier
                    .offset(x = ((i - center) * 58).dp, y = (kotlin.math.abs(i - center) * 9).dp)
                    .rotate(angle),
            )
        }
    }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun LoginPreview() = TernaTheme {
    LoginContent(LoginUiState(), remember { SnackbarHostState() }, {}, {}, {})
}
