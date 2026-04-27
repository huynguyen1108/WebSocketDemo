package com.example.feature.order.data.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

@Serializable
data class TradingMessageDto(
    val type: String,
    val payload: JsonElement = JsonNull,
)

@Serializable
data class OrderRequestDto(
    val clientOrderId: String,
    val symbol: String,
    val side: String,
    val orderType: String,
    val price: Double,
    val quantity: Long,
)

@Serializable
data class OrderResponseDto(
    val orderId: String,
    val clientOrderId: String,
    val symbol: String,
    val side: String,
    val orderType: String,
    val price: Double,
    val quantity: Long,
    val matchedQuantity: Long = 0L,
    val matchedPrice: Double = 0.0,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class CancelOrderRequestDto(
    val orderId: String,
)
