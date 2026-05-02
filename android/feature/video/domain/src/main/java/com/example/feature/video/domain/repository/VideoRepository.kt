package com.example.feature.video.domain.repository

import com.example.core.common.ConnectionState
import com.example.feature.video.domain.model.SignalMessage
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface VideoRepository {
    val connectionState: StateFlow<ConnectionState>
    val signals: SharedFlow<SignalMessage>

    fun connect(serverUrl: String, roomId: String)
    fun disconnect()
    suspend fun sendSignal(signal: SignalMessage): Boolean
}
