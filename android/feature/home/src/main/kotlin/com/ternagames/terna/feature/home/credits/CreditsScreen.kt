package com.ternagames.terna.feature.home.credits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ternagames.terna.core.designsystem.theme.TernaColors
import com.ternagames.terna.feature.home.ui.SubScreenScaffold

@Composable
fun CreditsScreen(onBack: () -> Unit, onOpenSettings: () -> Unit) {
    SubScreenScaffold(title = "CRÉDITOS", onBack = onBack, cornerAction = "AJUSTES" to onOpenSettings) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier) {
            listOf(
                "Desarrollo y Lógica: Hugo & Espinosa",
                "Diseño y Arte: Miguel",
                "Universidad Pontificia Bolivariana",
            ).forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                    color = TernaColors.White,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
