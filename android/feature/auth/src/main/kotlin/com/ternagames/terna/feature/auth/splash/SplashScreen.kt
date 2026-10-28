package com.ternagames.terna.feature.auth.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ternagames.terna.core.designsystem.component.TernaBackdrop
import com.ternagames.terna.core.designsystem.component.TernaBackground
import com.ternagames.terna.core.designsystem.component.TernaLogo
import com.ternagames.terna.core.designsystem.component.TernaProgressBar
import com.ternagames.terna.core.designsystem.component.rememberAppVersion
import com.ternagames.terna.core.designsystem.theme.TernaColors
import com.ternagames.terna.core.designsystem.theme.TernaTheme

@Composable
fun SplashScreen(
    onSignedIn: (resumeRoomId: String?) -> Unit,
    onSignedOut: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.destination) {
        when (val destination = state.destination) {
            SplashDestination.Home -> onSignedIn(null)
            is SplashDestination.Room -> onSignedIn(destination.roomId)
            SplashDestination.Login -> onSignedOut()
            null -> Unit
        }
    }
    SplashContent(state)
}

@Composable
internal fun SplashContent(state: SplashUiState) {
    TernaBackground(TernaBackdrop.Valley) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(vertical = 16.dp),
        ) {
            TernaLogo(fontSize = 76.sp)
            Text(
                "EDICIÓN COLOMBIA",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                color = TernaColors.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(TernaColors.Field.copy(alpha = 0.85f))
                    .padding(horizontal = 36.dp, vertical = 6.dp),
            )
            Spacer(Modifier.weight(1f))
            Text("Cargando...", style = MaterialTheme.typography.titleLarge, color = TernaColors.White)
            Spacer(Modifier.height(10.dp))
            TernaProgressBar(state.progress, Modifier.width(340.dp))
            Spacer(Modifier.height(8.dp))
            Text(state.message, style = MaterialTheme.typography.bodySmall, color = TernaColors.TextMuted)
        }
        Text(
            "v${rememberAppVersion()}",
            style = MaterialTheme.typography.bodySmall,
            color = TernaColors.TextMuted,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .safeDrawingPadding()
                .padding(12.dp),
        )
    }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun SplashPreview() = TernaTheme { SplashContent(SplashUiState(progress = 0.55f)) }
