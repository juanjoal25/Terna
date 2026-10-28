package com.ternagames.terna.feature.room

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.ternagames.terna.feature.room.create.CreateRoomScreen
import com.ternagames.terna.feature.room.join.JoinRoomScreen
import com.ternagames.terna.feature.room.waiting.WaitingRoomScreen
import kotlinx.serialization.Serializable

@Serializable
data object CreateRoomRoute

@Serializable
data object JoinRoomRoute

@Serializable
data class WaitingRoomRoute(val roomId: String)

fun NavGraphBuilder.createRoomScreen(onBack: () -> Unit, onCreated: (roomId: String) -> Unit) {
    composable<CreateRoomRoute> { CreateRoomScreen(onBack = onBack, onCreated = onCreated) }
}

fun NavGraphBuilder.joinRoomScreen(onBack: () -> Unit, onJoined: (roomId: String) -> Unit) {
    composable<JoinRoomRoute> { JoinRoomScreen(onBack = onBack, onJoined = onJoined) }
}

fun NavGraphBuilder.waitingRoomScreen(onExit: () -> Unit, onMatchStarted: (matchId: String) -> Unit) {
    composable<WaitingRoomRoute> { WaitingRoomScreen(onExit = onExit, onMatchStarted = onMatchStarted) }
}
