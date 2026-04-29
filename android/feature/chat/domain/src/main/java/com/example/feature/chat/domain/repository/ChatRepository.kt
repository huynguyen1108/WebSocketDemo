package com.example.feature.chat.domain.repository

import com.example.core.common.AppResult
import com.example.feature.chat.domain.model.ChatMessage
import com.example.core.common.ConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ChatRepository {
    val connectionState: StateFlow<ConnectionState>
    val messages: Flow<ChatMessage>

    fun connect(serverUrl: String, username: String)
    suspend fun sendMessage(content: String): AppResult<Unit>
    fun disconnect()
}
