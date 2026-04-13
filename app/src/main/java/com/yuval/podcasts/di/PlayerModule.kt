package com.yuval.podcasts.di

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.cast.CastPlayer
import com.google.android.gms.cast.framework.CastContext
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

import com.yuval.podcasts.data.repository.SettingsRepository

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @Provides
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository
    ): ExoPlayer {
        return ExoPlayer.Builder(context).build().apply {
            setSkipSilenceEnabled(settingsRepository.isSkipSilenceEnabled())
        }
    }

    @Provides
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun provideCastPlayer(@ApplicationContext context: Context): CastPlayer {
        // CastContext is initialized via the OptionsProvider in AndroidManifest.xml
        @Suppress("DEPRECATION")
        return CastPlayer(CastContext.getSharedInstance(context))
    }
}
