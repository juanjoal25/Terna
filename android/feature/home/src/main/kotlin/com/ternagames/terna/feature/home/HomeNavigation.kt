package com.ternagames.terna.feature.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.ternagames.terna.feature.home.credits.CreditsScreen
import com.ternagames.terna.feature.home.profile.ProfileScreen
import com.ternagames.terna.feature.home.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object SettingsRoute

@Serializable
data object CreditsRoute

@Serializable
data object ProfileRoute

fun NavGraphBuilder.homeScreen(
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCredits: () -> Unit,
    onSignedOut: () -> Unit,
) {
    composable<HomeRoute> {
        HomeScreen(onOpenProfile = onOpenProfile, onOpenSettings = onOpenSettings, onOpenCredits = onOpenCredits, onSignedOut = onSignedOut)
    }
}

fun NavGraphBuilder.profileScreen(onBack: () -> Unit, onOpenSettings: () -> Unit, onOpenCredits: () -> Unit, onSignedOut: () -> Unit) {
    composable<ProfileRoute> {
        ProfileScreen(onBack = onBack, onOpenSettings = onOpenSettings, onOpenCredits = onOpenCredits, onSignedOut = onSignedOut)
    }
}

fun NavGraphBuilder.settingsScreen(onBack: () -> Unit, onOpenCredits: () -> Unit, onSignedOut: () -> Unit) {
    composable<SettingsRoute> { SettingsScreen(onBack = onBack, onOpenCredits = onOpenCredits, onSignedOut = onSignedOut) }
}

fun NavGraphBuilder.creditsScreen(onBack: () -> Unit, onOpenSettings: () -> Unit) {
    composable<CreditsRoute> { CreditsScreen(onBack = onBack, onOpenSettings = onOpenSettings) }
}
