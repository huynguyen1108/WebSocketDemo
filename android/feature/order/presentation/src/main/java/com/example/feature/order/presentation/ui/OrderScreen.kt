package com.example.feature.order.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.feature.chat.domain.model.ConnectionState
import com.example.feature.order.presentation.OrderTab
import com.example.feature.order.presentation.OrderViewModel
import com.example.feature.order.presentation.ui.components.OrderListItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderScreen(
    prefilledSymbol: String? = null,
    modifier: Modifier = Modifier,
    viewModel: OrderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    var showForm by remember { mutableStateOf(false) }

    // Pre-fill symbol if coming from stock screen
    LaunchedEffect(prefilledSymbol) {
        if (!prefilledSymbol.isNullOrBlank()) {
            viewModel.prepareOrder(prefilledSymbol)
            showForm = true
        }
    }

    // Show success/error messages
    LaunchedEffect(state.submitError, state.successMessage) {
        val msg = state.submitError ?: state.successMessage
        if (msg != null) {
            snackbarHost.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Lệnh giao dịch", fontWeight = FontWeight.Bold)
                        TradingStatusLine(state.connectionState)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    when (val conn = state.connectionState) {
                        is ConnectionState.Connected -> {
                            IconButton(onClick = viewModel::disconnect) {
                                Icon(Icons.Default.WifiOff, "Ngắt kết nối", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        is ConnectionState.Connecting -> {
                            CircularProgressIndicator(modifier = Modifier.padding(12.dp), strokeWidth = 2.dp)
                        }
                        else -> {
                            IconButton(onClick = viewModel::connect) {
                                Icon(Icons.Default.Wifi, "Kết nối", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.connectionState is ConnectionState.Connected) {
                ExtendedFloatingActionButton(
                    text = { Text("Đặt lệnh") },
                    icon = { Icon(Icons.Default.Add, null) },
                    onClick = {
                        viewModel.prepareOrder("")
                        showForm = true
                    },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedVisibility(state.connectionState is ConnectionState.Connecting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Tab bar
            TabRow(selectedTabIndex = state.activeTab.ordinal) {
                OrderTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = state.activeTab.ordinal == index,
                        onClick = { viewModel.onTabChange(tab) },
                        text = {
                            val count = if (tab == OrderTab.ACTIVE) state.activeOrders.size
                            else state.historyOrders.size
                            Text("${tab.label} ($count)")
                        },
                    )
                }
            }

            if (state.displayedOrders.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (state.connectionState is ConnectionState.Connected)
                            "Chưa có lệnh nào" else "Kết nối để xem lệnh",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.displayedOrders, key = { it.orderId }) { order ->
                        OrderListItem(
                            order = order,
                            onCancel = if (order.isActive) viewModel::cancelOrder else null,
                        )
                    }
                }
            }
        }
    }

    if (showForm) {
        OrderFormBottomSheet(
            viewModel = viewModel,
            sheetState = sheetState,
            onDismiss = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (!sheetState.isVisible) showForm = false
                }
            },
        )
    }
}

@Composable
private fun TradingStatusLine(state: ConnectionState) {
    val (text, color) = when (state) {
        is ConnectionState.Connected -> "● LIVE" to MaterialTheme.colorScheme.primary
        is ConnectionState.Connecting -> "● Đang kết nối..." to MaterialTheme.colorScheme.tertiary
        is ConnectionState.Error -> "● Lỗi: ${state.message}" to MaterialTheme.colorScheme.error
        else -> "● Chưa kết nối" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
}
