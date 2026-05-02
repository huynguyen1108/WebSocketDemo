package com.example.feature.video.domain.usecase

import com.example.feature.video.domain.repository.VideoRepository
import javax.inject.Inject

class JoinRoomUseCase @Inject constructor(private val repository: VideoRepository) {
    operator fun invoke(serverUrl: String, roomId: String) {
        repository.connect(serverUrl, roomId)
    }
}
