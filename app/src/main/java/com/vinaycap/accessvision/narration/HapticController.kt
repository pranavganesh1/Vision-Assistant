package com.vinaycap.accessvision.narration

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Controls haptic feedback for accessibility alerts and gesture confirmations.
 *
 * Provides distinct vibration patterns for different event types:
 * - Hazard alerts: Strong double pulse
 * - Gesture confirmations: Short tap
 * - Mode toggles: Medium click
 * - Errors: Triple buzz
 *
 * Uses the Vibrator API with amplitude control when available (API 26+).
 *
 * Usage:
 *   val haptic = HapticController(context)
 *   haptic.play(HapticPattern.HAZARD_PULSE)
 *   haptic.play(HapticPattern.CONFIRM_TAP)
 */
class HapticController(context: Context) {

    companion object {
        private const val TAG = "HapticController"
    }

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private val hasAmplitudeControl: Boolean = vibrator?.hasAmplitudeControl() == true

    init {
        Log.i(TAG, "Haptic controller initialized. Amplitude control: $hasAmplitudeControl")
    }

    /**
     * Plays a haptic pattern.
     *
     * @param pattern The haptic pattern to play.
     */
    fun play(pattern: HapticPattern) {
        if (pattern == HapticPattern.NONE) return
        if (vibrator == null || !vibrator.hasVibrator()) {
            Log.w(TAG, "No vibrator available")
            return
        }

        try {
            if (hasAmplitudeControl) {
                val effect = VibrationEffect.createWaveform(
                    pattern.timings,
                    pattern.amplitudes,
                    -1 // Don't repeat
                )
                vibrator.vibrate(effect)
            } else {
                // Fallback for devices without amplitude control
                val effect = VibrationEffect.createWaveform(
                    pattern.timings,
                    -1
                )
                vibrator.vibrate(effect)
            }
            Log.d(TAG, "Playing haptic: ${pattern.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Haptic playback failed: ${e.message}")
        }
    }

    /**
     * Cancels any ongoing vibration.
     */
    fun cancel() {
        vibrator?.cancel()
    }
}
