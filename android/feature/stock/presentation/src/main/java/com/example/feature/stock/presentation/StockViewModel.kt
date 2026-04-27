package com.example.feature.stock.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.security.TokenStore
import com.example.feature.auth.domain.usecase.LogoutUseCase
import com.example.feature.stock.domain.repository.StockRepository
import com.example.feature.stock.domain.usecase.ConnectToMarketUseCase
import com.example.feature.stock.domain.usecase.ObserveIndicesUseCase
import com.example.feature.stock.domain.usecase.ObserveMarketConnectionUseCase
import com.example.feature.stock.domain.usecase.ObserveStocksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StockViewModel @Inject constructor(
    private val connectUseCase: ConnectToMarketUseCase,
    private val observeStocksUseCase: ObserveStocksUseCase,
    private val observeIndicesUseCase: ObserveIndicesUseCase,
    private val observeConnectionUseCase: ObserveMarketConnectionUseCase,
    private val repository: StockRepository,
    private val logoutUseCase: LogoutUseCase,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        StockUiState(serverUrl = tokenStore.getServerUrl()?.toWsBaseUrl() ?: "ws://10.0.2.2:8080"),
    )
    val uiState: StateFlow<StockUiState> = _uiState.asStateFlow()

    private val _logoutEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val logoutEvent: SharedFlow<Unit> = _logoutEvent.asSharedFlow()

    init {
        observeStocksUseCase().onEach { stocks ->
            _uiState.update { it.copy(stocks = stocks) }
        }.launchIn(viewModelScope)

        observeIndicesUseCase().onEach { indices ->
            _uiState.update { it.copy(indices = indices) }
        }.launchIn(viewModelScope)

        observeConnectionUseCase().onEach { state ->
            _uiState.update { it.copy(connectionState = state) }
        }.launchIn(viewModelScope)

        // Auto-connect nếu đã đăng nhập và có URL lưu sẵn
        if (tokenStore.hasSession()) connect()
    }

    fun connect() = connectUseCase(_uiState.value.serverUrl)

    fun disconnect() = repository.disconnect()

    fun logout() {
        viewModelScope.launch {
            repository.disconnect()
            logoutUseCase()
            _logoutEvent.emit(Unit)
        }
    }

    fun onServerUrlChange(url: String) = _uiState.update { it.copy(serverUrl = url) }

    fun onSearchQueryChange(query: String) = _uiState.update { it.copy(searchQuery = query) }

    fun onSortModeChange(mode: SortMode) = _uiState.update { it.copy(sortMode = mode) }

    override fun onCleared() {
        super.onCleared()
        repository.disconnect()
    }

    private fun String.toWsBaseUrl(): String =
        replace("http://", "ws://").replace("https://", "wss://")
}
