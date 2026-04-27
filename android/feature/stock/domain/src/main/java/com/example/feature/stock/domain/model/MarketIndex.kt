package com.example.feature.stock.domain.model

data class MarketIndex(
    val name: String,
    val value: Double,
    val change: Double,
    val changePercent: Double,
    val volume: Long,
    val advances: Int,
    val declines: Int,
    val noChanges: Int,
) {
    val isUp get() = change > 0
    val isDown get() = change < 0
}
