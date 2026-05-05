package com.example.feature.video.presentation.system

import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PROXIMITY_SCREEN_OFF wake lock: turns the screen off when the user holds the
 * phone to their ear during a call, turns it back on when they pull away.
 *
 * Useful even for video calls when the user temporarily switches to audio mode
 * by holding the phone against their head.
 */
@Singleton
class ProximityWakeLock @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val supported = powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)
    private var lock: PowerManager.WakeLock? = null

    fun acquire() {
        if (!supported || lock?.isHeld == true) return
        lock = powerManager.newWakeLock(
            PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
            "wschat:proximity",
        ).also { it.acquire(30 * 60 * 1000L) }
    }

    fun release() {
        lock?.let {
            if (it.isHeld) it.release()
        }
        lock = null
    }
}
