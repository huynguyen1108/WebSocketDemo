package com.example.feature.auth.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginResponseDto(
    val token: String,
    val userId: String,
    val username: String = "",
)
