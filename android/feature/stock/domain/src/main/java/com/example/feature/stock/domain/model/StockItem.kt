package com.example.feature.stock.domain.model

data class StockItem(
    val symbol: String,
    val name: String,
    val exchange: String,
    val price: Double,
    val reference: Double,
    val change: Double,
    val changePercent: Double,
    val open: Double,
    val high: Double,
    val low: Double,
    val ceiling: Double,
    val floor: Double,
    val volume: Long,
    val totalValue: Double,
) {
    val isUp get() = change > 0
    val isDown get() = change < 0
    val isCeiling get() = price >= ceiling
    val isFloor get() = price <= floor
}
