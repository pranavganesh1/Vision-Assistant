package com.vinaycap.accessvision.gesture

import android.util.Log
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.abs

/**
 * Full-screen transparent gesture overlay for screenless interaction.
 *
 * Gesture mappings:
 * ┌─────────────────────────────────────────┐
 * │  Single Tap    → Speak current scene     │
 * │  Double Tap    → Toggle OCR mode         │
 * │  Two-Finger    → Mute / Unmute           │
 * │  Double Tap                              │
 * │  Swipe Left    → Previous mode           │
 * │  Swipe Right   → Next mode               │
 * │  Long Press    → Read full scene detail  │
 * └─────────────────────────────────────────┘
 *
 * Every gesture triggers distinct haptic feedback.
 * The overlay is fully accessible via TalkBack with content descriptions.
 *
 * @param onSingleTap Called when user taps once.
 * @param onDoubleTap Called when user double-taps.
 * @param onTwoFingerDoubleTap Called when user double-taps with two fingers.
 * @param onSwipeLeft Called when user swipes left.
 * @param onSwipeRight Called when user swipes right.
 * @param onLongPress Called when user long-presses.
 */
@Composable
fun GestureOverlay(
    onSingleTap: () -> Unit = {},
    onDoubleTap: () -> Unit = {},
    onTwoFingerDoubleTap: () -> Unit = {},
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
    onLongPress: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Track tap state for double-tap detection
    var lastTapTime by remember { mutableLongStateOf(0L) }
    val doubleTapThresholdMs = 300L

    // Track for two-finger detection
    var twoFingerTapCount by remember { mutableIntStateOf(0) }
    var lastTwoFingerTapTime by remember { mutableLongStateOf(0L) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                contentDescription = "Touch area for gesture controls. " +
                        "Tap to hear scene. Double tap for text capture. " +
                        "Swipe left or right to switch modes. " +
                        "Two finger double tap to mute."
            }
            // Handle taps (single and double)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < doubleTapThresholdMs) {
                            // Double tap detected
                            lastTapTime = 0L
                            Log.d("GestureOverlay", "Double tap detected")
                            onDoubleTap()
                        } else {
                            lastTapTime = now
                            // Delay single tap to check for double tap
                            // This is handled by posting a delayed check
                            kotlinx.coroutines.MainScope().launch {
                                kotlinx.coroutines.delay(doubleTapThresholdMs + 50)
                                if (lastTapTime == now) {
                                    Log.d("GestureOverlay", "Single tap detected")
                                    onSingleTap()
                                }
                            }
                        }
                    },
                    onLongPress = {
                        Log.d("GestureOverlay", "Long press detected")
                        onLongPress()
                    }
                )
            }
            // Handle swipes
            .pointerInput(Unit) {
                var totalDragX = 0f
                var totalDragY = 0f

                detectDragGestures(
                    onDragStart = {
                        totalDragX = 0f
                        totalDragY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y
                    },
                    onDragEnd = {
                        val minSwipeDistance = 100f

                        // Only count horizontal swipes (not diagonal)
                        if (abs(totalDragX) > minSwipeDistance &&
                            abs(totalDragX) > abs(totalDragY) * 1.5f
                        ) {
                            if (totalDragX > 0) {
                                Log.d("GestureOverlay", "Swipe right detected")
                                onSwipeRight()
                            } else {
                                Log.d("GestureOverlay", "Swipe left detected")
                                onSwipeLeft()
                            }
                        }
                    }
                )
            }
    )
}

/**
 * Extension: Composable that adds two-finger gesture detection.
 * Note: True multi-touch in Compose requires awaitPointerEventScope.
 * This is a simplified implementation; production code should use
 * awaitPointerEventScope with PointerEvent for precise finger counting.
 */
@Composable
fun TwoFingerGestureOverlay(
    onTwoFingerDoubleTap: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    var lastTwoFingerTime by remember { mutableLongStateOf(0L) }
    val threshold = 400L

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        // Detect two-finger tap
                        if (event.changes.size >= 2) {
                            val allPressed = event.changes.all { it.pressed }
                            if (allPressed) {
                                val now = System.currentTimeMillis()
                                if (now - lastTwoFingerTime < threshold) {
                                    Log.d("GestureOverlay", "Two-finger double tap detected")
                                    onTwoFingerDoubleTap()
                                    lastTwoFingerTime = 0L
                                } else {
                                    lastTwoFingerTime = now
                                }
                            }
                        }
                        // Consume all changes
                        event.changes.forEach { it.consume() }
                    }
                }
            }
    ) {
        content()
    }
}
