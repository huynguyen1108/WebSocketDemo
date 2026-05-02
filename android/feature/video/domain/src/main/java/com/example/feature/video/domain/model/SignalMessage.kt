package com.example.feature.video.domain.model

sealed class SignalMessage {
    data class Join(val username: String, val initiator: Boolean) : SignalMessage()
    data class Offer(val sdp: String) : SignalMessage()
    data class Answer(val sdp: String) : SignalMessage()
    data class IceCandidate(val candidate: String, val sdpMid: String?, val sdpMLineIndex: Int?) : SignalMessage()
    data object Leave : SignalMessage()
    data object Busy : SignalMessage()
}
