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
import com.ternagames.terna.feature.room.CreateRoomRoute
import com.ternagames.terna.feature.room.JoinRoomRoute
import com.ternagames.terna.feature.room.WaitingRoomRoute
import com.ternagames.terna.feature.room.createRoomScreen
import com.ternagames.terna.feature.room.joinRoomScreen
import com.ternagames.terna.feature.room.waitingRoomScreen

/**
 * Splash → Login → Home → (Crear | Unirse) → Sala de espera → Mesa.
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
            onSignedIn = { resumeRoomId -> navController.resetTo(resumeRoomId?.let(::WaitingRoomRoute) ?: HomeRoute) },
            onSignedOut = { navController.resetTo(LoginRoute) },
        )
        loginScreen(onSignedIn = { navController.resetTo(HomeRoute) })
        homeScreen(
            onCreateRoom = { navController.navigate(CreateRoomRoute) { launchSingleTop = true } },
            onJoinRoom = { navController.navigate(JoinRoomRoute) { launchSingleTop = true } },
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

        // Salas: crear o unirse llevan a la sala de espera, y "atrás" desde ella regresa al Home.
        createRoomScreen(onBack = navController::popBackStack, onCreated = { navController.enterWaitingRoom(it) })
        joinRoomScreen(onBack = navController::popBackStack, onJoined = { navController.enterWaitingRoom(it) })
        waitingRoomScreen(
            onExit = { navController.resetTo(HomeRoute) },
            onMatchStarted = { /* La mesa de juego se conecta en el siguiente paso. */ },
        )
    }
}

private fun NavHostController.enterWaitingRoom(roomId: String) = navigate(WaitingRoomRoute(roomId)) {
    popUpTo(HomeRoute)
    launchSingleTop = true
}

private fun NavHostController.resetTo(route: Any) = navigate(route) {
    popUpTo(graph.id) { inclusive = true }
    launchSingleTop = true
}
