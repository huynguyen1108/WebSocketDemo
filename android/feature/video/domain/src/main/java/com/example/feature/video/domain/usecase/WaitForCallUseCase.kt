package com.example.feature.video.domain.usecase

import com.example.feature.video.domain.repository.CallRole
import com.example.feature.video.domain.repository.VideoRepository
import javax.inject.Inject

class WaitForCallUseCase @Inject constructor(private val repository: VideoRepository) {
    /** Connect as callee — server replies "incoming" if a caller is waiting,
     *  otherwise "rejected" with reason="no incoming call". */
    operator fun invoke(serverUrl: String, roomId: String) {
        repository.connect(serverUrl, roomId, CallRole.CALLEE)
    }
}
