package com.example.core.network.websocket

sealed class WebSocketEvent {
    data object Connected : WebSocketEvent()
    data class MessageReceived(val raw: String) : WebSocketEvent()
    data class Disconnected(val code: Int, val reason: String) : WebSocketEvent()
    data class Error(val throwable: Throwable) : WebSocketEvent()
}
