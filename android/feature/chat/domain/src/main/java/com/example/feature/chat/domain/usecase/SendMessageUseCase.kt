package com.example.feature.chat.domain.usecase

import com.example.core.common.AppResult
import com.example.feature.chat.domain.repository.ChatRepository
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val repository: ChatRepository,
) {
    suspend operator fun invoke(content: String): AppResult<Unit> {
        if (content.isBlank()) return AppResult.Error("Message cannot be empty")
        return repository.sendMessage(content.trim())
    }
}
