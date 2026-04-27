package com.example.feature.chat.domain.model

data class ChatMessage(
    val id: String,
    val type: MessageType,
    val userId: String,
    val username: String,
    val content: String,
    val timestamp: Long,
)

enum class MessageType { MESSAGE, JOIN, LEAVE, SYSTEM }
