package com.example.feature.stock.domain.usecase

import com.example.feature.stock.domain.repository.StockRepository
import javax.inject.Inject

class ConnectToMarketUseCase @Inject constructor(
    private val repository: StockRepository,
) {
    operator fun invoke(serverUrl: String) = repository.connect(serverUrl)
}
