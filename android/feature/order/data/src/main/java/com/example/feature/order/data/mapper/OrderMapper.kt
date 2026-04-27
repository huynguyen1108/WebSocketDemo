package com.example.feature.order.data.mapper

import com.example.feature.order.data.dto.OrderResponseDto
import com.example.feature.order.domain.model.Order
import com.example.feature.order.domain.model.OrderSide
import com.example.feature.order.domain.model.OrderStatus
import com.example.feature.order.domain.model.OrderType

fun OrderResponseDto.toDomain(): Order = Order(
    orderId = orderId,
    clientOrderId = clientOrderId,
    symbol = symbol,
    side = when (side.uppercase()) {
        "SELL" -> OrderSide.SELL
        else -> OrderSide.BUY
    },
    type = when (orderType.uppercase()) {
        "ATO" -> OrderType.ATO
        "ATC" -> OrderType.ATC
        "MP" -> OrderType.MP
        else -> OrderType.LO
    },
    price = price,
    quantity = quantity,
    matchedQuantity = matchedQuantity,
    matchedPrice = matchedPrice,
    status = when (status.uppercase()) {
        "PARTIALLY_MATCHED" -> OrderStatus.PARTIALLY_MATCHED
        "FULLY_MATCHED" -> OrderStatus.FULLY_MATCHED
        "CANCELLED" -> OrderStatus.CANCELLED
        "REJECTED" -> OrderStatus.REJECTED
        else -> OrderStatus.PENDING
    },
    createdAt = createdAt,
    updatedAt = updatedAt,
)
