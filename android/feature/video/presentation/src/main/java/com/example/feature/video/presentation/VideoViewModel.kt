package com.example.feature.video.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.common.ConnectionState
import com.example.core.security.TokenStore
import com.example.feature.video.domain.model.SignalMessage
import com.example.feature.video.domain.model.VideoCallState
import com.example.feature.video.domain.usecase.AcceptCallUseCase
import com.example.feature.video.domain.usecase.CancelCallUseCase
import com.example.feature.video.domain.usecase.LeaveRoomUseCase
import com.example.feature.video.domain.usecase.ObserveConnectionStateUseCase
import com.example.feature.video.domain.usecase.ObserveSignalsUseCase
import com.example.feature.video.domain.usecase.RejectCallUseCase
import com.example.feature.video.domain.usecase.SendSignalUseCase
import com.example.feature.video.domain.usecase.StartCallUseCase
import com.example.feature.video.domain.usecase.WaitForCallUseCase
import com.example.feature.video.presentation.system.CallAudioFocus
import com.example.feature.video.presentation.system.CallForegroundService
import com.example.feature.video.presentation.system.CallNotifications
import com.example.feature.video.presentation.system.ProximityWakeLock
import com.example.feature.video.presentation.system.RingtonePlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoTrack
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class VideoViewModel @Inject constructor(
    private val startCallUseCase: StartCallUseCase,
    private val waitForCallUseCase: WaitForCallUseCase,
    private val acceptCallUseCase: AcceptCallUseCase,
    private val rejectCallUseCase: RejectCallUseCase,
    private val cancelCallUseCase: CancelCallUseCase,
    private val leaveRoomUseCase: LeaveRoomUseCase,
    private val sendSignalUseCase: SendSignalUseCase,
    private val observeSignalsUseCase: ObserveSignalsUseCase,
    private val observeConnectionStateUseCase: ObserveConnectionStateUseCase,
    private val tokenStore: TokenStore,
    private val ringtonePlayer: RingtonePlayer,
    private val audioFocus: CallAudioFocus,
    private val proximityLock: ProximityWakeLock,
    private val callNotifications: CallNotifications,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val eglBase: EglBase = EglBase.create()

    private val _uiState = MutableStateFlow(
        VideoUiState(serverUrl = tokenStore.getServerUrl() ?: "ws://10.0.2.2:8080"),
    )
    val uiState: StateFlow<VideoUiState> = _uiState.asStateFlow()

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var localAudioTrack: AudioTrack? = null

    init {
        initPeerConnectionFactory()
        observeConnectionState()
        observeSignals()
        observeStateForSystemEffects()
    }

    /** Drive ringtone, foreground service, audio focus, proximity lock,
     *  and notification visibility from the call state — so they're all
     *  managed in one place rather than scattered across actions. */
    private fun observeStateForSystemEffects() {
        var prev: VideoCallState = VideoCallState.Idle
        uiState.onEach { state ->
            val cs = state.callState
            applyEffects(prev = prev, next = cs)
            prev = cs
        }.launchIn(viewModelScope)
    }

    private fun applyEffects(prev: VideoCallState, next: VideoCallState) {
        // ── Ringtone: only during Incoming ─────────────────────────────────
        val wasIncoming = prev is VideoCallState.Incoming
        val isIncoming  = next is VideoCallState.Incoming
        if (isIncoming && !wasIncoming) ringtonePlayer.start()
        if (wasIncoming && !isIncoming) ringtonePlayer.stop()

        // ── Incoming notification (full-screen intent) ─────────────────────
        if (isIncoming && !wasIncoming) {
            val name = (next as VideoCallState.Incoming).callerName
            callNotifications.showIncoming(CallNotifications.NOTIF_ID_INCOMING, name)
        }
        if (wasIncoming && !isIncoming) {
            callNotifications.cancel(CallNotifications.NOTIF_ID_INCOMING)
        }

        // ── Foreground service: keep process alive during the whole call ─
        val wasActiveCall = prev.isCallActive
        val isActiveCall  = next.isCallActive
        if (isActiveCall && !wasActiveCall) {
            CallForegroundService.start(context, foregroundLabel(next))
        } else if (!isActiveCall && wasActiveCall) {
            CallForegroundService.stop(context)
        }

        // ── Audio focus + proximity lock: only during InCall ──────────────
        val wasInCall = prev is VideoCallState.InCall
        val isInCall  = next is VideoCallState.InCall
        if (isInCall && !wasInCall) {
            audioFocus.acquire()
            proximityLock.acquire()
        }
        if (wasInCall && !isInCall) {
            audioFocus.release()
            proximityLock.release()
        }
    }

    /** The call is "active" (and the foreground service should run) for any
     *  non-Idle, non-terminal state. */
    private val VideoCallState.isCallActive: Boolean
        get() = when (this) {
            VideoCallState.Idle,
            is VideoCallState.Rejected,
            VideoCallState.Cancelled,
            VideoCallState.NoAnswer,
            is VideoCallState.Busy,
            is VideoCallState.Error -> false
            else -> true
        }

    private fun foregroundLabel(state: VideoCallState): String = when (state) {
        VideoCallState.Connecting -> "Đang kết nối"
        VideoCallState.WaitingForCallee -> "Đang chờ người nghe"
        is VideoCallState.Calling -> "Đang gọi ${state.calleeName}"
        is VideoCallState.Incoming -> "Cuộc gọi đến từ ${state.callerName}"
        is VideoCallState.PeerReady -> "Đang thiết lập"
        VideoCallState.InCall -> "Đang trong cuộc gọi"
        else -> "Cuộc gọi"
    }

    // ── Public actions ────────────────────────────────────────────────────────

    fun onServerUrlChange(url: String) = _uiState.update { it.copy(serverUrl = url) }
    fun onRoomIdChange(id: String) = _uiState.update { it.copy(roomId = id) }

    /** Caller — connect and wait for the callee to join. Camera starts immediately
     *  so the offer can include tracks once the callee accepts. */
    fun startCall() {
        val state = _uiState.value
        if (state.roomId.isBlank()) return
        _uiState.update { it.copy(signalLog = emptyList()) }
        viewModelScope.launch(Dispatchers.Main) {
            startCameraAndMic()
            startCallUseCase(state.serverUrl, state.roomId.trim())
        }
    }

    /** Callee — connect and wait for an "incoming" signal. Camera deferred until accept. */
    fun waitForCall() {
        val state = _uiState.value
        if (state.roomId.isBlank()) return
        _uiState.update { it.copy(signalLog = emptyList()) }
        waitForCallUseCase(state.serverUrl, state.roomId.trim())
    }

    /** Callee accepts — start camera, send Accept, wait for "join" from server. */
    fun acceptCall() {
        viewModelScope.launch(Dispatchers.Main) {
            startCameraAndMic()
            acceptCallUseCase()
            log("→ accept sent")
        }
    }

    /** Callee rejects — send Reject and disconnect. */
    fun rejectCall(reason: String? = null) {
        viewModelScope.launch {
            rejectCallUseCase(reason)
            log("→ reject sent")
            _uiState.update { it.copy(callState = VideoCallState.Idle) }
            leaveRoomUseCase()
        }
    }

    /** Caller cancels before callee answers. */
    fun cancelCall() {
        viewModelScope.launch {
            cancelCallUseCase()
            log("→ cancel sent")
            closePeerConnection()
            _uiState.update { it.copy(callState = VideoCallState.Idle) }
            leaveRoomUseCase()
        }
    }

    /** End an in-progress call. */
    fun leave() {
        viewModelScope.launch { sendSignalUseCase(SignalMessage.Leave) }
        closePeerConnection()
        leaveRoomUseCase()
        _uiState.update { it.copy(callState = VideoCallState.Idle) }
    }

    /** Reset terminal states (Rejected / Cancelled / NoAnswer / Busy / Error) back to Idle. */
    fun dismiss() {
        closePeerConnection()
        _uiState.update { it.copy(callState = VideoCallState.Idle) }
    }

    // ── State observation ─────────────────────────────────────────────────────

    private fun observeConnectionState() {
        observeConnectionStateUseCase()
            .onEach { connState ->
                _uiState.update { current ->
                    val next = mapConnection(connState, current.callState)
                    if (next == current.callState) current else current.copy(callState = next)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun mapConnection(connState: ConnectionState, current: VideoCallState): VideoCallState =
        when (connState) {
            is ConnectionState.Connecting -> VideoCallState.Connecting
            is ConnectionState.Connected -> {
                // Promote Connecting → role-specific waiting state. The first
                // server signal (incoming / ringing / rejected) will refine it.
                if (current is VideoCallState.Connecting) VideoCallState.WaitingForCallee
                else current
            }
            is ConnectionState.Error -> VideoCallState.Error(connState.message)
            else -> if (current.isTerminal) current else VideoCallState.Idle
        }

    private val VideoCallState.isTerminal: Boolean
        get() = this is VideoCallState.Rejected ||
            this is VideoCallState.Cancelled ||
            this is VideoCallState.NoAnswer ||
            this is VideoCallState.Busy ||
            this is VideoCallState.Error

    private fun observeSignals() {
        observeSignalsUseCase()
            .onEach { handleSignal(it) }
            .launchIn(viewModelScope)
    }

    // ── Signal handling ───────────────────────────────────────────────────────

    private fun handleSignal(signal: SignalMessage) {
        log(
            when (signal) {
                is SignalMessage.Incoming -> "← incoming from ${signal.callerName}"
                is SignalMessage.Ringing -> "← ringing ${signal.calleeName}"
                is SignalMessage.Join -> "← join peer=${signal.username} initiator=${signal.initiator}"
                is SignalMessage.Offer -> "← offer"
                is SignalMessage.Answer -> "← answer"
                is SignalMessage.IceCandidate -> "← ice"
                is SignalMessage.Rejected -> "← rejected (${signal.reason ?: "-"})"
                SignalMessage.Cancelled -> "← cancelled"
                SignalMessage.Timeout -> "← timeout"
                is SignalMessage.Busy -> "← busy (${signal.reason ?: "-"})"
                SignalMessage.Leave -> "← leave"
                SignalMessage.Accept, is SignalMessage.Reject, SignalMessage.Cancel -> ""
            },
        )

        when (signal) {
            is SignalMessage.Incoming ->
                _uiState.update { it.copy(callState = VideoCallState.Incoming(signal.callerName)) }

            is SignalMessage.Ringing ->
                _uiState.update { it.copy(callState = VideoCallState.Calling(signal.calleeName)) }

            is SignalMessage.Join -> {
                _uiState.update {
                    it.copy(callState = VideoCallState.PeerReady(signal.username, signal.initiator))
                }
                viewModelScope.launch(Dispatchers.Main) {
                    setupPeerConnection(initiator = signal.initiator)
                }
            }

            is SignalMessage.Offer -> viewModelScope.launch(Dispatchers.Main) {
                val pc = peerConnection ?: return@launch
                pc.setRemoteDescription(
                    SimpleSdpObserver(),
                    SessionDescription(SessionDescription.Type.OFFER, signal.sdp),
                )
                createAndSendAnswer(pc)
            }

            is SignalMessage.Answer -> viewModelScope.launch(Dispatchers.Main) {
                peerConnection?.setRemoteDescription(
                    SimpleSdpObserver(),
                    SessionDescription(SessionDescription.Type.ANSWER, signal.sdp),
                )
            }

            is SignalMessage.IceCandidate -> peerConnection?.addIceCandidate(
                IceCandidate(signal.sdpMid ?: "", signal.sdpMLineIndex ?: 0, signal.candidate),
            )

            is SignalMessage.Rejected -> {
                _uiState.update { it.copy(callState = VideoCallState.Rejected(signal.reason)) }
                closePeerConnection()
            }

            SignalMessage.Cancelled -> {
                _uiState.update { it.copy(callState = VideoCallState.Cancelled) }
                closePeerConnection()
            }

            SignalMessage.Timeout -> {
                _uiState.update { it.copy(callState = VideoCallState.NoAnswer) }
                closePeerConnection()
            }

            is SignalMessage.Busy ->
                _uiState.update { it.copy(callState = VideoCallState.Busy(signal.reason)) }

            SignalMessage.Leave -> {
                closePeerConnection()
                _uiState.update { it.copy(callState = VideoCallState.Idle) }
                leaveRoomUseCase()
            }

            // Outbound-only — never received from server.
            SignalMessage.Accept, is SignalMessage.Reject, SignalMessage.Cancel -> Unit
        }
    }

    // ── PeerConnectionFactory ─────────────────────────────────────────────────

    private fun initPeerConnectionFactory() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions(),
        )
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }

    // ── Camera / mic ──────────────────────────────────────────────────────────

    private fun startCameraAndMic() {
        if (_localVideoTrack.value != null) return // already running
        val factory = peerConnectionFactory ?: return
        val enumerator = Camera2Enumerator(context)
        val cameraName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?: enumerator.deviceNames.firstOrNull() ?: return

        val capturer = enumerator.createCapturer(cameraName, null) ?: return
        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        val videoSource = factory.createVideoSource(capturer.isScreencast)
        capturer.initialize(surfaceTextureHelper, context, videoSource.capturerObserver)
        capturer.startCapture(1280, 720, 30)
        _localVideoTrack.value = factory.createVideoTrack("local_video", videoSource).apply { setEnabled(true) }

        val audioSource = factory.createAudioSource(MediaConstraints())
        localAudioTrack = factory.createAudioTrack("local_audio", audioSource).apply { setEnabled(true) }
    }

    // ── PeerConnection ────────────────────────────────────────────────────────

    private fun buildIceServers(): List<PeerConnection.IceServer> {
        val servers = mutableListOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun.relay.metered.ca:80").createIceServer(),
        )
        if (TURN_USERNAME.isNotEmpty()) {
            servers += PeerConnection.IceServer.builder(
                listOf(
                    "turn:$TURN_HOST:80",
                    "turn:$TURN_HOST:80?transport=tcp",
                    "turn:$TURN_HOST:443",
                    "turns:$TURN_HOST:443?transport=tcp",
                ),
            ).setUsername(TURN_USERNAME).setPassword(TURN_PASSWORD).createIceServer()
        }
        return servers
    }

    companion object {
        private const val TURN_HOST     = "global.relay.metered.ca"
        private const val TURN_USERNAME = "a32e8382573e7aa0ed08d58f"
        private const val TURN_PASSWORD = "W6JJFlwSOopxUxwu"
    }

    private fun setupPeerConnection(initiator: Boolean) {
        val factory = peerConnectionFactory ?: return
        val config = PeerConnection.RTCConfiguration(buildIceServers()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        val pc = factory.createPeerConnection(config, pcObserver) ?: return
        peerConnection = pc

        _localVideoTrack.value?.let { pc.addTrack(it, listOf("local")) }
        localAudioTrack?.let { pc.addTrack(it, listOf("local")) }

        if (initiator) createAndSendOffer(pc)
    }

    private fun createAndSendOffer(pc: PeerConnection) {
        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        viewModelScope.launch {
                            sendSignalUseCase(SignalMessage.Offer(sdp.description))
                            log("→ offer sent")
                        }
                    }
                }, sdp)
            }
        }, MediaConstraints())
    }

    private fun createAndSendAnswer(pc: PeerConnection) {
        pc.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        viewModelScope.launch {
                            sendSignalUseCase(SignalMessage.Answer(sdp.description))
                            log("→ answer sent")
                        }
                    }
                }, sdp)
            }
        }, MediaConstraints())
    }

    private val pcObserver = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            viewModelScope.launch {
                sendSignalUseCase(
                    SignalMessage.IceCandidate(candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex),
                )
                val typ = when {
                    candidate.sdp.contains("typ relay") -> "relay"
                    candidate.sdp.contains("typ srflx") -> "srflx"
                    candidate.sdp.contains("typ prflx") -> "prflx"
                    else -> "host"
                }
                log("→ ice [$typ]")
            }
        }

        override fun onTrack(transceiver: RtpTransceiver) {
            val track = transceiver.receiver.track()
            if (track is VideoTrack) {
                _remoteVideoTrack.value = track
                _uiState.update { it.copy(callState = VideoCallState.InCall) }
                log("remote video track received")
            }
        }

        override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
            Timber.d("[VIDEO] PC state: %s", state)
            log("PC: $state")
            when (state) {
                PeerConnection.PeerConnectionState.FAILED ->
                    _uiState.update { it.copy(callState = VideoCallState.Error("WebRTC connection failed")) }
                PeerConnection.PeerConnectionState.DISCONNECTED ->
                    if (_uiState.value.callState is VideoCallState.InCall)
                        _uiState.update { it.copy(callState = VideoCallState.Error("Call disconnected")) }
                else -> Unit
            }
        }

        override fun onSignalingChange(p0: PeerConnection.SignalingState?) { log("SIG: $p0") }
        override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) { log("ICE conn: $p0") }
        override fun onIceConnectionReceivingChange(p0: Boolean) {}
        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) { log("ICE gather: $p0") }
        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
        override fun onAddStream(p0: MediaStream?) {}
        override fun onRemoveStream(p0: MediaStream?) {}
        override fun onDataChannel(p0: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    private fun closePeerConnection() {
        peerConnection?.close()
        peerConnection = null
        _remoteVideoTrack.value = null
        _localVideoTrack.value = null
        localAudioTrack = null
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
    }

    override fun onCleared() {
        super.onCleared()
        closePeerConnection()
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        eglBase.release()
        leaveRoomUseCase()
    }

    private fun log(msg: String) {
        if (msg.isEmpty()) return
        _uiState.update { it.copy(signalLog = it.signalLog + msg) }
    }
}

private open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) { Timber.e("[VIDEO] SDP create error: %s", error) }
    override fun onSetFailure(error: String?) { Timber.e("[VIDEO] SDP set error: %s", error) }
}
