package com.example.feature.video.data.repository

import com.example.core.common.ConnectionState
import com.example.feature.video.data.datasource.VideoWebSocketDataSource
import com.example.feature.video.domain.model.SignalMessage
import com.example.feature.video.domain.repository.CallRole
import com.example.feature.video.domain.repository.VideoRepository
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepositoryImpl @Inject constructor(
    private val dataSource: VideoWebSocketDataSource,
) : VideoRepository {

    override val connectionState: StateFlow<ConnectionState> = dataSource.connectionState
    override val signals: SharedFlow<SignalMessage> = dataSource.signals

    override fun connect(serverUrl: String, roomId: String, role: CallRole) =
        dataSource.connect(serverUrl, roomId, role)

    override fun disconnect() = dataSource.disconnect()
    override suspend fun sendSignal(signal: SignalMessage): Boolean = dataSource.sendSignal(signal)
}
