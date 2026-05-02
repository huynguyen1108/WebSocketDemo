package com.example.feature.video.domain.usecase

import com.example.core.common.ConnectionState
import com.example.feature.video.domain.repository.VideoRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveConnectionStateUseCase @Inject constructor(private val repository: VideoRepository) {
    operator fun invoke(): StateFlow<ConnectionState> = repository.connectionState
}
