package com.ternagames.terna.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ternagames.terna.core.designsystem.R
import com.ternagames.terna.core.designsystem.theme.LogoFont
import com.ternagames.terna.core.designsystem.theme.TernaColors

/**
 * Logo "TERNA!" dibujado con texto: relleno degradado amarillo, contorno café y sombra.
 * Provisional hasta tener el logo exportado de Figma como imagen.
 */
@Composable
fun TernaLogo(modifier: Modifier = Modifier, text: String = "TERNA!", fontSize: TextUnit = 84.sp) {
    val base = TextStyle(fontFamily = LogoFont, fontSize = fontSize, letterSpacing = 2.sp)
    val outline = with(LocalDensity.current) { 10.dp.toPx() }
    Box(modifier) {
        Text(
            text,
            style = base.copy(
                color = TernaColors.LogoOutline,
                drawStyle = Stroke(width = outline),
                shadow = Shadow(color = TernaColors.LogoOutline.copy(alpha = 0.8f), offset = Offset(0f, 14f), blurRadius = 2f),
            ),
        )
        Text(
            text,
            style = base.copy(brush = Brush.verticalGradient(listOf(TernaColors.LogoTop, TernaColors.LogoBottom))),
        )
    }
}

private val cardImages = intArrayOf(
    R.drawable.card_01, R.drawable.card_02, R.drawable.card_03, R.drawable.card_04,
    R.drawable.card_05, R.drawable.card_06, R.drawable.card_07, R.drawable.card_08,
    R.drawable.card_09, R.drawable.card_10, R.drawable.card_11, R.drawable.card_12,
)

/** Proporción de las cartas del diseño (341 × 512). */
const val CARD_ASPECT_RATIO = 341f / 512f

/** Carta boca arriba (valor 1–12) o boca abajo (`value = null`). Se dimensiona por su alto. */
@Composable
fun PlayingCard(value: Int?, height: Dp, modifier: Modifier = Modifier) {
    val image = if (value == null) R.drawable.card_back else cardImages[(value - 1).coerceIn(0, 11)]
    Image(
        painter = painterResource(image),
        contentDescription = value?.let { "Carta $it" } ?: "Carta boca abajo",
        contentScale = ContentScale.Fit,
        modifier = modifier.height(height).aspectRatio(CARD_ASPECT_RATIO),
    )
}

/** Avatar redondo. Por ahora solo existe el jaguar; los demás caen a él. */
@Composable
fun PlayerAvatar(avatarId: String, size: Dp, modifier: Modifier = Modifier) {
    val image = when (avatarId) {
        "jaguar_01" -> R.drawable.avatar_jaguar_01
        else -> R.drawable.avatar_jaguar_01
    }
    Image(
        painter = painterResource(image),
        contentDescription = "Avatar",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(2.dp, TernaColors.Yellow, CircleShape),
    )
}

/** Barra de carga del splash. */
@Composable
fun TernaProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .height(18.dp)
            .clip(shape)
            .background(TernaColors.PanelVariant)
            .border(1.dp, TernaColors.Yellow.copy(alpha = 0.4f), shape),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(shape)
                .background(Brush.verticalGradient(listOf(TernaColors.LogoTop, TernaColors.Yellow))),
        )
    }
}
