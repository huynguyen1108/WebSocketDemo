package com.example.feature.chat.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class MessageDto(
    val type: String,
    val userId: String,
    val username: String,
    val content: String,
    val timestamp: Long,
)
