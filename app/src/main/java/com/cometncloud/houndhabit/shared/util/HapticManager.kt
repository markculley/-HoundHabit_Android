package com.cometncloud.houndhabit.shared.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Single-tap and patterned haptic feedback, parallel to the iOS HapticManager.
 *
 * Call [init] once from MainActivity.onCreate, then use the static methods.
 * Calls become no-ops if the device has no vibrator or [init] was never
 * called. Every call also logs to TAG="Haptic" so emulators (which have no
 * physical vibrator) can be verified via logcat.
 *
 * Requires the VIBRATE permission, declared in the manifest.
 */
object HapticManager {
    private const val TAG = "Haptic"
    private var vibrator: Vibrator? = null

    fun init(context: Context) {
        val app = context.applicationContext
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            app.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun light() = oneShot("light", millis = 10)
    fun medium() = oneShot("medium", millis = 25)
    fun heavy() = oneShot("heavy", millis = 50)

    /** Heavy impact followed by a quick double-tap — used at timer end. */
    fun timerComplete() {
        Log.d(TAG, "timerComplete")
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        // pattern: [wait, vibrate, wait, vibrate, wait, vibrate]
        val pattern = longArrayOf(0, 50, 150, 30, 100, 30)
        v.vibrate(VibrationEffect.createWaveform(pattern, /* repeat = */ -1))
    }

    private fun oneShot(label: String, millis: Long) {
        Log.d(TAG, label)
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}