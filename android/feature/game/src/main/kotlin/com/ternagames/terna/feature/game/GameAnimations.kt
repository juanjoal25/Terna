package com.ternagames.terna.feature.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ternagames.terna.core.designsystem.component.PlayingCard
import com.ternagames.terna.core.designsystem.theme.TernaColors
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ───────────────────────── Dónde está cada cosa en la mesa ─────────────────────────

/**
 * Registro de las posiciones (en pantalla) de las piezas de la mesa: la mano de cada rival, los huecos del
 * centro, mi mano… Sirve para que las cartas vuelen desde donde salen hasta donde llegan.
 * Claves: `rival:<uid>`, `me`, `center:<hueco>`, `sets:me`.
 */
internal class TablePositions {
    private val rects = mutableStateMapOf<String, Rect>()

    fun set(key: String, rect: Rect) {
        if (rects[key] != rect) rects[key] = rect
    }

    operator fun get(key: String): Rect? = rects[key]
}

internal val LocalTablePositions = staticCompositionLocalOf { TablePositions() }

@Composable
internal fun ProvideTablePositions(content: @Composable () -> Unit) {
    val positions = remember { TablePositions() }
    CompositionLocalProvider(LocalTablePositions provides positions, content = content)
}

/** Apunta la posición de este elemento en el registro de la mesa. */
@Composable
internal fun Modifier.trackPosition(key: String): Modifier {
    val positions = LocalTablePositions.current
    return this.onGloballyPositioned { positions.set(key, it.boundsInRoot()) }
}

// ───────────────────────── Cartas ─────────────────────────

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

/**
 * El contenido viaja desde la pieza `from` (según el registro de posiciones) hasta su sitio: sale pequeño,
 * sube en arco y aterriza con su tamaño. Sin origen conocido, aparece con un rebote.
 */
@Composable
internal fun FlyIn(from: String?, modifier: Modifier = Modifier, delayMs: Long = 0, content: @Composable () -> Unit) {
    val positions = LocalTablePositions.current
    val progress = remember { Animatable(0f) }
    var own by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(own != null) {
        if (own == null) return@LaunchedEffect
        delay(delayMs)
        progress.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
    }
    Box(
        modifier
            .onGloballyPositioned { if (own == null) own = it.boundsInRoot() }
            .graphicsLayer {
                val p = progress.value
                val mine = own
                val source = from?.let { positions[it] }
                when {
                    mine == null -> alpha = 0f
                    source == null -> {
                        val s = 0.55f + 0.45f * p
                        scaleX = s
                        scaleY = s
                        alpha = p.coerceIn(0f, 1f)
                        translationY = (1f - p) * -28f
                    }
                    else -> {
                        translationX = (source.center.x - mine.center.x) * (1f - p)
                        // Sube en arco a mitad del vuelo.
                        translationY = (source.center.y - mine.center.y) * (1f - p) - sin(PI.toFloat() * p) * 70f
                        val s = 0.45f + 0.55f * p
                        scaleX = s
                        scaleY = s
                        rotationZ = (1f - p) * 14f
                        alpha = (p * 4f).coerceIn(0f, 1f)
                    }
                }
            },
    ) { content() }
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

// ───────────────────────── Pulsos y avisos ─────────────────────────

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

/**
 * Foco que viaja de un jugador a otro cuando cambia el turno: un marco brillante alrededor de quien juega.
 * `activeKey` es la clave del registro (`me` o `rival:<uid>`).
 */
@Composable
internal fun TurnSpotlight(activeKey: String?, modifier: Modifier = Modifier) {
    val positions = LocalTablePositions.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val target = activeKey?.let { positions[it] }

    Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        // Se crea ya en el sitio del primer jugador (así no cruza la pantalla al empezar) y desde ahí se desliza.
        if (target != null) SpotlightFrame(target, origin)
    }
}

@Composable
private fun SpotlightFrame(target: Rect, origin: Offset) {
    val spec = spring<Float>(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)
    val left by animateFloatAsState(target.left, spec, label = "spotL")
    val top by animateFloatAsState(target.top, spec, label = "spotT")
    val width by animateFloatAsState(target.width, spec, label = "spotW")
    val height by animateFloatAsState(target.height, spec, label = "spotH")
    val pulse by rememberPulse(0.35f, 1f, 700)
    val density = LocalDensity.current
    val pad = with(density) { 5.dp.toPx() }

    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .offset { IntOffset((left - origin.x - pad).roundToInt(), (top - origin.y - pad).roundToInt()) }
            .size(with(density) { (width + 2 * pad).toDp() }, with(density) { (height + 2 * pad).toDp() })
            .border(7.dp, TernaColors.Yellow.copy(alpha = 0.14f * pulse), shape)
            .border(3.dp, TernaColors.Yellow.copy(alpha = 0.45f + 0.55f * pulse), shape),
    )
}

/** Texto que sube y se desvanece sobre una pieza de la mesa ("-1000 AURA"). */
@Composable
internal fun FloatingText(trigger: Long?, text: String, anchorKey: String?, color: Color, modifier: Modifier = Modifier) {
    val positions = LocalTablePositions.current
    val progress = remember { Animatable(1f) }
    var anchor by remember { mutableStateOf<Rect?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    LaunchedEffect(trigger) {
        if (trigger == null || trigger <= 0) return@LaunchedEffect
        anchor = anchorKey?.let { positions[it] }
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1500, easing = LinearOutSlowInEasing))
    }
    Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        val a = anchor
        val p = progress.value
        if (a != null && p < 1f) {
            val half = with(density) { 90.dp.toPx() }
            val rise = with(density) { 70.dp.toPx() }
            Text(
                text,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold),
                color = color,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .width(180.dp)
                    .offset { IntOffset((a.center.x - origin.x - half).roundToInt(), (a.center.y - origin.y - rise * p).roundToInt()) }
                    .graphicsLayer {
                        val pop = if (p < 0.15f) p / 0.15f else 1f
                        scaleX = 0.6f + 0.5f * pop
                        scaleY = scaleX
                        alpha = (1f - p * p).coerceIn(0f, 1f)
                    },
            )
        }
    }
}

/** Destello rojo en los bordes de la pantalla cuando fallas. */
@Composable
internal fun DamageFlash(trigger: Long, modifier: Modifier = Modifier) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger <= 0) return@LaunchedEffect
        alpha.snapTo(0.5f)
        alpha.animateTo(0f, tween(700))
    }
    if (alpha.value > 0.01f) {
        Box(modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.Transparent, TernaColors.Danger.copy(alpha = alpha.value)))))
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
