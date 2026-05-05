package com.example.feature.video.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class SignalDto(
    val type: String,
    val username: String? = null,
    val initiator: Boolean? = null,
    val sdp: String? = null,
    val candidate: String? = null,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null,
    val callerName: String? = null,
    val calleeName: String? = null,
    val reason: String? = null,
)
