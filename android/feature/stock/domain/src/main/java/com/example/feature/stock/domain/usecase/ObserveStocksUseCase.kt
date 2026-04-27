package com.example.feature.stock.domain.usecase

import com.example.feature.stock.domain.model.StockItem
import com.example.feature.stock.domain.repository.StockRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveStocksUseCase @Inject constructor(
    private val repository: StockRepository,
) {
    operator fun invoke(): StateFlow<List<StockItem>> = repository.stocks
}
