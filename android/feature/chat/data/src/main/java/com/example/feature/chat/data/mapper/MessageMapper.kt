package com.example.feature.chat.data.mapper

import com.example.feature.chat.data.dto.MessageDto
import com.example.feature.chat.domain.model.ChatMessage
import com.example.feature.chat.domain.model.MessageType
import java.util.UUID

fun MessageDto.toDomain(): ChatMessage = ChatMessage(
    id = UUID.randomUUID().toString(),
    type = when (type) {
        "join" -> MessageType.JOIN
        "leave" -> MessageType.LEAVE
        "system" -> MessageType.SYSTEM
        else -> MessageType.MESSAGE
    },
    userId = userId,
    username = username,
    content = content,
    timestamp = timestamp,
)
