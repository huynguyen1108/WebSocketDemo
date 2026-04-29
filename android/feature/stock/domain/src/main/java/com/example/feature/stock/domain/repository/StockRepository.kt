package com.example.feature.stock.domain.repository

import com.example.core.common.ConnectionState
import com.example.feature.stock.domain.model.MarketIndex
import com.example.feature.stock.domain.model.StockItem
import kotlinx.coroutines.flow.StateFlow

interface StockRepository {
    val connectionState: StateFlow<ConnectionState>
    val stocks: StateFlow<List<StockItem>>
    val indices: StateFlow<List<MarketIndex>>

    fun connect(serverUrl: String)
    fun disconnect()
}
