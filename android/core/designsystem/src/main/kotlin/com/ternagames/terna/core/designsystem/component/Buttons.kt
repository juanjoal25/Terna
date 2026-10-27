package com.ternagames.terna.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ternagames.terna.core.designsystem.theme.TernaColors

/**
 * Botón "3D" del Figma: cara de color sobre una base más oscura que se hunde al presionar.
 * Base de todos los botones de la app.
 */
@Composable
fun TernaChunkyButton(
    onClick: () -> Unit,
    face: Color,
    base: Color,
    content: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    body: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth = 4.dp
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f)
            .defaultMinSize(minHeight = 52.dp)
            .clip(shape)
            .background(base)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .padding(bottom = if (pressed) 0.dp else depth)
                .clip(shape)
                .background(face)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(color = content, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
            } else {
                CompositionLocalProvider(LocalContentColor provides content) { body() }
            }
        }
    }
}

/** Botón amarillo principal ("¡JUGAR!", "CREAR", "Jugar como invitado"). */
@Composable
fun TernaPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    TernaChunkyButton(
        onClick = onClick,
        face = TernaColors.Yellow,
        base = TernaColors.YellowDark,
        content = TernaColors.OnYellow,
        modifier = modifier,
        enabled = enabled,
        loading = loading,
    ) { ButtonLabel(text, icon = icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(22.dp)) } }) }
}

/** Botón de proveedor de login (Google en blanco, Facebook en azul). */
@Composable
fun TernaProviderButton(
    text: String,
    logo: Painter,
    onClick: () -> Unit,
    face: Color,
    base: Color,
    content: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    TernaChunkyButton(onClick, face, base, content, modifier, enabled, loading) {
        ButtonLabel(text, icon = { Icon(logo, contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(22.dp)) })
    }
}

@Composable
private fun ButtonLabel(text: String, icon: (@Composable () -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
    }
}

/** Botón redondo oscuro con etiqueta debajo ("AJUSTES", "CRÉDITOS"). */
@Composable
fun TernaRoundIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(TernaColors.PanelVariant),
        ) {
            Icon(icon, contentDescription = label, tint = TernaColors.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TernaColors.White)
    }
}
