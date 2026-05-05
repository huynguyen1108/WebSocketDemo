package com.example.feature.video.domain.model

sealed class VideoCallState {
    data object Idle : VideoCallState()
    data object Connecting : VideoCallState()

    // ── Caller side ───────────────────────────────────────────────────────────
    /** Connected as caller, waiting for callee to join the room. */
    data object WaitingForCallee : VideoCallState()
    /** Callee joined; their device is ringing. */
    data class Calling(val calleeName: String) : VideoCallState()

    // ── Callee side ───────────────────────────────────────────────────────────
    /** This device is being called. */
    data class Incoming(val callerName: String) : VideoCallState()

    // ── Both sides (after accept) ─────────────────────────────────────────────
    data class PeerReady(val peerName: String, val initiator: Boolean) : VideoCallState()
    data object InCall : VideoCallState()

    // ── Terminal outcomes ─────────────────────────────────────────────────────
    data class Rejected(val reason: String?) : VideoCallState()
    data object Cancelled : VideoCallState()
    data object NoAnswer : VideoCallState()
    data class Busy(val reason: String?) : VideoCallState()
    data class Error(val message: String) : VideoCallState()
}
