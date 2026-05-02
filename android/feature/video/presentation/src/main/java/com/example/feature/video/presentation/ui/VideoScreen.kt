package com.example.feature.video.presentation.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.feature.video.domain.model.VideoCallState
import com.example.feature.video.presentation.VideoViewModel
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

private val ColorGreen = Color(0xFF3FB950)
private val ColorRed = Color(0xFFF85149)
private val ColorYellow = Color(0xFFE3B341)
private val ColorSurface = Color(0xFF161B22)

private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoScreen(
    modifier: Modifier = Modifier,
    viewModel: VideoViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val localTrack by viewModel.localVideoTrack.collectAsStateWithLifecycle()
    val remoteTrack by viewModel.remoteVideoTrack.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val logListState = rememberLazyListState()

    // Create renderers once; init EGL context inside factory so it's ready immediately.
    val localRenderer = remember {
        SurfaceViewRenderer(context).apply {
            init(viewModel.eglBase.eglBaseContext, null)
            setMirror(true)
        }
    }
    val remoteRenderer = remember {
        SurfaceViewRenderer(context).apply {
            init(viewModel.eglBase.eglBaseContext, null)
        }
    }

    // Release renderers when screen is disposed.
    DisposableEffect(Unit) {
        onDispose {
            localRenderer.release()
            remoteRenderer.release()
        }
    }

    // Attach / detach local video sink whenever the track changes.
    DisposableEffect(localTrack) {
        localTrack?.addSink(localRenderer)
        onDispose { localTrack?.removeSink(localRenderer) }
    }

    // Attach / detach remote video sink whenever the track changes.
    DisposableEffect(remoteTrack) {
        remoteTrack?.addSink(remoteRenderer)
        onDispose { remoteTrack?.removeSink(remoteRenderer) }
    }

    // Auto-scroll signal log to bottom.
    LaunchedEffect(state.signalLog.size) {
        if (state.signalLog.isNotEmpty()) logListState.animateScrollToItem(state.signalLog.lastIndex)
    }

    // Permission launcher — calls viewModel.join() after permissions are granted.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.all { it }) viewModel.join()
    }

    fun requestJoin() {
        val allGranted = REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) viewModel.join() else permissionLauncher.launch(REQUIRED_PERMISSIONS)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Video Call", fontWeight = FontWeight.Bold)
                        CallStateLabel(state.callState)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {

            // ── Video panes ──────────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VideoPane(
                    label = "Local",
                    track = localTrack,
                    renderer = localRenderer,
                    modifier = Modifier.weight(1f),
                )
                VideoPane(
                    label = "Remote",
                    track = remoteTrack,
                    renderer = remoteRenderer,
                    modifier = Modifier.weight(1f),
                )
            }

            // ── Connect panel (Idle / Error / Busy) ──────────────────────────
            if (state.callState is VideoCallState.Idle ||
                state.callState is VideoCallState.Error ||
                state.callState is VideoCallState.Busy
            ) {
                Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (state.callState is VideoCallState.Error) {
                            Text(
                                "⚠ ${(state.callState as VideoCallState.Error).message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ColorRed,
                            )
                        }
                        if (state.callState is VideoCallState.Busy) {
                            Text(
                                "Room is full (max 2 peers)",
                                style = MaterialTheme.typography.bodySmall,
                                color = ColorRed,
                            )
                        }
                        OutlinedTextField(
                            value = state.serverUrl,
                            onValueChange = viewModel::onServerUrlChange,
                            label = { Text("Server URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = state.roomId,
                            onValueChange = viewModel::onRoomIdChange,
                            label = { Text("Room ID") },
                            placeholder = { Text("e.g. room1") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = { requestJoin() },
                            enabled = state.roomId.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Videocam, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Join Room")
                        }
                    }
                }
            }

            // ── In-call controls (Connecting / Waiting / PeerReady / InCall) ─
            if (state.callState !is VideoCallState.Idle &&
                state.callState !is VideoCallState.Error &&
                state.callState !is VideoCallState.Busy
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (state.callState) {
                        is VideoCallState.Connecting,
                        is VideoCallState.WaitingForPeer ->
                            CircularProgressIndicator(strokeWidth = 2.dp)
                        else -> {}
                    }
                    Text(
                        text = when (val cs = state.callState) {
                            is VideoCallState.Connecting -> "Connecting…"
                            is VideoCallState.WaitingForPeer -> "Waiting for peer in \"${state.roomId}\"…"
                            is VideoCallState.PeerReady ->
                                "${cs.peerName} • ${if (cs.initiator) "Sending offer…" else "Waiting for offer…"}"
                            is VideoCallState.InCall -> "In call"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.callState is VideoCallState.InCall) ColorGreen else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = viewModel::leave,
                        colors = ButtonDefaults.buttonColors(containerColor = ColorRed),
                    ) {
                        Icon(Icons.Default.CallEnd, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Leave")
                    }
                }
            }

            // ── Signal log ───────────────────────────────────────────────────
            Text(
                "Signal log",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(
                state = logListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(ColorSurface, MaterialTheme.shapes.small)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (state.signalLog.isEmpty()) {
                    item {
                        Text(
                            "No signals yet",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                items(state.signalLog) { entry ->
                    Text(entry, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = ColorGreen)
                }
            }
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun VideoPane(
    label: String,
    track: VideoTrack?,
    renderer: SurfaceViewRenderer,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(4f / 3f)
            .background(ColorSurface, MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center,
    ) {
        if (track != null) {
            AndroidView(
                factory = { renderer },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Videocam, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CallStateLabel(state: VideoCallState) {
    val (text, color) = when (state) {
        VideoCallState.Idle -> "● Not in a room" to Color.Gray
        VideoCallState.Connecting -> "● Connecting…" to ColorYellow
        VideoCallState.WaitingForPeer -> "● Waiting for peer" to ColorYellow
        is VideoCallState.PeerReady -> "● Peer connected" to ColorGreen
        VideoCallState.InCall -> "● In call" to ColorGreen
        VideoCallState.Busy -> "● Room full" to ColorRed
        is VideoCallState.Error -> "● Error: ${state.message}" to ColorRed
    }
    Text(text, style = MaterialTheme.typography.labelSmall, color = color)
}
