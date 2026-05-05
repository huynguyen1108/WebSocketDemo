package com.example.feature.video.domain.usecase

import com.example.feature.video.domain.repository.CallRole
import com.example.feature.video.domain.repository.VideoRepository
import javax.inject.Inject

class StartCallUseCase @Inject constructor(private val repository: VideoRepository) {
    /** Connect as caller, then wait for the callee to join the room. */
    operator fun invoke(serverUrl: String, roomId: String) {
        repository.connect(serverUrl, roomId, CallRole.CALLER)
    }
}
