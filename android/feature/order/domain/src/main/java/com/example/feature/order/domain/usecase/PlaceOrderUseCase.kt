package com.example.feature.order.domain.usecase

import com.example.core.common.AppResult
import com.example.feature.order.domain.model.OrderSide
import com.example.feature.order.domain.model.OrderType
import com.example.feature.order.domain.repository.OrderRepository
import javax.inject.Inject

class PlaceOrderUseCase @Inject constructor(
    private val repository: OrderRepository,
) {
    suspend operator fun invoke(
        symbol: String,
        side: OrderSide,
        type: OrderType,
        price: Double,
        quantity: Long,
        ceiling: Double = Double.MAX_VALUE,
        floor: Double = 0.0,
    ): AppResult<String> {
        if (symbol.isBlank()) return AppResult.Error("Mã cổ phiếu không được để trống")
        if (quantity <= 0) return AppResult.Error("Khối lượng phải lớn hơn 0")
        if (quantity % 100 != 0L) return AppResult.Error("Khối lượng phải là bội số của 100")
        if (type == OrderType.LO || type == OrderType.MP) {
            if (price <= 0) return AppResult.Error("Giá phải lớn hơn 0")
            if (price < floor) return AppResult.Error("Giá thấp hơn giá sàn ($floor)")
            if (price > ceiling) return AppResult.Error("Giá cao hơn giá trần ($ceiling)")
        }
        return repository.placeOrder(symbol.uppercase(), side, type, price, quantity)
    }
}
