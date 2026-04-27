package com.example.feature.auth.data.di

import com.example.feature.auth.data.remote.AuthApiService
import com.example.feature.auth.data.repository.AuthRepositoryImpl
import com.example.feature.auth.domain.repository.AuthRepository
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthDataModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    companion object {

        @Provides
        @Singleton
        @Named("auth")
        fun provideAuthOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor { Timber.tag("AuthHttp").d(it) }
                    .apply { level = HttpLoggingInterceptor.Level.BODY },
            )
            .build()

        @Provides
        @Singleton
        fun provideAuthApiService(
            @Named("auth") okHttpClient: OkHttpClient,
            json: Json,
        ): AuthApiService = Retrofit.Builder()
            .baseUrl("http://localhost/")  // placeholder — overridden per call via @Url
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AuthApiService::class.java)
    }
}
