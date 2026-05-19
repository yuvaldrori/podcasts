package com.yuval.podcasts.di

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.cast.CastPlayer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import dagger.hilt.android.components.ServiceComponent

@Module
@InstallIn(ServiceComponent::class)
object PlayerModule {

    @Provides
    @ServiceScoped
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun provideExoPlayer(
        @ApplicationContext context: Context
    ): ExoPlayer {
        return ExoPlayer.Builder(context).build()
    }

    @Provides
    @ServiceScoped
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun provideCastPlayer(
        @ApplicationContext context: Context,
        exoPlayer: ExoPlayer
    ): CastPlayer {
        return CastPlayer.Builder(context)
            .setLocalPlayer(exoPlayer)
            .build()
    }
}
