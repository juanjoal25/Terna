package com.ternagames.terna.di

import android.content.Context
import com.ternagames.terna.BuildConfig
import com.ternagames.terna.R
import com.ternagames.terna.core.data.di.FirebaseConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * `default_web_client_id` lo genera el plugin google-services a partir de google-services.json
     * (el oauth_client con client_type 3). Por eso solo existe en el módulo :app.
     */
    @Provides
    @Singleton
    fun firebaseConfig(@ApplicationContext context: Context) = FirebaseConfig(
        webClientId = context.getString(R.string.default_web_client_id),
        useEmulators = BuildConfig.USE_EMULATORS,
    )
}
