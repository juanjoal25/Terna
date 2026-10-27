package com.ternagames.terna.feature.home.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ternagames.terna.core.designsystem.component.TernaBackdrop
import com.ternagames.terna.core.designsystem.component.TernaBackground
import com.ternagames.terna.core.designsystem.component.TernaRoundIconButton
import com.ternagames.terna.core.designsystem.theme.TernaColors

/** Marco común de Ajustes, Créditos y Perfil: "← VOLVER", título TERNA y un botón de esquina. */
@Composable
internal fun SubScreenScaffold(
    title: String,
    onBack: () -> Unit,
    cornerAction: Pair<String, () -> Unit>?,
    snackbar: SnackbarHostState? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    TernaBackground(TernaBackdrop.Home) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Box(Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clickable(role = Role.Button, onClickLabel = "Volver", onClick = onBack)
                        .padding(8.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = TernaColors.White)
                    Spacer(Modifier.width(6.dp))
                    Text("VOLVER", style = MaterialTheme.typography.labelLarge, color = TernaColors.White)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.Center)) {
                    Text("TERNA", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp), color = TernaColors.White)
                    Text("MENÚ PRINCIPAL", style = MaterialTheme.typography.labelSmall, color = TernaColors.TextMuted)
                }
                cornerAction?.let { (label, action) ->
                    val icon = if (label == "AJUSTES") Icons.Outlined.Settings else Icons.Outlined.Info
                    TernaRoundIconButton(icon, label, action, Modifier.align(Alignment.CenterEnd))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.displayLarge.copy(fontSize = 44.sp), color = TernaColors.White)
            Spacer(Modifier.height(12.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
                modifier = Modifier.weight(1f),
                content = content,
            )
        }
        snackbar?.let { SnackbarHost(it, Modifier.align(Alignment.BottomCenter).safeDrawingPadding()) }
    }
}
