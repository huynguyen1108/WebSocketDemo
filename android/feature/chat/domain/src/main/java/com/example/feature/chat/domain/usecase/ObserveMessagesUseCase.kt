package com.example.feature.chat.domain.usecase

import com.example.feature.chat.domain.model.ChatMessage
import com.example.feature.chat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveMessagesUseCase @Inject constructor(
    private val repository: ChatRepository,
) {
    operator fun invoke(): Flow<ChatMessage> = repository.messages
}
