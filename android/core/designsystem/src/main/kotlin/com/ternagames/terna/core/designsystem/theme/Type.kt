package com.ternagames.terna.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.ternagames.terna.core.designsystem.R

// Fuentes descargables de Google Fonts: no pesan en el APK y, si no hay conexión,
// se usa la fuente del sistema. Confirmar con Miguel los nombres exactos del Figma.
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

/** Logo "TERNA!": redondeada y gruesa. */
val LogoFont = FontFamily(Font(GoogleFont("Lilita One"), provider))

/** Títulos condensados ("SALA DE MIGUEL", "¡Iniciando Partida!"). */
val TitleFont = FontFamily(
    Font(GoogleFont("Roboto Condensed"), provider, FontWeight.Bold),
    Font(GoogleFont("Roboto Condensed"), provider, FontWeight.ExtraBold),
)

/** Texto y botones. */
val BodyFont = FontFamily(
    Font(GoogleFont("Inter"), provider, FontWeight.Normal),
    Font(GoogleFont("Inter"), provider, FontWeight.Medium),
    Font(GoogleFont("Inter"), provider, FontWeight.Bold),
    Font(GoogleFont("Inter"), provider, FontWeight.ExtraBold),
)

val TernaTypography = Typography(
    displayLarge = TextStyle(fontFamily = LogoFont, fontSize = 72.sp),
    headlineLarge = TextStyle(fontFamily = TitleFont, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp),
    headlineMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = BodyFont, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = BodyFont, fontSize = 13.sp),
    bodySmall = TextStyle(fontFamily = BodyFont, fontSize = 11.sp),
    labelLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp),
    labelSmall = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 9.sp),
)
