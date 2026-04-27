package com.example.feature.stock.data.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

// Sealed class parsed in a single pass — `type` field selects the subclass.
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class MarketEvent {
    @Serializable @SerialName("snapshot")
    data class Snapshot(val payload: SnapshotDto) : MarketEvent()

    @Serializable @SerialName("stock_tick")
    data class StockTick(val payload: StockDto) : MarketEvent()

    @Serializable @SerialName("index_tick")
    data class IndexTick(val payload: MarketIndexDto) : MarketEvent()
}

@Serializable
data class StockDto(
    val symbol: String,
    val name: String? = null,
    val exchange: String? = null,
    val price: Double,
    val reference: Double? = null,
    val change: Double,
    val changePercent: Double,
    val open: Double? = null,
    val high: Double? = null,
    val low: Double? = null,
    val ceiling: Double? = null,
    val floor: Double? = null,
    val volume: Long,
    val totalValue: Double? = null,
)

@Serializable
data class MarketIndexDto(
    val name: String,
    val value: Double,
    val change: Double,
    val changePercent: Double,
    val volume: Long,
    val advances: Int,
    val declines: Int,
    val noChanges: Int,
)

@Serializable
data class SnapshotDto(
    val stocks: List<StockDto>,
    val indices: List<MarketIndexDto>,
    val status: String,
)
