package com.yuval.podcasts.di

import com.yuval.podcasts.data.network.PodcastApi
import com.yuval.podcasts.data.network.RssParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://example.com/") // Base URL is required but we use @Url
            .client(okHttpClient)
            .build()
    }

    @Provides
    @Singleton
    fun providePodcastApi(retrofit: Retrofit): PodcastApi {
        return retrofit.create(PodcastApi::class.java)
    }

    @Provides
    @Singleton
    fun provideRssParser(): RssParser {
        return RssParser()
    }
}