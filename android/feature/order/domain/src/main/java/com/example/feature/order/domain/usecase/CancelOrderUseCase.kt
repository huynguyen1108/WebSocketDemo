package com.example.feature.order.domain.usecase

import com.example.core.common.AppResult
import com.example.feature.order.domain.repository.OrderRepository
import javax.inject.Inject

class CancelOrderUseCase @Inject constructor(
    private val repository: OrderRepository,
) {
    suspend operator fun invoke(orderId: String): AppResult<Unit> =
        repository.cancelOrder(orderId)
}
