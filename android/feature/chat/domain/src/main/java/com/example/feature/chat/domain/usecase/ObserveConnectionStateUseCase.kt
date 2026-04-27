package com.example.feature.chat.domain.usecase

import com.example.feature.chat.domain.model.ConnectionState
import com.example.feature.chat.domain.repository.ChatRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveConnectionStateUseCase @Inject constructor(
    private val repository: ChatRepository,
) {
    operator fun invoke(): StateFlow<ConnectionState> = repository.connectionState
}
