package com.example.feature.auth.data.repository

import com.example.core.common.AppResult
import com.example.core.common.map
import com.example.core.security.TokenStore
import com.example.feature.auth.data.remote.AuthRemoteDataSource
import com.example.feature.auth.domain.model.AuthUser
import com.example.feature.auth.domain.repository.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton

class AuthRepositoryImpl @Inject constructor(
    private val remoteDataSource: AuthRemoteDataSource,
    private val tokenStore: TokenStore,
) : AuthRepository {

    override suspend fun login(
        serverUrl: String,
        username: String,
        password: String,
    ): AppResult<AuthUser> {
        val result = remoteDataSource.login(serverUrl, username, password)
        return result.map { dto ->
            tokenStore.saveSession(
                token = dto.token,
                userId = dto.userId,
                serverUrl = serverUrl,
            )
            AuthUser(
                userId = dto.userId,
                username = dto.username,
                token = dto.token,
                serverUrl = serverUrl,
            )
        }
    }

    override fun logout() = tokenStore.clearSession()

    override fun isLoggedIn(): Boolean = tokenStore.hasSession()

    override fun getToken(): String? = tokenStore.getToken()

    override fun getServerUrl(): String? = tokenStore.getServerUrl()
}
