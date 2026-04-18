package com.yuval.podcasts.di

import com.yuval.podcasts.data.network.PodcastApi
import com.yuval.podcasts.data.network.RssParser
import com.yuval.podcasts.data.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(Constants.NETWORK_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(Constants.NETWORK_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun providePodcastApi(
        okHttpClient: OkHttpClient,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): PodcastApi {
        return PodcastApi(okHttpClient, ioDispatcher)
    }

    @Provides
    @Singleton
    fun provideRssParser(): RssParser {
        return RssParser()
    }
}
