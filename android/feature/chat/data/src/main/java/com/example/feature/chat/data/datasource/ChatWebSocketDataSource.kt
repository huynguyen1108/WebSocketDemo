package com.example.feature.chat.data.datasource

import com.example.core.common.ApplicationScope
import com.example.core.common.IoDispatcher
import com.example.core.network.monitor.NetworkMonitor
import com.example.core.network.websocket.WebSocketClient
import com.example.core.network.websocket.WebSocketEvent
import com.example.core.security.TokenStore
import com.example.core.security.authHeaders
import com.example.feature.chat.data.dto.MessageDto
import com.example.feature.chat.data.mapper.toDomain
import com.example.feature.chat.domain.model.ChatMessage
import com.example.core.common.ConnectionState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

private const val MAX_RECONNECT_ATTEMPTS = 5
private const val INITIAL_DELAY_MS = 1_000L
private const val MAX_DELAY_MS = 30_000L

@Singleton
class ChatWebSocketDataSource @Inject constructor(
    @Named("chat") private val wsClient: WebSocketClient,
    private val json: Json,
    private val tokenStore: TokenStore,
    private val networkMonitor: NetworkMonitor,
    @ApplicationScope private val appScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _messages = MutableSharedFlow<ChatMessage>(
        replay = 0,
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<ChatMessage> = _messages.asSharedFlow()

    private var serverUrl: String = ""
    private var username: String = ""
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null
    private var eventCollectorJob: Job? = null

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

    fun connect(serverUrl: String, username: String) {
        _connectionState.value = ConnectionState.Connecting
        this.serverUrl = serverUrl
        this.username = username
        reconnectAttempts = 0
        startEventCollection()
        wsClient.connect(buildWsUrl(serverUrl, username), tokenStore.authHeaders())
    }

    suspend fun sendMessage(content: String): Boolean = withContext(ioDispatcher) {
        val payload = buildJsonObject {
            put("type", "message")
            put("content", content)
        }.toString()
        wsClient.send(payload)
    }

    fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempts = 0
        wsClient.disconnect()
        _connectionState.value = ConnectionState.Disconnected
    }

    private fun startEventCollection() {
        eventCollectorJob?.cancel()
        eventCollectorJob = appScope.launch {
            wsClient.events.collect { event ->
                handleEvent(event)
            }
        }
    }

    private fun handleEvent(event: WebSocketEvent) {
        when (event) {
            is WebSocketEvent.Connected -> {
                reconnectAttempts = 0
                reconnectJob?.cancel()
                _connectionState.value = ConnectionState.Connected
                Timber.d("Connected to $serverUrl")
            }

            is WebSocketEvent.MessageReceived -> {
                parseMessage(event.raw)?.let { msg ->
                    appScope.launch { _messages.emit(msg) }
                }
            }

            is WebSocketEvent.Disconnected -> {
                Timber.d("Disconnected: ${event.code} ${event.reason}")
                if (event.code != 1000) {
                    scheduleReconnect()
                } else {
                    _connectionState.value = ConnectionState.Disconnected
                }
            }

            is WebSocketEvent.Error -> {
                Timber.e(event.throwable, "WebSocket error")
                _connectionState.value = ConnectionState.Error(event.throwable.message ?: "Connection failed")
                scheduleReconnect()
            }
        }
    }

    private fun handleNetworkChange(online: Boolean, dozing: Boolean) {
        if (!online || dozing) {
            reconnectJob?.cancel()
            reconnectJob = null
            val state = _connectionState.value
            if (state !is ConnectionState.Disconnected && state !is ConnectionState.Idle) {
                wsClient.disconnect()
                _connectionState.value = ConnectionState.Disconnected
            }
        } else if (serverUrl.isNotEmpty()) {
            val state = _connectionState.value
            if (state !is ConnectionState.Connected && state !is ConnectionState.Connecting) {
                reconnectAttempts = 0
                startEventCollection()
                _connectionState.value = ConnectionState.Connecting
                wsClient.connect(buildWsUrl(serverUrl, username), tokenStore.authHeaders())
            }
        }
    }

    private fun scheduleReconnect() {
        if (!networkMonitor.canReconnect) {
            _connectionState.value = ConnectionState.Disconnected
            return
        }
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            _connectionState.value = ConnectionState.Error("Max reconnect attempts reached")
            return
        }

        val delayMs = min(
            (INITIAL_DELAY_MS * 2.0.pow(reconnectAttempts)).toLong(),
            MAX_DELAY_MS,
        )
        reconnectAttempts++

        Timber.d("Reconnecting in ${delayMs}ms (attempt $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS)")

        reconnectJob?.cancel()
        reconnectJob = appScope.launch {
            _connectionState.value = ConnectionState.Connecting
            delay(delayMs)
            wsClient.connect(buildWsUrl(serverUrl, username), tokenStore.authHeaders())
        }
    }

    private fun parseMessage(raw: String): ChatMessage? = try {
        json.decodeFromString<MessageDto>(raw).toDomain()
    } catch (e: SerializationException) {
        Timber.w("Failed to parse message: %s", raw)
        null
    }

    private fun buildWsUrl(serverUrl: String, username: String): String {
        val base = serverUrl.trimEnd('/')
        return "$base/ws?username=${username.trim()}"
    }
}
