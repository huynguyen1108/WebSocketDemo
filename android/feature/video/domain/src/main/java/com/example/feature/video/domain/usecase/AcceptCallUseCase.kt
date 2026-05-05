package com.example.feature.video.domain.usecase

import com.example.feature.video.domain.model.SignalMessage
import com.example.feature.video.domain.repository.VideoRepository
import javax.inject.Inject

class AcceptCallUseCase @Inject constructor(private val repository: VideoRepository) {
    suspend operator fun invoke(): Boolean = repository.sendSignal(SignalMessage.Accept)
}
