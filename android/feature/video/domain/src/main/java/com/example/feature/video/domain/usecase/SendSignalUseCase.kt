package com.example.feature.video.domain.usecase

import com.example.feature.video.domain.model.SignalMessage
import com.example.feature.video.domain.repository.VideoRepository
import javax.inject.Inject

class SendSignalUseCase @Inject constructor(private val repository: VideoRepository) {
    suspend operator fun invoke(signal: SignalMessage): Boolean = repository.sendSignal(signal)
}
