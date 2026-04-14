package com.yuval.podcasts.di

import com.yuval.podcasts.data.repository.DefaultPodcastRepository
import com.yuval.podcasts.data.repository.PodcastRepository
import com.yuval.podcasts.utils.DefaultNetworkMonitor
import com.yuval.podcasts.utils.NetworkMonitor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPodcastRepository(
        defaultPodcastRepository: DefaultPodcastRepository
    ): PodcastRepository

    @Binds
    @Singleton
    abstract fun bindNetworkMonitor(
        defaultNetworkMonitor: DefaultNetworkMonitor
    ): NetworkMonitor
}
