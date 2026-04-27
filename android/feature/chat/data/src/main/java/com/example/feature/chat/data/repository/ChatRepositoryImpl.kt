package com.example.feature.chat.data.repository

import com.example.core.common.AppResult
import com.example.feature.chat.data.datasource.ChatWebSocketDataSource
import com.example.feature.chat.domain.model.ChatMessage
import com.example.feature.chat.domain.model.ConnectionState
import com.example.feature.chat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val dataSource: ChatWebSocketDataSource,
) : ChatRepository {

    override val connectionState: StateFlow<ConnectionState> = dataSource.connectionState

    override val messages: Flow<ChatMessage> = dataSource.messages

    override fun connect(serverUrl: String, username: String) {
        dataSource.connect(serverUrl, username)
    }

    override suspend fun sendMessage(content: String): AppResult<Unit> {
        val sent = dataSource.sendMessage(content)
        return if (sent) AppResult.Success(Unit)
        else AppResult.Error("Not connected to server")
    }

    override fun disconnect() = dataSource.disconnect()
}
