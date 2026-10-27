package com.ternagames.terna.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.ternagames.terna.feature.auth.LoginRoute
import com.ternagames.terna.feature.auth.SplashRoute
import com.ternagames.terna.feature.auth.loginScreen
import com.ternagames.terna.feature.auth.splashScreen
import com.ternagames.terna.feature.home.CreditsRoute
import com.ternagames.terna.feature.home.HomeRoute
import com.ternagames.terna.feature.home.SettingsRoute
import com.ternagames.terna.feature.home.creditsScreen
import com.ternagames.terna.feature.home.homeScreen
import com.ternagames.terna.feature.home.settingsScreen

/**
 * Splash → Login → Home (+ Ajustes y Créditos).
 * Al entrar o salir de la sesión se limpia la pila para que "atrás" no regrese al login.
 */
@Composable
fun TernaNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = SplashRoute,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
    ) {
        splashScreen(
            onSignedIn = { navController.resetTo(HomeRoute) },
            onSignedOut = { navController.resetTo(LoginRoute) },
        )
        loginScreen(onSignedIn = { navController.resetTo(HomeRoute) })
        homeScreen(
            onOpenSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } },
            onOpenCredits = { navController.navigate(CreditsRoute) { launchSingleTop = true } },
            onSignedOut = { navController.resetTo(LoginRoute) },
        )
        settingsScreen(
            onBack = navController::popBackStack,
            onOpenCredits = { navController.navigate(CreditsRoute) { popUpTo(HomeRoute); launchSingleTop = true } },
            onSignedOut = { navController.resetTo(LoginRoute) },
        )
        creditsScreen(
            onBack = navController::popBackStack,
            onOpenSettings = { navController.navigate(SettingsRoute) { popUpTo(HomeRoute); launchSingleTop = true } },
        )
    }
}

private fun NavHostController.resetTo(route: Any) = navigate(route) {
    popUpTo(graph.id) { inclusive = true }
    launchSingleTop = true
}
