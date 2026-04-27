package com.example.feature.chat.domain.usecase

import com.example.feature.chat.domain.repository.ChatRepository
import javax.inject.Inject

class DisconnectFromChatUseCase @Inject constructor(
    private val repository: ChatRepository,
) {
    operator fun invoke() = repository.disconnect()
}
