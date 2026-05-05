package com.example.feature.video.domain.usecase

import com.example.feature.video.domain.model.SignalMessage
import com.example.feature.video.domain.repository.VideoRepository
import javax.inject.Inject

class RejectCallUseCase @Inject constructor(private val repository: VideoRepository) {
    suspend operator fun invoke(reason: String? = null): Boolean =
        repository.sendSignal(SignalMessage.Reject(reason))
}
