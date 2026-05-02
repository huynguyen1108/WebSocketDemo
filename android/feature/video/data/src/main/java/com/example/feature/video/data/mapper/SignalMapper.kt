package com.example.feature.video.data.mapper

import com.example.feature.video.data.dto.SignalDto
import com.example.feature.video.domain.model.SignalMessage

fun SignalDto.toDomain(): SignalMessage? = when (type) {
    "join" -> SignalMessage.Join(username = username ?: "", initiator = initiator ?: false)
    "offer" -> sdp?.let { SignalMessage.Offer(it) }
    "answer" -> sdp?.let { SignalMessage.Answer(it) }
    "ice" -> candidate?.let { SignalMessage.IceCandidate(it, sdpMid, sdpMLineIndex) }
    "leave" -> SignalMessage.Leave
    "busy" -> SignalMessage.Busy
    else -> null
}

fun SignalMessage.toDto(): SignalDto? = when (this) {
    is SignalMessage.Offer -> SignalDto(type = "offer", sdp = sdp)
    is SignalMessage.Answer -> SignalDto(type = "answer", sdp = sdp)
    is SignalMessage.IceCandidate -> SignalDto(type = "ice", candidate = candidate, sdpMid = sdpMid, sdpMLineIndex = sdpMLineIndex)
    else -> null // Join/Leave/Busy are server-generated, never sent by client
}
