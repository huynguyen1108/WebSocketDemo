package com.example.feature.stock.data.repository

import com.example.feature.chat.domain.model.ConnectionState
import com.example.feature.stock.data.datasource.StockWebSocketDataSource
import com.example.feature.stock.domain.model.MarketIndex
import com.example.feature.stock.domain.model.StockItem
import com.example.feature.stock.domain.repository.StockRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StockRepositoryImpl @Inject constructor(
    private val dataSource: StockWebSocketDataSource,
) : StockRepository {

    override val connectionState: StateFlow<ConnectionState> = dataSource.connectionState
    override val stocks: StateFlow<List<StockItem>> = dataSource.stocks
    override val indices: StateFlow<List<MarketIndex>> = dataSource.indices

    override fun connect(serverUrl: String) = dataSource.connect(serverUrl)
    override fun disconnect() = dataSource.disconnect()
}
