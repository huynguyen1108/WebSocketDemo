package com.example.feature.order.data.datasource

import com.example.core.common.AppResult
import com.example.core.common.ApplicationScope
import com.example.core.common.IoDispatcher
import com.example.core.network.monitor.NetworkMonitor
import com.example.core.network.websocket.WebSocketClient
import com.example.core.network.websocket.WebSocketEvent
import com.example.core.security.TokenStore
import com.example.core.common.ConnectionState
import com.example.feature.order.data.dto.CancelOrderRequestDto
import com.example.feature.order.data.dto.OrderRequestDto
import com.example.feature.order.data.dto.OrderResponseDto
import com.example.feature.order.data.dto.TradingMessageDto
import com.example.feature.order.data.mapper.toDomain
import com.example.feature.order.domain.model.Order
import com.example.feature.order.domain.model.OrderSide
import com.example.feature.order.domain.model.OrderType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

private const val MAX_RETRY = 5
private const val BASE_DELAY_MS = 1_000L
private const val MAX_DELAY_MS = 32_000L

@Singleton
class OrderWebSocketDataSource @Inject constructor(
    @Named("trading") private val wsClient: WebSocketClient,
    private val json: Json,
    private val tokenStore: TokenStore,
    private val networkMonitor: NetworkMonitor,
    @ApplicationScope private val appScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _orders = MutableStateFlow<Map<String, Order>>(emptyMap())

    private val _ordersListFlow = MutableStateFlow<List<Order>>(emptyList())
    val ordersListFlow: StateFlow<List<Order>> = _ordersListFlow.asStateFlow()

    private var retryCount = 0
    private var retryJob: Job? = null
    private var collectJob: Job? = null

    init {
        appScope.launch {
            _orders.collect { map ->
                _ordersListFlow.value = map.values.sortedByDescending { it.createdAt }
            }
        }
        appScope.launch {
            combine(networkMonitor.networkAvailable, networkMonitor.isDozing) { online, dozing ->
                online to dozing
            }
                .distinctUntilChanged()
                .drop(1)
                .collect { (online, dozing) -> handleNetworkChange(online, dozing) }
        }
    }

    fun connect() {
        val token = tokenStore.getToken() ?: run {
            _connectionState.value = ConnectionState.Error("Chưa đăng nhập")
            return
        }
        val serverUrl = tokenStore.getServerUrl() ?: run {
            _connectionState.value = ConnectionState.Error("Không có server URL")
            return
        }
        retryCount = 0
        startCollecting()
        _connectionState.value = ConnectionState.Connecting
        wsClient.connect(
            url = buildTradingUrl(serverUrl),
            headers = mapOf("Authorization" to "Bearer $token"),
        )
    }

    fun disconnect() {
        retryJob?.cancel()
        collectJob?.cancel()
        wsClient.disconnect()
        _connectionState.value = ConnectionState.Disconnected
    }

    suspend fun placeOrder(
        symbol: String,
        side: OrderSide,
        type: OrderType,
        price: Double,
        quantity: Long,
    ): AppResult<String> = withContext(ioDispatcher) {
        if (_connectionState.value !is ConnectionState.Connected) {
            return@withContext AppResult.Error("Chưa kết nối tới server giao dịch")
        }
        val clientOrderId = UUID.randomUUID().toString()
        val request = OrderRequestDto(
            clientOrderId = clientOrderId,
            symbol = symbol,
            side = side.name,
            orderType = type.name,
            price = if (type == OrderType.ATO || type == OrderType.ATC) 0.0 else price,
            quantity = quantity,
        )
        val payload = buildJsonMessage("place_order", json.encodeToString(OrderRequestDto.serializer(), request))
        val sent = wsClient.send(payload)
        if (sent) AppResult.Success(clientOrderId)
        else AppResult.Error("Không thể gửi lệnh - mất kết nối")
    }

    suspend fun cancelOrder(orderId: String): AppResult<Unit> = withContext(ioDispatcher) {
        if (_connectionState.value !is ConnectionState.Connected) {
            return@withContext AppResult.Error("Chưa kết nối tới server giao dịch")
        }
        val request = CancelOrderRequestDto(orderId)
        val payload = buildJsonMessage("cancel_order", json.encodeToString(CancelOrderRequestDto.serializer(), request))
        val sent = wsClient.send(payload)
        if (sent) AppResult.Success(Unit)
        else AppResult.Error("Không thể huỷ lệnh - mất kết nối")
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
        Timber.d("Trading WebSocket connected")
    }

    private fun onMessage(raw: String) {
        try {
            val msg = json.decodeFromString<TradingMessageDto>(raw)
            when (msg.type) {
                "order_snapshot" -> {
                    val list = json.decodeFromJsonElement<List<OrderResponseDto>>(msg.payload)
                    _orders.value = list.associate { it.orderId to it.toDomain() }
                }
                "order_ack", "order_update" -> {
                    val dto = json.decodeFromJsonElement<OrderResponseDto>(msg.payload)
                    _orders.update { current ->
                        current.toMutableMap().also { it[dto.orderId] = dto.toDomain() }
                    }
                }
            }
        } catch (e: SerializationException) {
            Timber.w("Parse error: %s", raw)
        }
    }

    private fun onDisconnected(code: Int) {
        if (code == 1000) _connectionState.value = ConnectionState.Disconnected
        else scheduleRetry()
    }

    private fun onError(t: Throwable) {
        Timber.e(t, "Trading WebSocket error")
        _connectionState.value = ConnectionState.Error(t.message ?: "Connection failed")
        scheduleRetry()
    }

    private fun handleNetworkChange(online: Boolean, dozing: Boolean) {
        if (!online || dozing) {
            retryJob?.cancel()
            val state = _connectionState.value
            if (state !is ConnectionState.Disconnected && state !is ConnectionState.Idle) {
                wsClient.disconnect()
                _connectionState.value = ConnectionState.Disconnected
            }
        } else {
            val state = _connectionState.value
            val hasSession = tokenStore.hasSession()
            if (hasSession && state !is ConnectionState.Connected && state !is ConnectionState.Connecting) {
                retryCount = 0
                connect()
            }
        }
    }

    private fun scheduleRetry() {
        if (!networkMonitor.canReconnect || !tokenStore.hasSession()) {
            _connectionState.value = ConnectionState.Disconnected
            return
        }
        if (retryCount >= MAX_RETRY) {
            _connectionState.value = ConnectionState.Error("Không thể kết nối sau $MAX_RETRY lần thử")
            return
        }
        val delayMs = min(BASE_DELAY_MS * 2.0.pow(retryCount).toLong(), MAX_DELAY_MS)
        retryCount++
        retryJob?.cancel()
        retryJob = appScope.launch {
            _connectionState.value = ConnectionState.Connecting
            delay(delayMs)
            connect()
        }
    }

    private fun buildTradingUrl(baseUrl: String): String {
        val wsBase = baseUrl.trimEnd('/')
            .replace("http://", "ws://")
            .replace("https://", "wss://")
        return "$wsBase/trading"
    }

    private fun buildJsonMessage(type: String, payloadJson: String): String =
        """{"type":"$type","payload":$payloadJson}"""
}
