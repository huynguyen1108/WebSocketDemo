package com.example.feature.stock.data.datasource

import com.example.core.common.ApplicationScope
import com.example.core.network.monitor.NetworkMonitor
import com.example.core.network.websocket.WebSocketClient
import com.example.core.network.websocket.WebSocketEvent
import com.example.core.security.TokenStore
import com.example.core.security.authHeaders
import com.example.core.common.ConnectionState
import com.example.feature.stock.data.dto.MarketEvent
import com.example.feature.stock.data.mapper.toDomain
import com.example.feature.stock.domain.model.MarketIndex
import com.example.feature.stock.domain.model.StockItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

private const val MAX_RETRY = 5
private const val BASE_DELAY_MS = 1_000L
private const val MAX_DELAY_MS = 32_000L

@Singleton
class StockWebSocketDataSource @Inject constructor(
    @Named("market") private val wsClient: WebSocketClient,
    private val json: Json,
    private val tokenStore: TokenStore,
    private val networkMonitor: NetworkMonitor,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _stocksMap = MutableStateFlow<LinkedHashMap<String, StockItem>>(LinkedHashMap())

    private val _stocks = MutableStateFlow<List<StockItem>>(emptyList())
    val stocks: StateFlow<List<StockItem>> = _stocks.asStateFlow()

    private val _indices = MutableStateFlow<List<MarketIndex>>(emptyList())
    val indices: StateFlow<List<MarketIndex>> = _indices.asStateFlow()

    private var serverUrl = ""
    private var retryCount = 0
    private var retryJob: Job? = null
    private var collectJob: Job? = null

    init {
        appScope.launch {
            combine(networkMonitor.networkAvailable, networkMonitor.isDozing) { online, dozing ->
                online to dozing
            }
                .distinctUntilChanged()
                .drop(1)
                .debounce(500)
                .collect { (online, dozing) -> handleNetworkChange(online, dozing) }
        }
    }

    fun connect(serverUrl: String) {
        _connectionState.value = ConnectionState.Connecting
        this.serverUrl = serverUrl
        retryCount = 0
        startCollecting()
        wsClient.connect(buildUrl(serverUrl), tokenStore.authHeaders())
    }

    fun disconnect() {
        retryJob?.cancel()
        collectJob?.cancel()
        wsClient.disconnect()
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun startCollecting() {
        collectJob?.cancel()
        collectJob = appScope.launch {
            wsClient.events.collect { event ->
                when (event) {
                    is WebSocketEvent.Connected -> onConnected()
                    is WebSocketEvent.MessageReceived -> onMessage(event.raw)
                    is WebSocketEvent.Disconnected -> onDisconnected(event.code)
                    is WebSocketEvent.Error -> onError(event.throwable)
                }
            }
        }
    }

    private fun onConnected() {
        retryCount = 0
        retryJob?.cancel()
        _connectionState.value = ConnectionState.Connected
        Timber.d("Connected to %s", serverUrl)
    }

    private fun onMessage(raw: String) {
        try {
            when (val event = json.decodeFromString<MarketEvent>(raw)) {
                is MarketEvent.Snapshot -> {
                    val map = LinkedHashMap<String, StockItem>()
                    event.payload.stocks.forEach { dto -> map[dto.symbol] = dto.toDomain() }
                    _stocksMap.value = map
                    _stocks.value = map.values.toList()
                    _indices.value = event.payload.indices.map { it.toDomain() }
                    Timber.d("Snapshot: %d stocks, %d indices", event.payload.stocks.size, event.payload.indices.size)
                }
                is MarketEvent.StockTick -> applyStockTick(event.payload)
                is MarketEvent.IndexTick -> applyIndexTick(event.payload)
            }
        } catch (e: SerializationException) {
            Timber.w("Parse error: %s", raw)
        }
    }

    private fun applyStockTick(dto: com.example.feature.stock.data.dto.StockDto) {
        _stocksMap.update { current ->
            val updated = LinkedHashMap(current)
            updated[dto.symbol] = dto.toDomain(updated[dto.symbol])
            updated
        }
        _stocks.value = _stocksMap.value.values.toList()
    }

    private fun applyIndexTick(dto: com.example.feature.stock.data.dto.MarketIndexDto) {
        _indices.update { current ->
            val idx = current.indexOfFirst { it.name == dto.name }
            if (idx >= 0) current.toMutableList().also { it[idx] = dto.toDomain() }
            else current + dto.toDomain()
        }
    }

    private fun onDisconnected(code: Int) {
        if (code == 1000) _connectionState.value = ConnectionState.Disconnected
        else scheduleRetry()
    }

    private fun onError(t: Throwable) {
        Timber.e(t, "WebSocket error")
        _connectionState.value = ConnectionState.Error(t.message ?: "Connection failed")
        scheduleRetry()
    }

    private fun handleNetworkChange(online: Boolean, dozing: Boolean) {
        if (!online || dozing) {
            retryJob?.cancel()
            retryJob = null
            val state = _connectionState.value
            if (state !is ConnectionState.Disconnected && state !is ConnectionState.Idle) {
                wsClient.disconnect()
                _connectionState.value = ConnectionState.Disconnected
            }
        } else if (serverUrl.isNotEmpty()) {
            val state = _connectionState.value
            if (state !is ConnectionState.Connected && state !is ConnectionState.Connecting) {
                retryCount = 0
                startCollecting()
                _connectionState.value = ConnectionState.Connecting
                wsClient.connect(buildUrl(serverUrl), tokenStore.authHeaders())
            }
        }
    }

    private fun scheduleRetry() {
        if (!networkMonitor.canReconnect) {
            _connectionState.value = ConnectionState.Disconnected
            return
        }
        if (retryCount >= MAX_RETRY) {
            _connectionState.value = ConnectionState.Error("Cannot connect after $MAX_RETRY attempts")
            return
        }
        val delayMs = min(BASE_DELAY_MS * 2.0.pow(retryCount).toLong(), MAX_DELAY_MS)
        retryCount++
        Timber.d("Retry #%d in %dms", retryCount, delayMs)

        retryJob?.cancel()
        retryJob = appScope.launch {
            _connectionState.value = ConnectionState.Connecting
            delay(delayMs)
            wsClient.connect(buildUrl(serverUrl), tokenStore.authHeaders())
        }
    }

    private fun buildUrl(base: String): String = base.trimEnd('/') + "/market"
}
