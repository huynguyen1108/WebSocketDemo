package com.example.feature.auth.domain.repository

import com.example.core.common.AppResult
import com.example.feature.auth.domain.model.AuthUser

interface AuthRepository {
    suspend fun login(serverUrl: String, username: String, password: String): AppResult<AuthUser>
    fun logout()
    fun isLoggedIn(): Boolean
    fun getToken(): String?
    fun getServerUrl(): String?
}
