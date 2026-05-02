package com.example.feature.video.domain.model

sealed class VideoCallState {
    data object Idle : VideoCallState()
    data object Connecting : VideoCallState()
    data object WaitingForPeer : VideoCallState()
    data class PeerReady(val peerName: String, val initiator: Boolean) : VideoCallState()
    data object InCall : VideoCallState()
    data object Busy : VideoCallState()
    data class Error(val message: String) : VideoCallState()
}
