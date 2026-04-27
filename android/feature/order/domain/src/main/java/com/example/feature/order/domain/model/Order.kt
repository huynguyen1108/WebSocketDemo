package com.example.feature.order.domain.model

data class Order(
    val orderId: String,
    val clientOrderId: String,
    val symbol: String,
    val side: OrderSide,
    val type: OrderType,
    val price: Double,
    val quantity: Long,
    val matchedQuantity: Long = 0L,
    val matchedPrice: Double = 0.0,
    val status: OrderStatus,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val remainingQuantity: Long get() = quantity - matchedQuantity
    val isActive: Boolean get() = status == OrderStatus.PENDING || status == OrderStatus.PARTIALLY_MATCHED
}

enum class OrderSide { BUY, SELL }

enum class OrderType(val label: String) {
    LO("Giới hạn (LO)"),
    ATO("Mở cửa (ATO)"),
    ATC("Đóng cửa (ATC)"),
    MP("Thị trường (MP)"),
}

enum class OrderStatus(val label: String) {
    PENDING("Chờ khớp"),
    PARTIALLY_MATCHED("Khớp một phần"),
    FULLY_MATCHED("Đã khớp"),
    CANCELLED("Đã huỷ"),
    REJECTED("Bị từ chối"),
}
