package com.example.feature.stock.domain.usecase

import com.example.core.common.ConnectionState
import com.example.feature.stock.domain.repository.StockRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveMarketConnectionUseCase @Inject constructor(
    private val repository: StockRepository,
) {
    operator fun invoke(): StateFlow<ConnectionState> = repository.connectionState
}
