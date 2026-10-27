package com.ternagames.terna.feature.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.ternagames.terna.feature.home.credits.CreditsScreen
import com.ternagames.terna.feature.home.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object SettingsRoute

@Serializable
data object CreditsRoute

fun NavGraphBuilder.homeScreen(onOpenSettings: () -> Unit, onOpenCredits: () -> Unit, onSignedOut: () -> Unit) {
    composable<HomeRoute> {
        HomeScreen(onOpenSettings = onOpenSettings, onOpenCredits = onOpenCredits, onSignedOut = onSignedOut)
    }
}

fun NavGraphBuilder.settingsScreen(onBack: () -> Unit, onOpenCredits: () -> Unit, onSignedOut: () -> Unit) {
    composable<SettingsRoute> { SettingsScreen(onBack = onBack, onOpenCredits = onOpenCredits, onSignedOut = onSignedOut) }
}

fun NavGraphBuilder.creditsScreen(onBack: () -> Unit, onOpenSettings: () -> Unit) {
    composable<CreditsRoute> { CreditsScreen(onBack = onBack, onOpenSettings = onOpenSettings) }
}
