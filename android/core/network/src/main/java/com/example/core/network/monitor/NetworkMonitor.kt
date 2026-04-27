package com.example.core.network.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.net.NetworkRequest
import android.os.PowerManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observable singleton theo dõi:
 *  - networkAvailable: có mạng internet thực sự (VALIDATED) không
 *  - isDozing: thiết bị có đang ở Doze mode không
 *
 * Doze mode (API 23+) block network access theo chu kỳ khi màn hình tắt lâu.
 * Khi dozing=true, dù networkAvailable=true vẫn không connect được.
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)

    private val _networkAvailable = MutableStateFlow(isCurrentlyOnline())
    val networkAvailable: StateFlow<Boolean> = _networkAvailable.asStateFlow()

    private val _isDozing = MutableStateFlow(powerManager.isDeviceIdleMode)
    val isDozing: StateFlow<Boolean> = _isDozing.asStateFlow()

    // ── Network callback ──────────────────────────────────────────────────────

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {

        // Một network mới available (không có nghĩa là internet hoạt động)
        override fun onAvailable(network: Network) {
            // Chờ onCapabilitiesChanged để confirm VALIDATED
        }

        // Capabilities thay đổi — đây là thời điểm chính xác nhất để check
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            _networkAvailable.value = caps.hasCapability(NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NET_CAPABILITY_VALIDATED)
        }

        // Network bị mất — kiểm tra xem còn network nào khác không
        override fun onLost(network: Network) {
            _networkAvailable.value = isCurrentlyOnline()
        }
    }

    // ── Doze mode receiver ────────────────────────────────────────────────────

    private val dozeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED) {
                _isDozing.value = powerManager.isDeviceIdleMode
            }
        }
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)

        // API 34+ yêu cầu phải khai báo EXPORTED hay không
        ContextCompat.registerReceiver(
            context,
            dozeReceiver,
            IntentFilter(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun isCurrentlyOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NET_CAPABILITY_VALIDATED)
    }

    /** Có thể reconnect không — network available VÀ không đang Doze */
    val canReconnect: Boolean
        get() = networkAvailable.value && !isDozing.value
}
