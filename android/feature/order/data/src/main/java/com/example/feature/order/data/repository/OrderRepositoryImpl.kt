package com.example.feature.order.data.repository

import com.example.core.common.AppResult
import com.example.core.common.ConnectionState
import com.example.feature.order.data.datasource.OrderWebSocketDataSource
import com.example.feature.order.domain.model.Order
import com.example.feature.order.domain.model.OrderSide
import com.example.feature.order.domain.model.OrderType
import com.example.feature.order.domain.repository.OrderRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderRepositoryImpl @Inject constructor(
    private val dataSource: OrderWebSocketDataSource,
) : OrderRepository {

    override val connectionState: StateFlow<ConnectionState> = dataSource.connectionState
    override val orders: StateFlow<List<Order>> = dataSource.ordersListFlow

    override fun connect() = dataSource.connect()
    override fun disconnect() = dataSource.disconnect()

    override suspend fun placeOrder(
        symbol: String,
        side: OrderSide,
        type: OrderType,
        price: Double,
        quantity: Long,
    ): AppResult<String> = dataSource.placeOrder(symbol, side, type, price, quantity)

    override suspend fun cancelOrder(orderId: String): AppResult<Unit> =
        dataSource.cancelOrder(orderId)
}
