package com.example.feature.stock.domain.usecase

import com.example.feature.chat.domain.model.ConnectionState
import com.example.feature.stock.domain.repository.StockRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveMarketConnectionUseCase @Inject constructor(
    private val repository: StockRepository,
) {
    operator fun invoke(): StateFlow<ConnectionState> = repository.connectionState
}
