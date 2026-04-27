package com.example.feature.order.domain.usecase

import com.example.feature.order.domain.repository.OrderRepository
import javax.inject.Inject

class ConnectToTradingUseCase @Inject constructor(
    private val repository: OrderRepository,
) {
    operator fun invoke() = repository.connect()
}
