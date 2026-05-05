package com.example.feature.video.domain.model

sealed class SignalMessage {
    // ── Client → Server ───────────────────────────────────────────────────────
    data object Accept : SignalMessage()
    data class Reject(val reason: String? = null) : SignalMessage()
    data object Cancel : SignalMessage()
    data class Offer(val sdp: String) : SignalMessage()
    data class Answer(val sdp: String) : SignalMessage()
    data class IceCandidate(val candidate: String, val sdpMid: String?, val sdpMLineIndex: Int?) : SignalMessage()
    data object Leave : SignalMessage()

    // ── Server → Client ───────────────────────────────────────────────────────
    data class Incoming(val callerName: String) : SignalMessage()
    data class Ringing(val calleeName: String) : SignalMessage()
    data class Join(val username: String, val initiator: Boolean) : SignalMessage()
    data class Rejected(val reason: String?) : SignalMessage()
    data object Cancelled : SignalMessage()
    data object Timeout : SignalMessage()
    data class Busy(val reason: String?) : SignalMessage()
}
