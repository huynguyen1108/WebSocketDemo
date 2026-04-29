package com.example.feature.order.domain.usecase

import com.example.core.common.ConnectionState
import com.example.feature.order.domain.repository.OrderRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveTradingConnectionUseCase @Inject constructor(
    private val repository: OrderRepository,
) {
    operator fun invoke(): StateFlow<ConnectionState> = repository.connectionState
}
