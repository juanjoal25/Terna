package com.ternagames.terna.feature.game

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data class GameRoute(val matchId: String)

fun NavGraphBuilder.gameScreen(onBackToRoom: (roomId: String) -> Unit, onGoHome: () -> Unit) {
    composable<GameRoute> { GameScreen(onBackToRoom = onBackToRoom, onGoHome = onGoHome) }
}
