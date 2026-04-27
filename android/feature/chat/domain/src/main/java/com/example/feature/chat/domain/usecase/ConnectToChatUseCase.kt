package com.example.feature.chat.domain.usecase

import com.example.feature.chat.domain.repository.ChatRepository
import javax.inject.Inject

class ConnectToChatUseCase @Inject constructor(
    private val repository: ChatRepository,
) {
    operator fun invoke(serverUrl: String, username: String) {
        repository.connect(serverUrl, username)
    }
}
