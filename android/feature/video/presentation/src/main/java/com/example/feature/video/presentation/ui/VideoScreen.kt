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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

    val localRenderer = remember {
        SurfaceViewRenderer(context).apply {
            init(viewModel.eglBase.eglBaseContext, null)
            setMirror(true)
        }
    }
    val remoteRenderer = remember {
        SurfaceViewRenderer(context).apply { init(viewModel.eglBase.eglBaseContext, null) }
    }

    DisposableEffect(Unit) {
        onDispose {
            localRenderer.release()
            remoteRenderer.release()
        }
    }
    DisposableEffect(localTrack) {
        localTrack?.addSink(localRenderer)
        onDispose { localTrack?.removeSink(localRenderer) }
    }
    DisposableEffect(remoteTrack) {
        remoteTrack?.addSink(remoteRenderer)
        onDispose { remoteTrack?.removeSink(remoteRenderer) }
    }

    LaunchedEffect(state.signalLog.size) {
        if (state.signalLog.isNotEmpty()) logListState.animateScrollToItem(state.signalLog.lastIndex)
    }

    // Permission launchers — caller and callee both need camera+mic, but at
    // different moments (caller: at "Start Call", callee: at "Accept").
    val callerPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted -> if (granted.values.all { it }) viewModel.startCall() }

    val acceptPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted -> if (granted.values.all { it }) viewModel.acceptCall() }

    fun hasPerms(): Boolean = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun requestStartCall() {
        if (hasPerms()) viewModel.startCall() else callerPermissionLauncher.launch(REQUIRED_PERMISSIONS)
    }

    fun requestAccept() {
        if (hasPerms()) viewModel.acceptCall() else acceptPermissionLauncher.launch(REQUIRED_PERMISSIONS)
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
                VideoPane("Local", localTrack, localRenderer, modifier = Modifier.weight(1f))
                VideoPane("Remote", remoteTrack, remoteRenderer, modifier = Modifier.weight(1f))
            }

            // ── State-driven controls ────────────────────────────────────────
            when (val cs = state.callState) {
                VideoCallState.Idle ->
                    IdlePanel(
                        serverUrl = state.serverUrl,
                        roomId = state.roomId,
                        onUrlChange = viewModel::onServerUrlChange,
                        onRoomChange = viewModel::onRoomIdChange,
                        onStartCall = ::requestStartCall,
                        onWaitForCall = viewModel::waitForCall,
                    )

                VideoCallState.Connecting ->
                    StatusRow("Đang kết nối server…", spinning = true)

                VideoCallState.WaitingForCallee ->
                    WaitingForCalleePanel(roomId = state.roomId, onCancel = viewModel::cancelCall)

                is VideoCallState.Calling ->
                    CallingPanel(calleeName = cs.calleeName, onCancel = viewModel::cancelCall)

                is VideoCallState.Incoming ->
                    IncomingPanel(
                        callerName = cs.callerName,
                        onAccept = ::requestAccept,
                        onReject = { viewModel.rejectCall("declined") },
                    )

                is VideoCallState.PeerReady ->
                    StatusRow(
                        "${cs.peerName} • ${if (cs.initiator) "Đang gửi offer…" else "Đang chờ offer…"}",
                        spinning = true,
                    )

                VideoCallState.InCall ->
                    InCallPanel(onLeave = viewModel::leave)

                is VideoCallState.Rejected ->
                    OutcomePanel(
                        title = "Cuộc gọi bị từ chối",
                        subtitle = cs.reason,
                        color = ColorRed,
                        onDismiss = viewModel::dismiss,
                    )

                VideoCallState.Cancelled ->
                    OutcomePanel(
                        title = "Cuộc gọi đã bị huỷ",
                        subtitle = null,
                        color = ColorYellow,
                        onDismiss = viewModel::dismiss,
                    )

                VideoCallState.NoAnswer ->
                    OutcomePanel(
                        title = "Không có người trả lời",
                        subtitle = "Hết thời gian chờ",
                        color = ColorYellow,
                        onDismiss = viewModel::dismiss,
                    )

                is VideoCallState.Busy ->
                    OutcomePanel(
                        title = "Phòng đang bận",
                        subtitle = cs.reason,
                        color = ColorRed,
                        onDismiss = viewModel::dismiss,
                    )

                is VideoCallState.Error ->
                    OutcomePanel(
                        title = "Lỗi",
                        subtitle = cs.message,
                        color = ColorRed,
                        onDismiss = viewModel::dismiss,
                    )
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

// ── Idle (role choice) ────────────────────────────────────────────────────────

@Composable
private fun IdlePanel(
    serverUrl: String,
    roomId: String,
    onUrlChange: (String) -> Unit,
    onRoomChange: (String) -> Unit,
    onStartCall: () -> Unit,
    onWaitForCall: () -> Unit,
) {
    Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = serverUrl,
                onValueChange = onUrlChange,
                label = { Text("Server URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = roomId,
                onValueChange = onRoomChange,
                label = { Text("Room ID") },
                placeholder = { Text("vd: room1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onStartCall,
                    enabled = roomId.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Call, null); Spacer(Modifier.width(8.dp)); Text("Gọi")
                }
                OutlinedButton(
                    onClick = onWaitForCall,
                    enabled = roomId.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Phone, null); Spacer(Modifier.width(8.dp)); Text("Chờ cuộc gọi")
                }
            }
        }
    }
}

// ── Caller waiting / calling ──────────────────────────────────────────────────

@Composable
private fun WaitingForCalleePanel(roomId: String, onCancel: () -> Unit) {
    Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.HourglassTop, null, tint = ColorYellow)
            Text(
                "Đang chờ \"$roomId\" tham gia phòng…",
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onCancel) { Text("Huỷ") }
        }
    }
}

@Composable
private fun CallingPanel(calleeName: String, onCancel: () -> Unit) {
    Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(strokeWidth = 2.dp, color = ColorYellow, modifier = Modifier.size(20.dp))
            Text("Đang đổ chuông $calleeName…", modifier = Modifier.weight(1f))
            Button(onClick = onCancel, colors = ButtonDefaults.buttonColors(containerColor = ColorRed)) {
                Icon(Icons.Default.CallEnd, null); Spacer(Modifier.width(4.dp)); Text("Huỷ")
            }
        }
    }
}

// ── Callee incoming ───────────────────────────────────────────────────────────

@Composable
private fun IncomingPanel(callerName: String, onAccept: () -> Unit, onReject: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("📞 Cuộc gọi đến", style = MaterialTheme.typography.labelMedium)
            Text(callerName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                FilledIconButton(
                    onClick = onReject,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = ColorRed),
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(Icons.Default.CallEnd, "Từ chối", tint = Color.White)
                }
                FilledIconButton(
                    onClick = onAccept,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = ColorGreen),
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(Icons.Default.Call, "Nghe", tint = Color.White)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                Text("Từ chối", style = MaterialTheme.typography.labelSmall)
                Text("Nghe", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ── In-call ───────────────────────────────────────────────────────────────────

@Composable
private fun InCallPanel(onLeave: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Đang trong cuộc gọi", color = ColorGreen, modifier = Modifier.weight(1f))
        Button(onClick = onLeave, colors = ButtonDefaults.buttonColors(containerColor = ColorRed)) {
            Icon(Icons.Default.CallEnd, null); Spacer(Modifier.width(4.dp)); Text("Kết thúc")
        }
    }
}

// ── Terminal outcomes ─────────────────────────────────────────────────────────

@Composable
private fun OutcomePanel(title: String, subtitle: String?, color: Color, onDismiss: () -> Unit) {
    Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = color, fontWeight = FontWeight.SemiBold)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedButton(onClick = onDismiss) { Text("Đóng") }
        }
    }
}

// ── Generic status row ────────────────────────────────────────────────────────

@Composable
private fun StatusRow(text: String, spinning: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(8.dp),
    ) {
        if (spinning) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
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
            AndroidView(factory = { renderer }, modifier = Modifier.fillMaxSize())
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
        VideoCallState.Idle -> "● Sẵn sàng" to Color.Gray
        VideoCallState.Connecting -> "● Đang kết nối…" to ColorYellow
        VideoCallState.WaitingForCallee -> "● Chờ người nghe" to ColorYellow
        is VideoCallState.Calling -> "● Đang đổ chuông" to ColorYellow
        is VideoCallState.Incoming -> "● Cuộc gọi đến" to ColorGreen
        is VideoCallState.PeerReady -> "● Peer kết nối" to ColorGreen
        VideoCallState.InCall -> "● Đang gọi" to ColorGreen
        is VideoCallState.Rejected -> "● Bị từ chối" to ColorRed
        VideoCallState.Cancelled -> "● Đã huỷ" to ColorYellow
        VideoCallState.NoAnswer -> "● Không trả lời" to ColorYellow
        is VideoCallState.Busy -> "● Bận" to ColorRed
        is VideoCallState.Error -> "● Lỗi: ${state.message}" to ColorRed
    }
    Text(text, style = MaterialTheme.typography.labelSmall, color = color)
}
