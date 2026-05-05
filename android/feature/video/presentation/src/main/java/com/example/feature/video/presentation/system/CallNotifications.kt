package com.example.feature.video.presentation.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the two notifications used during a call:
 *  - Incoming: high-importance, full-screen intent → opens MainActivity.
 *  - Ongoing: low-importance, sticky — keeps the foreground service alive.
 *
 * Action buttons (Accept / Reject from the notification) are intentionally
 * omitted in this iteration — tapping the notification opens the app where
 * the user uses the in-app Accept / Reject buttons.
 */
@Singleton
class CallNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        ensureChannels()
    }

    private fun ensureChannels() {
        val incoming = NotificationChannel(
            CHANNEL_INCOMING,
            "Cuộc gọi đến",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Thông báo cuộc gọi đến"
            setSound(null, null)            // ringtone handled by RingtonePlayer
            enableVibration(false)          // vibration handled by RingtonePlayer
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val ongoing = NotificationChannel(
            CHANNEL_ONGOING,
            "Cuộc gọi đang diễn ra",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Trạng thái cuộc gọi đang hoạt động"
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannels(listOf(incoming, ongoing))
    }

    /** Full-screen-intent incoming notification — wakes the screen. */
    fun buildIncoming(callerName: String): Notification {
        val full = openAppPendingIntent(action = ACTION_OPEN_INCOMING, callerName = callerName)
        return NotificationCompat.Builder(context, CHANNEL_INCOMING)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("Cuộc gọi đến")
            .setContentText(callerName)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    /** Low-priority ongoing notification — required by the foreground service. */
    fun buildOngoing(text: String): Notification {
        val open = openAppPendingIntent(action = ACTION_OPEN_INCOMING, callerName = null)
        return NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(android.R.drawable.sym_call_outgoing)
            .setContentTitle("Cuộc gọi")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .build()
    }

    fun showIncoming(id: Int, callerName: String) {
        nm.notify(id, buildIncoming(callerName))
    }

    fun cancel(id: Int) = nm.cancel(id)

    private fun openAppPendingIntent(action: String, callerName: String?): PendingIntent {
        // Target MainActivity by class name string so the video module doesn't
        // need a compile-time dependency on the app module.
        val intent = Intent(action).apply {
            component = ComponentName(context, "com.example.wschat.MainActivity")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (callerName != null) putExtra(EXTRA_CALLER_NAME, callerName)
        }
        return PendingIntent.getActivity(
            context, REQUEST_OPEN, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_INCOMING = "ws_calls_incoming"
        const val CHANNEL_ONGOING  = "ws_calls_ongoing"
        const val NOTIF_ID_INCOMING = 1001
        const val NOTIF_ID_ONGOING  = 1002

        const val ACTION_OPEN_INCOMING = "com.example.wschat.OPEN_INCOMING_CALL"
        const val EXTRA_CALLER_NAME    = "caller_name"

        private const val REQUEST_OPEN = 100
    }
}
