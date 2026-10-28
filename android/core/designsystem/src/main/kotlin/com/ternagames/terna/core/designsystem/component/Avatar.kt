package com.ternagames.terna.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ternagames.terna.core.designsystem.R
import com.ternagames.terna.core.designsystem.theme.TernaColors
import com.ternagames.terna.core.model.Avatars

/** Avatar que el jugador puede elegir. El [id] es el que se guarda en `users/{uid}.avatar`. */
data class AvatarOption(val id: String, val name: String, @param:DrawableRes val image: Int)

/** Mascotas de la fauna colombiana. Fuentes editables en `diseño/diseño app/avatares/svg/`. */
object TernaAvatars {
    val all = listOf(
        AvatarOption(Avatars.DEFAULT, "Jaguar", R.drawable.avatar_jaguar_01),
        AvatarOption("oso_01", "Oso de anteojos", R.drawable.avatar_oso_01),
        AvatarOption("condor_01", "Cóndor", R.drawable.avatar_condor_01),
        AvatarOption("tucan_01", "Tucán", R.drawable.avatar_tucan_01),
        AvatarOption("guacamaya_01", "Guacamaya", R.drawable.avatar_guacamaya_01),
        AvatarOption("rana_01", "Rana dorada", R.drawable.avatar_rana_01),
        AvatarOption("chiguiro_01", "Chigüiro", R.drawable.avatar_chiguiro_01),
        AvatarOption("perezoso_01", "Perezoso", R.drawable.avatar_perezoso_01),
        AvatarOption("delfin_01", "Delfín rosado", R.drawable.avatar_delfin_01),
    )

    /** IDs desconocidos (versiones futuras de la app) caen al jaguar. */
    @DrawableRes
    fun imageFor(id: String): Int = all.firstOrNull { it.id == id }?.image ?: R.drawable.avatar_jaguar_01
}

/**
 * Avatar redondo. Con [Avatars.PHOTO] muestra [photoUrl] (la foto de Google); si no hay
 * foto o no carga, se ve el jaguar. Los demás jugadores no conocen tu foto: ven el jaguar.
 */
@Composable
fun PlayerAvatar(
    avatarId: String,
    size: Dp,
    modifier: Modifier = Modifier,
    photoUrl: String? = null,
    borderWidth: Dp = 2.dp,
) {
    val shaped = modifier
        .size(size)
        .clip(CircleShape)
        .then(if (borderWidth > 0.dp) Modifier.border(borderWidth, TernaColors.Yellow, CircleShape) else Modifier)
    val fallback = painterResource(R.drawable.avatar_jaguar_01)

    if (avatarId == Avatars.PHOTO && photoUrl != null) {
        AsyncImage(
            model = photoUrl,
            contentDescription = "Avatar",
            contentScale = ContentScale.Crop,
            placeholder = fallback,
            error = fallback,
            modifier = shaped,
        )
    } else {
        Image(
            painter = painterResource(TernaAvatars.imageFor(avatarId)),
            contentDescription = "Avatar",
            contentScale = ContentScale.Crop,
            modifier = shaped,
        )
    }
}
