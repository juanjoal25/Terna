package com.ternagames.terna.feature.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ternagames.terna.core.data.auth.AuthRepository
import com.ternagames.terna.core.data.user.UserRepository
import com.ternagames.terna.core.model.PlayerProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object SignedOut : HomeUiState
    data class Ready(val profile: PlayerProfile?, val photoUrl: String? = null) : HomeUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    auth: AuthRepository,
    users: UserRepository,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = auth.session
        .flatMapLatest { session ->
            if (session == null) {
                flowOf(HomeUiState.SignedOut)
            } else {
                users.observeProfile(session.uid)
                    // Si el splash no pudo crear el perfil (p. ej. sin red), se reintenta aquí.
                    .onStart { runCatching { users.ensureProfile(session) } }
                    .map<PlayerProfile?, HomeUiState> { HomeUiState.Ready(it, session.photoUrl) }
                    .catch {
                        Log.w("Home", "No se pudo leer el perfil", it)
                        emit(HomeUiState.Ready(null, session.photoUrl))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)
}
