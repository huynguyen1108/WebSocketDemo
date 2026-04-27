package com.example.feature.auth.domain.model

data class AuthUser(
    val userId: String,
    val username: String,
    val token: String,
    val serverUrl: String,
)
