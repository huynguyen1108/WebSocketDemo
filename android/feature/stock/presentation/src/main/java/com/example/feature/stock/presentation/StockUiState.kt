package com.example.feature.stock.presentation

import com.example.core.common.ConnectionState
import com.example.feature.stock.domain.model.MarketIndex
import com.example.feature.stock.domain.model.StockItem

data class StockUiState(
    val stocks: List<StockItem> = emptyList(),
    val indices: List<MarketIndex> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.Idle,
    val searchQuery: String = "",
    val serverUrl: String = "ws://10.0.2.2:8080",
    val sortMode: SortMode = SortMode.DEFAULT,
) {
    val filteredStocks: List<StockItem> get() {
        val base = if (searchQuery.isBlank()) stocks
        else stocks.filter {
            it.symbol.contains(searchQuery, ignoreCase = true) ||
                it.name.contains(searchQuery, ignoreCase = true)
        }
        return when (sortMode) {
            SortMode.DEFAULT -> base
            SortMode.CHANGE_DESC -> base.sortedByDescending { it.changePercent }
            SortMode.CHANGE_ASC -> base.sortedBy { it.changePercent }
            SortMode.PRICE_DESC -> base.sortedByDescending { it.price }
            SortMode.VOLUME_DESC -> base.sortedByDescending { it.volume }
        }
    }
}

enum class SortMode(val label: String) {
    DEFAULT("Mặc định"),
    CHANGE_DESC("Tăng mạnh nhất"),
    CHANGE_ASC("Giảm mạnh nhất"),
    PRICE_DESC("Giá cao nhất"),
    VOLUME_DESC("Khối lượng"),
}
