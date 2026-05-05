package com.example.feature.video.presentation.system

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays the device's default ringtone + a vibration pattern while the callee's
 * UI is showing an Incoming call. Ringtone uses USAGE_NOTIFICATION_RINGTONE so
 * it routes through the ring stream (respects silent mode, ducks media).
 */
@Singleton
class RingtonePlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var ringtone: Ringtone? = null
    private val vibrator: Vibrator? = run {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun start() {
        if (ringtone?.isPlaying == true) return
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE) ?: return
        ringtone = RingtoneManager.getRingtone(context, uri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            play()
        }

        // Pattern: 0ms wait, vibrate 800ms, pause 1000ms — repeat.
        vibrator?.let {
            val timings = longArrayOf(0, 800, 1000)
            val amplitudes = intArrayOf(0, 255, 0)
            it.vibrate(VibrationEffect.createWaveform(timings, amplitudes, 0))
        }
    }

    fun stop() {
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
    }
}
