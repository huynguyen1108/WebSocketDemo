package com.example.feature.auth.presentation

data class LoginUiState(
    val serverUrl: String = "ws://10.0.2.2:8080",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)
