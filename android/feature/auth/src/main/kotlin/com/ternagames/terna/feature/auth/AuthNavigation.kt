package com.ternagames.terna.feature.auth

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.ternagames.terna.feature.auth.login.LoginScreen
import com.ternagames.terna.feature.auth.splash.SplashScreen
import kotlinx.serialization.Serializable

@Serializable
data object SplashRoute

@Serializable
data object LoginRoute

fun NavGraphBuilder.splashScreen(onSignedIn: () -> Unit, onSignedOut: () -> Unit) {
    composable<SplashRoute> { SplashScreen(onSignedIn = onSignedIn, onSignedOut = onSignedOut) }
}

fun NavGraphBuilder.loginScreen(onSignedIn: () -> Unit) {
    composable<LoginRoute> { LoginScreen(onSignedIn = onSignedIn) }
}
