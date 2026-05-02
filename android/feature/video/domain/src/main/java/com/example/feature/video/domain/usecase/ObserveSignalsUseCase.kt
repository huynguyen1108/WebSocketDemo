package com.example.feature.video.domain.usecase

import com.example.feature.video.domain.model.SignalMessage
import com.example.feature.video.domain.repository.VideoRepository
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject

class ObserveSignalsUseCase @Inject constructor(private val repository: VideoRepository) {
    operator fun invoke(): SharedFlow<SignalMessage> = repository.signals
}
