package com.example.core.network.di

import com.example.core.network.websocket.OkHttpWebSocketClient
import com.example.core.network.websocket.WebSocketClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .pingInterval(30, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @Named("market")
    fun provideMarketWebSocketClient(okHttpClient: OkHttpClient): WebSocketClient =
        OkHttpWebSocketClient(okHttpClient)

    @Provides
    @Singleton
    @Named("chat")
    fun provideChatWebSocketClient(okHttpClient: OkHttpClient): WebSocketClient =
        OkHttpWebSocketClient(okHttpClient)

    @Provides
    @Singleton
    @Named("trading")
    fun provideTradingWebSocketClient(okHttpClient: OkHttpClient): WebSocketClient =
        OkHttpWebSocketClient(okHttpClient)
}
