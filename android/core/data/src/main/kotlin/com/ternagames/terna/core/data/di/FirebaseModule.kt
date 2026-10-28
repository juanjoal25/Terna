package com.ternagames.terna.core.data.di

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.database
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.functions
import com.ternagames.terna.core.data.auth.AuthRepository
import com.ternagames.terna.core.data.auth.FirebaseAuthRepository
import com.ternagames.terna.core.data.match.FirebaseMatchRepository
import com.ternagames.terna.core.data.match.MatchRepository
import com.ternagames.terna.core.data.room.FirebaseRoomRepository
import com.ternagames.terna.core.data.room.RoomRepository
import com.ternagames.terna.core.data.user.FirestoreUserRepository
import com.ternagames.terna.core.data.user.UserRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Datos que solo conoce el módulo :app (vienen de google-services.json y de BuildConfig). */
data class FirebaseConfig(
    /** ID de cliente web OAuth (`default_web_client_id`) para "Iniciar sesión con Google". */
    val webClientId: String,
    /** Si es true, todos los SDK apuntan a los emuladores locales en [emulatorHost]. */
    val useEmulators: Boolean,
    val emulatorHost: String = "10.0.2.2",
    /** Se suma a los puertos de los emuladores (9099, 9000, 8080, 5001): permite correr varios a la vez. */
    val emulatorPortOffset: Int = 0,
)

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun auth(config: FirebaseConfig): FirebaseAuth = Firebase.auth.apply {
        if (config.useEmulators) useEmulator(config.emulatorHost, 9099 + config.emulatorPortOffset)
    }

    @Provides
    @Singleton
    fun firestore(config: FirebaseConfig): FirebaseFirestore = Firebase.firestore.apply {
        if (config.useEmulators) useEmulator(config.emulatorHost, 8080 + config.emulatorPortOffset)
    }

    @Provides
    @Singleton
    fun database(config: FirebaseConfig): FirebaseDatabase = Firebase.database.apply {
        if (config.useEmulators) useEmulator(config.emulatorHost, 9000 + config.emulatorPortOffset)
    }

    /** Misma región que las Cloud Functions (us-central1). */
    @Provides
    @Singleton
    fun functions(config: FirebaseConfig): FirebaseFunctions = Firebase.functions("us-central1").apply {
        if (config.useEmulators) useEmulator(config.emulatorHost, 5001 + config.emulatorPortOffset)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun authRepository(impl: FirebaseAuthRepository): AuthRepository

    @Binds
    abstract fun userRepository(impl: FirestoreUserRepository): UserRepository

    @Binds
    abstract fun roomRepository(impl: FirebaseRoomRepository): RoomRepository

    @Binds
    abstract fun matchRepository(impl: FirebaseMatchRepository): MatchRepository
}
