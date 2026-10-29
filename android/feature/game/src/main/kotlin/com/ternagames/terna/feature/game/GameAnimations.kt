package com.ternagames.terna.feature.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ternagames.terna.core.designsystem.component.PlayingCard
import com.ternagames.terna.core.designsystem.theme.TernaColors
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Carta que gira sobre su eje: boca abajo (`faceUp = false`) o con su número. Al voltear de nuevo
 * se sigue viendo el último número mientras gira, así el giro de vuelta se ve entero.
 */
@Composable
internal fun FlipCard(value: Int?, height: Dp, faceUp: Boolean, modifier: Modifier = Modifier, durationMs: Int = 480) {
    var lastValue by remember { mutableStateOf(value) }
    LaunchedEffect(value) { if (value != null) lastValue = value }
    val front = value ?: lastValue

    val rotation by animateFloatAsState(
        targetValue = if (faceUp && front != null) 180f else 0f,
        animationSpec = tween(durationMs, easing = FastOutSlowInEasing),
        label = "flip",
    )
    val density = LocalDensity.current.density
    Box(modifier.graphicsLayer { rotationY = rotation; cameraDistance = 14f * density }) {
        if (rotation <= 90f || front == null) {
            PlayingCard(null, height)
        } else {
            // Pasados 90° la carta se ve de espaldas: se gira otra vez para que el número no salga al revés.
            PlayingCard(front, height, Modifier.graphicsLayer { rotationY = 180f })
        }
    }
}

/** Carta que entra boca abajo y se voltea sola para mostrar su número. */
@Composable
internal fun RevealingCard(value: Int, height: Dp, modifier: Modifier = Modifier, delayMs: Long = 0) {
    var up by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs + 60)
        up = true
    }
    FlipCard(value, height, faceUp = up, modifier = modifier)
}

/** El contenido entra con un pequeño rebote (crece, baja y se hace visible). */
@Composable
internal fun PopIn(modifier: Modifier = Modifier, delayMs: Long = 0, content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        progress.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }
    Box(
        modifier.graphicsLayer {
            val p = progress.value
            scaleX = 0.55f + 0.45f * p
            scaleY = scaleX
            alpha = p.coerceIn(0f, 1f)
            translationY = (1f - p) * -28f
        },
    ) { content() }
}

/** Sacude el contenido de lado a lado cada vez que `trigger` cambia (null = no sacude). */
@Composable
internal fun Shaking(trigger: Any?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val offset = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == null) return@LaunchedEffect
        repeat(3) {
            offset.animateTo(9f, tween(45))
            offset.animateTo(-9f, tween(45))
        }
        offset.animateTo(0f, tween(60))
    }
    val density = LocalDensity.current.density
    Box(modifier.graphicsLayer { translationX = offset.value * density }) { content() }
}

/** Valor que sube y baja sin parar entre `from` y `to`: sirve para latidos y brillos. */
@Composable
internal fun rememberPulse(from: Float = 0.45f, to: Float = 1f, millis: Int = 750): State<Float> {
    val transition = rememberInfiniteTransition(label = "pulse")
    return transition.animateFloat(
        initialValue = from,
        targetValue = to,
        animationSpec = infiniteRepeatable(tween(millis, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
}

/** "", ".", "..", "..." que va cambiando: para "PENSANDO…". */
@Composable
internal fun rememberDots(): String {
    var count by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(420)
            count = (count + 1) % 4
        }
    }
    return ".".repeat(count)
}

/** Aviso grande y breve en medio de la mesa ("¡TU TURNO!"). No bloquea los toques. */
@Composable
internal fun TurnAnnouncement(trigger: Long, text: String, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(trigger) {
        if (trigger <= 0) return@LaunchedEffect
        visible = true
        delay(1_400)
        visible = false
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(120)) + scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), initialScale = 0.35f),
        exit = fadeOut(tween(320)) + scaleOut(tween(320), targetScale = 1.35f),
        modifier = modifier,
    ) {
        val shape = RoundedCornerShape(16.dp)
        Text(
            text,
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold),
            color = TernaColors.OnYellow,
            modifier = Modifier
                .clip(shape)
                .background(TernaColors.Yellow)
                .border(3.dp, TernaColors.YellowDark, shape)
                .padding(horizontal = 26.dp, vertical = 8.dp),
        )
    }
}

/** Barra de tiempo: se vacía despacio y se pone roja (y late) cuando quedan pocos segundos. */
@Composable
internal fun TimerBar(fraction: Float, urgent: Boolean, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(260, easing = LinearEasing), label = "timer")
    val pulse by rememberPulse(0.55f, 1f, 380)
    val color = when {
        urgent -> TernaColors.Danger.copy(alpha = pulse)
        animated < 0.5f -> TernaColors.Yellow
        else -> Color(0xFF4ADE80)
    }
    val shape = RoundedCornerShape(50)
    Box(modifier.height(6.dp).fillMaxWidth().clip(shape).background(Color(0x33FFFFFF))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(animated).clip(shape).background(color))
    }
}

private val ConfettiColors = listOf(Color(0xFFFDE047), Color(0xFF4ADE80), Color(0xFFFF383C), Color(0xFF60A5FA), Color(0xFFF472B6), Color(0xFFFFFFFF))

/**
 * Lluvia de confeti desde el centro. Se dispara cada vez que `trigger` cambia (null = nada) y dura
 * poco más de un segundo. No recibe toques.
 */
@Composable
internal fun ConfettiBurst(trigger: Any?, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) }
    var seed by remember { mutableIntStateOf(0) }
    LaunchedEffect(trigger) {
        if (trigger == null) return@LaunchedEffect
        seed = trigger.hashCode()
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1500, easing = LinearOutSlowInEasing))
    }
    Canvas(modifier.fillMaxSize()) {
        val p = progress.value
        if (p >= 1f) return@Canvas
        val rnd = java.util.Random(seed.toLong())
        repeat(42) { i ->
            val angle = rnd.nextFloat() * (2 * PI).toFloat()
            val speed = (0.25f + rnd.nextFloat() * 0.75f) * size.minDimension * 1.1f
            val x = center.x + cos(angle) * speed * p
            // Sale hacia arriba y luego cae por la gravedad.
            val y = center.y + sin(angle) * speed * p + size.height * 0.55f * p * p - size.height * 0.12f
            val w = 7.dp.toPx() * (0.6f + rnd.nextFloat())
            val spin = p * 360f * (rnd.nextFloat() - 0.5f) * 3f
            val color = ConfettiColors[i % ConfettiColors.size].copy(alpha = (1.2f - p).coerceIn(0f, 1f))
            rotate(degrees = spin, pivot = Offset(x, y)) {
                drawRect(color = color, topLeft = Offset(x - w / 2, y - w / 3), size = Size(w, w * 0.6f))
            }
        }
    }
}
