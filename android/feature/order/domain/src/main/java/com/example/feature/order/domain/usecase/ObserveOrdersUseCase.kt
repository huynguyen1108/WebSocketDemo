package com.example.feature.order.domain.usecase

import com.example.feature.order.domain.model.Order
import com.example.feature.order.domain.repository.OrderRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveOrdersUseCase @Inject constructor(
    private val repository: OrderRepository,
) {
    operator fun invoke(): StateFlow<List<Order>> = repository.orders
}
