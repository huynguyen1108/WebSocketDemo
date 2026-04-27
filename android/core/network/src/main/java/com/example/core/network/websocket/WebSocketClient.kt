package com.example.core.network.websocket

import kotlinx.coroutines.flow.SharedFlow

interface WebSocketClient {
    val events: SharedFlow<WebSocketEvent>
    fun connect(url: String, headers: Map<String, String> = emptyMap())
    fun send(message: String): Boolean
    fun disconnect(code: Int = 1000, reason: String = "Normal closure")
}
