package com.example.feature.chat.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.feature.chat.domain.model.ConnectionState

@Composable
fun ConnectionStatusBar(state: ConnectionState, modifier: Modifier = Modifier) {
    val (label, color) = when (state) {
        is ConnectionState.Idle -> "Idle" to Color.Gray
        is ConnectionState.Connecting -> "Connecting..." to Color(0xFFFFA500)
        is ConnectionState.Connected -> "Connected" to Color(0xFF4CAF50)
        is ConnectionState.Disconnected -> "Disconnected" to Color.Gray
        is ConnectionState.Error -> "Error: ${state.message}" to MaterialTheme.colorScheme.error
    }

    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(300),
        label = "statusColor",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(animatedColor)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
    }
}
