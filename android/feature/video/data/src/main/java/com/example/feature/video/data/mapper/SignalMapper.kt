package com.example.feature.video.data.mapper

import com.example.feature.video.data.dto.SignalDto
import com.example.feature.video.domain.model.SignalMessage

fun SignalDto.toDomain(): SignalMessage? = when (type) {
    // Server → Client
    "incoming" -> SignalMessage.Incoming(callerName = callerName ?: "")
    "ringing" -> SignalMessage.Ringing(calleeName = calleeName ?: "")
    "join" -> SignalMessage.Join(username = username ?: "", initiator = initiator ?: false)
    "rejected" -> SignalMessage.Rejected(reason)
    "cancelled" -> SignalMessage.Cancelled
    "timeout" -> SignalMessage.Timeout
    "busy" -> SignalMessage.Busy(reason)

    // Bidirectional (peer-to-peer relay)
    "offer" -> sdp?.let { SignalMessage.Offer(it) }
    "answer" -> sdp?.let { SignalMessage.Answer(it) }
    "ice" -> candidate?.let { SignalMessage.IceCandidate(it, sdpMid, sdpMLineIndex) }
    "leave" -> SignalMessage.Leave
    else -> null
}

fun SignalMessage.toDto(): SignalDto? = when (this) {
    // Client → Server control
    SignalMessage.Accept -> SignalDto(type = "accept")
    is SignalMessage.Reject -> SignalDto(type = "reject", reason = reason)
    SignalMessage.Cancel -> SignalDto(type = "cancel")

    // SDP / ICE
    is SignalMessage.Offer -> SignalDto(type = "offer", sdp = sdp)
    is SignalMessage.Answer -> SignalDto(type = "answer", sdp = sdp)
    is SignalMessage.IceCandidate -> SignalDto(type = "ice", candidate = candidate, sdpMid = sdpMid, sdpMLineIndex = sdpMLineIndex)
    SignalMessage.Leave -> SignalDto(type = "leave")

    // Server-only — never sent by client
    else -> null
}
