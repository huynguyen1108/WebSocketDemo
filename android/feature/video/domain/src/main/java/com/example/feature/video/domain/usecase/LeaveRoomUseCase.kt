package com.example.feature.video.domain.usecase

import com.example.feature.video.domain.repository.VideoRepository
import javax.inject.Inject

class LeaveRoomUseCase @Inject constructor(private val repository: VideoRepository) {
    operator fun invoke() {
        repository.disconnect()
    }
}
