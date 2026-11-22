package com.yuval.podcasts.di

import android.content.Context
import coil.ImageLoader
import com.yuval.podcasts.BuildConfig
import com.yuval.podcasts.data.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
        val builder = OkHttpClient.Builder()
        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            builder.addInterceptor(loggingInterceptor)
        }
        return builder
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", Constants.HTTP_USER_AGENT)
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(Constants.NETWORK_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(Constants.NETWORK_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(Constants.NETWORK_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .okHttpClient(okHttpClient)
            .build()
    }
}
