package com.example.core.security

interface TokenStore {
    fun saveSession(token: String, userId: String, serverUrl: String)
    fun getToken(): String?
    fun getUserId(): String?
    fun getServerUrl(): String?
    fun clearSession()
    fun hasSession(): Boolean
}

fun TokenStore.authHeaders(): Map<String, String> =
    getToken()?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()
