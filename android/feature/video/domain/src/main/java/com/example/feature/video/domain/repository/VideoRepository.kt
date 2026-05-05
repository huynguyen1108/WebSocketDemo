package com.example.feature.video.domain.repository

import com.example.core.common.ConnectionState
import com.example.feature.video.domain.model.SignalMessage
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class CallRole { CALLER, CALLEE }

interface VideoRepository {
    val connectionState: StateFlow<ConnectionState>
    val signals: SharedFlow<SignalMessage>

    fun connect(serverUrl: String, roomId: String, role: CallRole)
    fun disconnect()
    suspend fun sendSignal(signal: SignalMessage): Boolean
}
