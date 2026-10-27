package com.ternagames.terna.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Colores tomados del Figma (ver docs/ARQUITECTURA.md §8.1). */
object TernaColors {
    val Yellow = Color(0xFFFDE047)
    val YellowDark = Color(0xFFC9A80F)
    val OnYellow = Color(0xFF1A1410)
    val Panel = Color(0xE61C1412)
    val PanelVariant = Color(0xFF2A2220)
    val Field = Color(0xFF19110F)
    val Danger = Color(0xFFFF383C)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextMuted = Color(0xFFCFC6BE)
    val Facebook = Color(0xFF1877F2)
    val FacebookDark = Color(0xFF0F5BC4)
    val White = Color(0xFFFFFFFF)
    val WhiteShade = Color(0xFFD9D5D0)
    val LogoOutline = Color(0xFF6B3A12)
    val LogoTop = Color(0xFFFFF27A)
    val LogoBottom = Color(0xFFF5B81C)
}

private val TernaColorScheme = darkColorScheme(
    primary = TernaColors.Yellow,
    onPrimary = TernaColors.OnYellow,
    secondary = TernaColors.Yellow,
    onSecondary = TernaColors.OnYellow,
    background = TernaColors.Field,
    onBackground = TernaColors.TextPrimary,
    surface = TernaColors.Panel,
    onSurface = TernaColors.TextPrimary,
    surfaceVariant = TernaColors.PanelVariant,
    onSurfaceVariant = TernaColors.TextMuted,
    outline = TernaColors.Yellow,
    error = TernaColors.Danger,
)

@Composable
fun TernaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TernaColorScheme,
        typography = TernaTypography,
        content = content,
    )
}
