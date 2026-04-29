package com.example.core.common

sealed class ConnectionState {
    data object Idle : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data object Disconnected : ConnectionState()
    data class Error(val message: String) : ConnectionState()

    val isConnected get() = this is Connected
    val isConnecting get() = this is Connecting
}
