package com.ternagames.terna.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ternagames.terna.core.designsystem.R
import com.ternagames.terna.core.designsystem.theme.TernaColors

/** Fondos ilustrados del Figma. */
enum class TernaBackdrop(@param:DrawableRes val image: Int) {
    /** Valle cafetero: splash y login. */
    Valley(R.drawable.bg_valle),
    /** Atardecer con cartas doradas: home, crear y unirse. */
    Home(R.drawable.bg_home),
    /** Azul con destellos: sala de espera. */
    Lobby(R.drawable.bg_lobby),
    /** Mesa de madera con "TERNA" grabado: pantalla de juego. */
    Table(R.drawable.bg_mesa),
}

/** Fondo a pantalla completa con un velo para que el texto se lea. */
@Composable
fun TernaBackground(
    backdrop: TernaBackdrop,
    modifier: Modifier = Modifier,
    scrim: Brush = Brush.verticalGradient(listOf(Color(0x33000000), Color(0x80000000))),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        Image(
            painter = painterResource(backdrop.image),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(scrim))
        content()
    }
}

/** Panel oscuro con borde amarillo: la tarjeta base de todas las pantallas. */
@Composable
fun TernaPanel(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(TernaColors.Panel)
            .border(1.5.dp, TernaColors.Yellow, shape)
            .padding(contentPadding),
        content = content,
    )
}
