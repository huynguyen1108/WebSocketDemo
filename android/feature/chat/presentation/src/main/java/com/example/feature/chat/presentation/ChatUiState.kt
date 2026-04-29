package com.example.feature.chat.presentation

import com.example.feature.chat.domain.model.ChatMessage
import com.example.core.common.ConnectionState

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.Idle,
    val inputText: String = "",
    val serverUrl: String = "ws://10.0.2.2:8080",
    val username: String = "",
    val error: String? = null,
)
