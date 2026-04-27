package com.example.core.security.di

import com.example.core.security.TokenDataStore
import com.example.core.security.TokenStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    @Binds
    @Singleton
    abstract fun bindTokenStore(impl: TokenDataStore): TokenStore
}
