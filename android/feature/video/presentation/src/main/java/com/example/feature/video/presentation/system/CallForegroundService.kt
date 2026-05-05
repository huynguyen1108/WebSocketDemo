package com.example.feature.video.presentation.system

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * A bare-bones foreground service whose only purpose is to keep the app
 * process alive while a call is active or ringing — so the OS doesn't kill
 * the WebSocket connection or the WebRTC PeerConnection when the user
 * navigates away from the activity.
 *
 * The service does not own the call state; it just sits in the foreground
 * showing an ongoing notification. The actual signalling lives in the
 * @Singleton VideoWebSocketDataSource.
 */
@AndroidEntryPoint
class CallForegroundService : Service() {

    @Inject lateinit var notifications: CallNotifications

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "Cuộc gọi đang diễn ra"
        val notif = notifications.buildOngoing(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                CallNotifications.NOTIF_ID_ONGOING,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL,
            )
        } else {
            startForeground(CallNotifications.NOTIF_ID_ONGOING, notif)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TEXT = "text"

        fun start(context: Context, text: String) {
            val intent = Intent(context, CallForegroundService::class.java)
                .putExtra(EXTRA_TEXT, text)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallForegroundService::class.java))
        }
    }
}
