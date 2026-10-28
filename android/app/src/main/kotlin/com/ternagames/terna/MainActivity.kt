package com.ternagames.terna

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ternagames.terna.core.designsystem.audio.TernaAudio
import com.ternagames.terna.core.designsystem.theme.TernaTheme
import com.ternagames.terna.navigation.TernaNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        setContent {
            TernaTheme { TernaNavHost() }
        }
    }

    override fun onStart() {
        super.onStart()
        TernaAudio.get(this).setAppForeground(true)
    }

    override fun onStop() {
        TernaAudio.get(this).setAppForeground(false)
        super.onStop()
    }

    /** Pantalla completa de juego: las barras aparecen deslizando desde el borde. */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
