package com.ternagames.terna.feature.home.settings

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ternagames.terna.core.designsystem.R as DesignR
import com.ternagames.terna.core.designsystem.component.TernaChunkyButton
import com.ternagames.terna.core.designsystem.component.TernaPanel
import com.ternagames.terna.core.designsystem.component.TernaProviderButton
import com.ternagames.terna.core.designsystem.theme.TernaColors
import com.ternagames.terna.feature.home.ui.SubScreenScaffold

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenCredits: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val snackbar = remember { SnackbarHostState() }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.signedIn) { if (!state.signedIn) onSignedOut() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    SubScreenScaffold(
        title = "AJUSTES",
        onBack = onBack,
        cornerAction = "CRÉDITOS" to onOpenCredits,
        snackbar = snackbar,
    ) {
        TernaPanel(Modifier.widthIn(max = 460.dp).fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (state.guest) "Estás jugando como invitado. Vincula tu cuenta de Google para no perder tu progreso."
                    else "Tu progreso está guardado en tu cuenta.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TernaColors.White,
                    textAlign = TextAlign.Center,
                )
                if (state.guest) {
                    TernaProviderButton(
                        text = "Vincular con Google",
                        logo = painterResource(DesignR.drawable.ic_google),
                        onClick = { activity?.let(viewModel::linkGoogle) },
                        face = TernaColors.White,
                        base = TernaColors.WhiteShade,
                        content = Color(0xFF1F1F1F),
                        loading = state.linking,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TernaChunkyButton(
                    onClick = { if (state.guest) confirmSignOut = true else viewModel.signOut() },
                    face = TernaColors.PanelVariant,
                    base = Color(0xFF15100E),
                    content = TernaColors.White,
                    enabled = !state.linking,
                    modifier = Modifier.width(240.dp),
                ) { Text("CERRAR SESIÓN", style = MaterialTheme.typography.labelLarge) }
            }
        }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            icon = { androidx.compose.material3.Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null) },
            title = { Text("¿Cerrar sesión de invitado?") },
            text = { Text("Las cuentas de invitado no se pueden recuperar: perderás tu nivel y tus estadísticas. Vincula Google antes si quieres conservarlos.") },
            confirmButton = {
                TextButton(onClick = { confirmSignOut = false; viewModel.signOut() }) {
                    Text("Cerrar sesión", color = TernaColors.Danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancelar") } },
            containerColor = TernaColors.Field,
        )
    }
}
