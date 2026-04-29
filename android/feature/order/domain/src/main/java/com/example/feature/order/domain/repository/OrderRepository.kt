package com.example.feature.order.domain.repository

import com.example.core.common.AppResult
import com.example.core.common.ConnectionState
import com.example.feature.order.domain.model.Order
import com.example.feature.order.domain.model.OrderSide
import com.example.feature.order.domain.model.OrderType
import kotlinx.coroutines.flow.StateFlow

interface OrderRepository {
    val connectionState: StateFlow<ConnectionState>
    val orders: StateFlow<List<Order>>

    fun connect()
    fun disconnect()
    suspend fun placeOrder(
        symbol: String,
        side: OrderSide,
        type: OrderType,
        price: Double,
        quantity: Long,
    ): AppResult<String>
    suspend fun cancelOrder(orderId: String): AppResult<Unit>
}
