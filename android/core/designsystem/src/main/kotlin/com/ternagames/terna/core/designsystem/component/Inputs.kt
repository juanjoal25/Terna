package com.ternagames.terna.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ternagames.terna.core.designsystem.theme.TernaColors

/** Botón oscuro con borde amarillo ("ABANDONAR", "CANCELAR", "BUSCAR"). */
@Composable
fun TernaOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f)
            .defaultMinSize(minHeight = 44.dp)
            .clip(shape)
            .background(TernaColors.Field.copy(alpha = 0.85f))
            .border(1.5.dp, TernaColors.Yellow, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = TernaColors.Yellow, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = TernaColors.Yellow, maxLines = 1)
    }
}

/** Botón cuadrado con borde amarillo (los "−" y "+" del número de jugadores). */
@Composable
fun TernaSquareIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .size(48.dp)
            .clip(shape)
            .background(TernaColors.Field.copy(alpha = 0.6f))
            .border(1.5.dp, TernaColors.Yellow, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Icon(icon, contentDescription = null, tint = TernaColors.Yellow, modifier = Modifier.size(26.dp))
    }
}

/** Interruptor amarillo del diseño (apagado: pista oscura; encendido: pista amarilla). */
@Composable
fun TernaSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val track by animateColorAsState(if (checked) TernaColors.Yellow else TernaColors.PanelVariant, label = "switch-track")
    val knob by animateColorAsState(if (checked) TernaColors.OnYellow else Color(0xFF9A918A), label = "switch-knob")
    val offset by animateDpAsState(if (checked) 28.dp else 0.dp, label = "switch-offset")
    Box(
        modifier = modifier
            .width(56.dp)
            .height(30.dp)
            .clip(CircleShape)
            .background(track)
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset(x = offset)
                .size(24.dp)
                .clip(CircleShape)
                .background(knob),
        )
    }
}

/** Campo de texto oscuro con borde amarillo (código de sala, nombre de jugador). */
@Composable
fun TernaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    capitalizeAll: Boolean = false,
    singleLine: Boolean = true,
) {
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = TernaColors.White),
        cursorBrush = SolidColor(TernaColors.Yellow),
        keyboardOptions = KeyboardOptions(
            capitalization = if (capitalizeAll) KeyboardCapitalization.Characters else KeyboardCapitalization.Sentences,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        modifier = modifier,
        decorationBox = { inner ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(shape)
                    .background(TernaColors.Field)
                    .border(1.5.dp, TernaColors.Yellow, shape)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = TernaColors.TextMuted.copy(alpha = 0.7f))
                }
                inner()
            }
        },
    )
}

/** Ventana emergente con el estilo de los modales del Figma (panel oscuro con borde amarillo). */
@Composable
fun TernaDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = dismissible, dismissOnClickOutside = dismissible, usePlatformDefaultWidth = false),
    ) {
        TernaPanel(modifier = modifier.padding(horizontal = 24.dp).defaultMinSize(minWidth = 320.dp), contentPadding = 20.dp, content = content)
    }
}

/** Texto centrado de uso común en los modales. */
@Composable
fun DialogTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.headlineMedium,
        color = TernaColors.White,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}
