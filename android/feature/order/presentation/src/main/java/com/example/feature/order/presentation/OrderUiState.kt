package com.example.feature.order.presentation

import com.example.core.common.ConnectionState
import com.example.feature.order.domain.model.Order
import com.example.feature.order.domain.model.OrderSide
import com.example.feature.order.domain.model.OrderStatus
import com.example.feature.order.domain.model.OrderType

data class OrderUiState(
    val connectionState: ConnectionState = ConnectionState.Idle,
    val orders: List<Order> = emptyList(),
    val activeTab: OrderTab = OrderTab.ACTIVE,
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
    val successMessage: String? = null,
) {
    val activeOrders: List<Order> get() = orders.filter { it.isActive }
    val historyOrders: List<Order> get() = orders.filter { !it.isActive }
    val displayedOrders: List<Order> get() = if (activeTab == OrderTab.ACTIVE) activeOrders else historyOrders
}

data class OrderFormState(
    val symbol: String = "",
    val side: OrderSide = OrderSide.BUY,
    val type: OrderType = OrderType.LO,
    val priceText: String = "",
    val quantityText: String = "",
    val ceiling: Double = Double.MAX_VALUE,
    val floor: Double = 0.0,
) {
    val price: Double get() = priceText.toDoubleOrNull() ?: 0.0
    val quantity: Long get() = quantityText.toLongOrNull() ?: 0L
    val isPriceEnabled: Boolean get() = type == OrderType.LO || type == OrderType.MP
}

enum class OrderTab(val label: String) { ACTIVE("Đang hoạt động"), HISTORY("Lịch sử") }

fun OrderStatus.color() = when (this) {
    OrderStatus.PENDING -> "yellow"
    OrderStatus.PARTIALLY_MATCHED -> "orange"
    OrderStatus.FULLY_MATCHED -> "green"
    OrderStatus.CANCELLED -> "gray"
    OrderStatus.REJECTED -> "red"
}
