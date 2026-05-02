package com.example.feature.video.presentation

import com.example.feature.video.domain.model.VideoCallState

data class VideoUiState(
    val serverUrl: String = "",
    val roomId: String = "",
    val callState: VideoCallState = VideoCallState.Idle,
    val signalLog: List<String> = emptyList(),
)
