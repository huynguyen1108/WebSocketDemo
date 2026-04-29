package com.example.feature.stock.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.common.ConnectionState
import com.example.feature.stock.domain.model.MarketIndex
import com.example.feature.stock.domain.model.StockItem
import com.example.feature.stock.presentation.SortMode
import com.example.feature.stock.presentation.StockUiState
import com.example.feature.stock.presentation.StockViewModel
import com.example.feature.stock.presentation.ui.components.MarketIndexCard
import com.example.feature.stock.presentation.ui.components.StockListItem
import com.example.feature.stock.presentation.ui.components.ColorDown
import com.example.feature.stock.presentation.ui.components.ColorFlat
import com.example.feature.stock.presentation.ui.components.ColorUp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(
    modifier: Modifier = Modifier,
    onTradeClick: ((String) -> Unit)? = null,
    onLogout: () -> Unit = {},
    viewModel: StockViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.logoutEvent.collect { onLogout() }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Thị trường chứng khoán", fontWeight = FontWeight.Bold)
                        StatusLine(state.connectionState)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    val connState = state.connectionState
                    if (connState is ConnectionState.Connected) {
                        IconButton(onClick = viewModel::disconnect) {
                            Icon(Icons.Default.WifiOff, "Disconnect", tint = ColorDown)
                        }
                    } else if (connState is ConnectionState.Connecting) {
                        CircularProgressIndicator(modifier = Modifier.padding(12.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = viewModel::connect) {
                            Icon(Icons.Default.Wifi, "Connect", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = viewModel::logout) {
                        Icon(Icons.Default.Logout, "Logout", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Loading indicator khi đang connecting
            AnimatedVisibility(state.connectionState is ConnectionState.Connecting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Panel nhập URL khi chưa kết nối
            AnimatedVisibility(
                state.connectionState is ConnectionState.Idle ||
                    state.connectionState is ConnectionState.Disconnected ||
                    state.connectionState is ConnectionState.Error,
            ) {
                ConnectPanel(
                    serverUrl = state.serverUrl,
                    connectionState = state.connectionState,
                    onUrlChange = viewModel::onServerUrlChange,
                    onConnect = viewModel::connect,
                )
            }

            // Nội dung chính
            AnimatedVisibility(state.stocks.isNotEmpty() || state.indices.isNotEmpty()) {
                MarketContent(
                    state = state,
                    onSearchChange = viewModel::onSearchQueryChange,
                    onSortChange = viewModel::onSortModeChange,
                    onTradeClick = onTradeClick,
                )
            }
        }
    }
}

// ── Sub-composables ───────────────────────────────────────────────────────────

@Composable
private fun StatusLine(state: ConnectionState) {
    val text = when (state) {
        is ConnectionState.Connected -> "● LIVE"
        is ConnectionState.Connecting -> "● Đang kết nối..."
        is ConnectionState.Error -> "● Lỗi: ${state.message}"
        else -> "● Chưa kết nối"
    }
    val color = when (state) {
        is ConnectionState.Connected -> ColorUp
        is ConnectionState.Connecting -> ColorFlat
        is ConnectionState.Error -> ColorDown
        else -> Color.Gray
    }
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
private fun ConnectPanel(
    serverUrl: String,
    connectionState: ConnectionState,
    onUrlChange: (String) -> Unit,
    onConnect: () -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            if (connectionState is ConnectionState.Error) {
                Text(
                    text = "⚠ ${connectionState.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ColorDown,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = onUrlChange,
                    label = { Text("WebSocket Server URL") },
                    placeholder = { Text("ws://10.0.2.2:8080") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onConnect) { Text("Kết nối") }
            }
        }
    }
}

@Composable
private fun MarketContent(
    state: StockUiState,
    onSearchChange: (String) -> Unit,
    onSortChange: (SortMode) -> Unit,
    onTradeClick: ((String) -> Unit)? = null,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {

        // ── Index cards ──────────────────────────────────────────────────────
        if (state.indices.isNotEmpty()) {
            item(key = "indices") {
                IndicesRow(state.indices)
            }
        }

        // ── Market summary bar ───────────────────────────────────────────────
        if (state.stocks.isNotEmpty()) {
            item(key = "summary") {
                MarketSummaryBar(state.stocks)
            }
        }

        // ── Search + Sort toolbar ────────────────────────────────────────────
        item(key = "toolbar") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Tìm mã / tên...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Default.Close, null)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.large,
                )
                Spacer(Modifier.width(8.dp))
                Box {
                    IconButton(onClick = { sortMenuOpen = true }) {
                        Icon(Icons.Default.Sort, "Sắp xếp", tint = MaterialTheme.colorScheme.primary)
                    }
                    DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                        for (mode in SortMode.entries) {
                            DropdownMenuItem(
                                text = { Text(mode.label) },
                                onClick = { onSortChange(mode); sortMenuOpen = false },
                            )
                        }
                    }
                }
            }
        }

        // ── Stock list ───────────────────────────────────────────────────────
        items(state.filteredStocks, key = { it.symbol }) { stock ->
            StockListItem(stock = stock, onTradeClick = onTradeClick)
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun IndicesRow(indices: List<MarketIndex>) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        Text(
            text = "CHỈ SỐ THỊ TRƯỜNG",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(indices, key = { it.name }) { index ->
                MarketIndexCard(index)
            }
        }
    }
}

@Composable
private fun MarketSummaryBar(stocks: List<StockItem>) {
    val ups = stocks.count { it.isUp }
    val downs = stocks.count { it.isDown }
    val flats = stocks.size - ups - downs

    Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("PHIÊN GIAO DỊCH", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("↑ $ups", style = MaterialTheme.typography.labelMedium, color = ColorUp, fontWeight = FontWeight.Bold)
            Text("─ $flats", style = MaterialTheme.typography.labelMedium, color = ColorFlat, fontWeight = FontWeight.Bold)
            Text("↓ $downs", style = MaterialTheme.typography.labelMedium, color = ColorDown, fontWeight = FontWeight.Bold)
        }
    }
}
