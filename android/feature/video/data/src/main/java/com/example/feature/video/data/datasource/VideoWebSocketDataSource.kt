package com.example.feature.video.data.datasource

import com.example.core.common.ApplicationScope
import com.example.core.common.ConnectionState
import com.example.core.network.websocket.WebSocketClient
import com.example.core.network.websocket.WebSocketEvent
import com.example.core.security.TokenStore
import com.example.core.security.authHeaders
import com.example.feature.video.data.dto.SignalDto
import com.example.feature.video.data.mapper.toDomain
import com.example.feature.video.data.mapper.toDto
import com.example.feature.video.domain.model.SignalMessage
import com.example.feature.video.domain.repository.CallRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class VideoWebSocketDataSource @Inject constructor(
    @Named("video") private val wsClient: WebSocketClient,
    private val json: Json,
    private val tokenStore: TokenStore,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _signals = MutableSharedFlow<SignalMessage>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val signals: SharedFlow<SignalMessage> = _signals.asSharedFlow()

    private var serverUrl = ""
    private var roomId = ""
    private var role: CallRole = CallRole.CALLER
    private var collectJob: Job? = null

    fun connect(serverUrl: String, roomId: String, role: CallRole) {
        _connectionState.value = ConnectionState.Connecting
        this.serverUrl = serverUrl
        this.roomId = roomId
        this.role = role
        startCollecting()
        wsClient.connect(buildUrl(serverUrl, roomId, role), tokenStore.authHeaders())
    }

    fun disconnect() {
        collectJob?.cancel()
        wsClient.disconnect()
        _connectionState.value = ConnectionState.Disconnected
    }

    suspend fun sendSignal(signal: SignalMessage): Boolean {
        val dto = signal.toDto() ?: return false
        return wsClient.send(json.encodeToString(SignalDto.serializer(), dto))
    }

    private fun startCollecting() {
        collectJob?.cancel()
        collectJob = appScope.launch {
            wsClient.events.collect { event ->
                when (event) {
                    is WebSocketEvent.Connected -> {
                        _connectionState.value = ConnectionState.Connected
                        Timber.d("[VIDEO] Connected room=%s", roomId)
                    }
                    is WebSocketEvent.MessageReceived -> parseAndEmit(event.raw)
                    is WebSocketEvent.Disconnected -> {
                        val state = if (event.code == 1000) ConnectionState.Disconnected
                        else ConnectionState.Error("Connection lost (${event.code})")
                        _connectionState.value = state
                        Timber.d("[VIDEO] Disconnected code=%d", event.code)
                    }
                    is WebSocketEvent.Error -> {
                        _connectionState.value = ConnectionState.Error(event.throwable.message ?: "Connection failed")
                        Timber.e(event.throwable, "[VIDEO] Error")
                    }
                }
            }
        }
    }

    private fun parseAndEmit(raw: String) {
        try {
            val dto = json.decodeFromString<SignalDto>(raw)
            val signal = dto.toDomain() ?: return
            Timber.d("[VIDEO] Signal: type=%s", dto.type)
            appScope.launch { _signals.emit(signal) }
        } catch (e: SerializationException) {
            Timber.w("[VIDEO] Parse error: %s", raw)
        }
    }

    private fun buildUrl(base: String, roomId: String, role: CallRole): String {
        val roleName = role.name.lowercase()
        return base.trimEnd('/') + "/video?room=" + roomId + "&role=" + roleName
    }
}
