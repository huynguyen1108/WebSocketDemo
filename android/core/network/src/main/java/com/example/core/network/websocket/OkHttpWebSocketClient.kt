package com.example.core.network.websocket

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class OkHttpWebSocketClient(
    private val okHttpClient: OkHttpClient,
) : WebSocketClient {

    private val _events = MutableSharedFlow<WebSocketEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: SharedFlow<WebSocketEvent> = _events.asSharedFlow()

    private val webSocketRef = AtomicReference<WebSocket?>(null)
    private val generation = AtomicInteger(0)

    override fun connect(url: String, headers: Map<String, String>) {
        val gen = generation.incrementAndGet()
        webSocketRef.getAndSet(null)?.cancel()
        val request = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> addHeader(k, v) }
        }.build()
        val ws = okHttpClient.newWebSocket(request, WebSocketCallback(gen))
        webSocketRef.set(ws)
    }

    override fun send(message: String): Boolean = webSocketRef.get()?.send(message) ?: false

    override fun disconnect(code: Int, reason: String) {
        generation.incrementAndGet()
        webSocketRef.getAndSet(null)?.close(code, reason)
    }

    private inner class WebSocketCallback(private val gen: Int) : WebSocketListener() {

        private fun isCurrent() = gen == generation.get()

        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (isCurrent()) _events.tryEmit(WebSocketEvent.Connected)
            else webSocket.cancel()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (isCurrent()) _events.tryEmit(WebSocketEvent.MessageReceived(text))
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {}

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (isCurrent()) _events.tryEmit(WebSocketEvent.Disconnected(code, reason))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (isCurrent()) _events.tryEmit(WebSocketEvent.Error(t))
        }
    }
}
