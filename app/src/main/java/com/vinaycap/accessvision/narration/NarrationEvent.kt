package com.vinaycap.accessvision.narration

/**
 * Represents a narration event with priority level.
 *
 * Events are dispatched through the [PriorityDispatcher] which determines
 * whether to interrupt current speech, queue the event, or drop it.
 *
 * Priority levels:
 *   HAZARD (1):    Immediate interrupt — obstacle within ~1.5m
 *   NAVIGATION (2): Queued TTS — signs, directions
 *   AMBIENT (3):    Periodic summary — surroundings, every 5-8 seconds
 */
data class NarrationEvent(
    val text: String,
    val priority: Priority,
    val hapticPattern: HapticPattern = HapticPattern.NONE,
    val timestampMs: Long = System.currentTimeMillis()
) : Comparable<NarrationEvent> {

    override fun compareTo(other: NarrationEvent): Int {
        return this.priority.level.compareTo(other.priority.level)
    }
}

/**
 * Event priority levels.
 * Lower numeric level = higher priority.
 */
enum class Priority(val level: Int, val label: String) {
    HAZARD(1, "Hazard"),
    NAVIGATION(2, "Navigation"),
    AMBIENT(3, "Ambient")
}

/**
 * Haptic feedback patterns for different event types.
 *
 * Each pattern defines a vibration sequence:
 * - HAZARD_PULSE: Strong double pulse (danger)
 * - CONFIRM_TAP: Single short tap (gesture acknowledged)
 * - ERROR_BUZZ: Triple short buzz (error/invalid)
 * - TOGGLE_CLICK: Medium click (mode toggle)
 * - NONE: No haptic feedback
 */
enum class HapticPattern(val timings: LongArray, val amplitudes: IntArray) {
    HAZARD_PULSE(
        longArrayOf(0, 150, 100, 150),      // wait, vibrate, pause, vibrate
        intArrayOf(0, 255, 0, 255)           // off, max, off, max
    ),
    CONFIRM_TAP(
        longArrayOf(0, 50),
        intArrayOf(0, 180)
    ),
    ERROR_BUZZ(
        longArrayOf(0, 40, 40, 40, 40, 40),
        intArrayOf(0, 200, 0, 200, 0, 200)
    ),
    TOGGLE_CLICK(
        longArrayOf(0, 80),
        intArrayOf(0, 150)
    ),
    NONE(
        longArrayOf(),
        intArrayOf()
    )
}
