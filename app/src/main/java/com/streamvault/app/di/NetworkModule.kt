package com.streamvault.app.di

import android.content.Context
import com.streamvault.data.remote.xtream.XtreamApiService
import com.streamvault.data.parser.XmltvParser
import com.streamvault.player.Media3PlayerEngine
import com.streamvault.player.PlayerEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            .followRedirects(true)
            .followSslRedirects(true)
            .connectionPool(okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES)) // Allow more idle connections
            .dispatcher(okhttp3.Dispatcher().apply {
                maxRequests = 64
                maxRequestsPerHost = 10 // Increase host limit for Multi-View
            })
            .build()

    @Provides
    @Singleton
    fun provideXtreamApiService(okHttpClient: OkHttpClient, gson: Gson): XtreamApiService =
        Retrofit.Builder()
            // Base URL will be overridden per-request by the provider
            .baseUrl("https://placeholder.invalid/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(XtreamApiService::class.java)

    @Provides
    @Singleton
    fun provideXmltvParser(): XmltvParser = XmltvParser()

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    /**
     * Factory binding for PlayerEngine (NOT @Singleton).
     * This allows MultiViewViewModel to inject Provider<PlayerEngine>
     * and create up to 4 independent instances for split-screen playback.
     */
    @Provides
    fun providePlayerEngine(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient
    ): PlayerEngine = Media3PlayerEngine(context, okHttpClient)
}
