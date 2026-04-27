package com.example.feature.order.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.AppResult
import com.example.feature.order.domain.model.OrderSide
import com.example.feature.order.domain.model.OrderType
import com.example.feature.order.domain.repository.OrderRepository
import com.example.feature.order.domain.usecase.CancelOrderUseCase
import com.example.feature.order.domain.usecase.ConnectToTradingUseCase
import com.example.feature.order.domain.usecase.ObserveOrdersUseCase
import com.example.feature.order.domain.usecase.ObserveTradingConnectionUseCase
import com.example.feature.order.domain.usecase.PlaceOrderUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OrderViewModel @Inject constructor(
    private val connectUseCase: ConnectToTradingUseCase,
    private val placeOrderUseCase: PlaceOrderUseCase,
    private val cancelOrderUseCase: CancelOrderUseCase,
    private val observeOrdersUseCase: ObserveOrdersUseCase,
    private val observeConnectionUseCase: ObserveTradingConnectionUseCase,
    private val repository: OrderRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrderUiState())
    val uiState: StateFlow<OrderUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(OrderFormState())
    val formState: StateFlow<OrderFormState> = _formState.asStateFlow()

    init {
        observeOrdersUseCase().onEach { orders ->
            _uiState.update { it.copy(orders = orders) }
        }.launchIn(viewModelScope)

        observeConnectionUseCase().onEach { state ->
            _uiState.update { it.copy(connectionState = state) }
        }.launchIn(viewModelScope)

        connectUseCase()
    }

    fun connect() = connectUseCase()
    fun disconnect() = repository.disconnect()

    fun onTabChange(tab: OrderTab) = _uiState.update { it.copy(activeTab = tab) }

    fun prepareOrder(symbol: String, ceiling: Double = Double.MAX_VALUE, floor: Double = 0.0) {
        _formState.update {
            it.copy(symbol = symbol, ceiling = ceiling, floor = floor)
        }
    }

    fun onSymbolChange(v: String) = _formState.update { it.copy(symbol = v.uppercase()) }
    fun onSideChange(v: OrderSide) = _formState.update { it.copy(side = v) }
    fun onTypeChange(v: OrderType) = _formState.update { it.copy(type = v) }
    fun onPriceChange(v: String) = _formState.update { it.copy(priceText = v) }
    fun onQuantityChange(v: String) = _formState.update { it.copy(quantityText = v) }

    fun clearMessages() = _uiState.update { it.copy(submitError = null, successMessage = null) }

    fun submitOrder() {
        val form = _formState.value
        _uiState.update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            val result = placeOrderUseCase(
                symbol = form.symbol,
                side = form.side,
                type = form.type,
                price = form.price,
                quantity = form.quantity,
                ceiling = form.ceiling,
                floor = form.floor,
            )
            when (result) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(isSubmitting = false, successMessage = "Đã gửi lệnh thành công")
                    }
                    _formState.update { it.copy(priceText = "", quantityText = "") }
                }
                is AppResult.Error -> _uiState.update {
                    it.copy(isSubmitting = false, submitError = result.message)
                }
            }
        }
    }

    fun cancelOrder(orderId: String) {
        viewModelScope.launch {
            val result = cancelOrderUseCase(orderId)
            if (result is AppResult.Error) {
                _uiState.update { it.copy(submitError = result.message) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.disconnect()
    }
}
