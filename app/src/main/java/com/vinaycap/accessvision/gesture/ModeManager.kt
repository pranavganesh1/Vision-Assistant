package com.vinaycap.accessvision.gesture

import android.util.Log

/**
 * Manages operational modes for the accessibility assistant.
 *
 * Modes:
 * - FULL_NAVIGATION: All features active (detection + OCR + ambient narration)
 * - TEXT_READER:      OCR-only mode for reading signs and text
 * - HAZARD_WARNING:   Detection-only mode, only announces immediate hazards
 *
 * Mode switching is triggered by swipe gestures and provides
 * haptic + TTS confirmation for the user.
 */
class ModeManager {

    companion object {
        private const val TAG = "ModeManager"
    }

    /**
     * Operational modes for the vision assistant.
     */
    enum class Mode(val displayName: String, val description: String) {
        FULL_NAVIGATION(
            "Full Navigation",
            "Navigation mode active. Detecting obstacles and reading signs."
        ),
        TEXT_READER(
            "Text Reader",
            "Text reader mode active. Scanning for signs and text only."
        ),
        HAZARD_WARNING(
            "Hazard Warning",
            "Hazard warning mode active. Alerting for immediate obstacles only."
        )
    }

    /** Current active mode */
    var currentMode: Mode = Mode.FULL_NAVIGATION
        private set

    /** Callback when mode changes */
    var onModeChanged: (Mode) -> Unit = {}

    /**
     * Cycles to the next mode (forward: swipe right).
     * FULL_NAVIGATION → TEXT_READER → HAZARD_WARNING → FULL_NAVIGATION
     *
     * @return The new active mode.
     */
    fun nextMode(): Mode {
        val modes = Mode.entries
        val nextIndex = (modes.indexOf(currentMode) + 1) % modes.size
        currentMode = modes[nextIndex]
        Log.i(TAG, "Mode switched to: ${currentMode.displayName}")
        onModeChanged(currentMode)
        return currentMode
    }

    /**
     * Cycles to the previous mode (backward: swipe left).
     *
     * @return The new active mode.
     */
    fun previousMode(): Mode {
        val modes = Mode.entries
        val prevIndex = (modes.indexOf(currentMode) - 1 + modes.size) % modes.size
        currentMode = modes[prevIndex]
        Log.i(TAG, "Mode switched to: ${currentMode.displayName}")
        onModeChanged(currentMode)
        return currentMode
    }

    /**
     * Whether object detection should be active in the current mode.
     */
    val isDetectionActive: Boolean
        get() = currentMode != Mode.TEXT_READER

    /**
     * Whether OCR should be active in the current mode.
     */
    val isOcrActive: Boolean
        get() = currentMode != Mode.HAZARD_WARNING

    /**
     * Whether ambient narration should be active in the current mode.
     */
    val isAmbientActive: Boolean
        get() = currentMode == Mode.FULL_NAVIGATION
}
