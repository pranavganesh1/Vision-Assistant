package com.vinaycap.accessvision.narration

import android.util.Log
import com.vinaycap.accessvision.ml.FrameDetectionResult
import com.vinaycap.accessvision.ml.OcrResult
import kotlinx.coroutines.*
import java.util.concurrent.PriorityBlockingQueue

/**
 * Priority-based audio-haptic dispatcher that manages narration flow.
 *
 * Implements a multi-level priority system to prevent cognitive sensory overload:
 *
 * Priority 1 (HAZARD - Immediate Interrupt):
 *   → Triggers haptic vibration pulse immediately
 *   → Interrupts any active TTS
 *   → Announces: "Warning: Obstacle direct center"
 *
 * Priority 2 (NAVIGATION / Signs):
 *   → Queued TTS: "Sign: Restroom to your right"
 *
 * Priority 3 (AMBIENT Surroundings):
 *   → Summarized every 5–8 seconds when stationary
 *   → "Desk on left, open doorway ahead"
 *
 * The dispatcher runs a continuous loop that:
 * 1. Pulls the highest-priority event from the queue
 * 2. Applies deduplication filtering
 * 3. Dispatches haptic feedback (if applicable)
 * 4. Dispatches TTS with appropriate queue/interrupt mode
 *
 * Usage:
 *   val dispatcher = PriorityDispatcher(ttsController, hapticController)
 *   dispatcher.start()
 *   dispatcher.onDetectionResult(frameResult)
 *   dispatcher.onOcrResult(ocrResult)
 *   // ... later
 *   dispatcher.stop()
 */
class PriorityDispatcher(
    private val tts: TtsController,
    private val haptic: HapticController
) {

    companion object {
        private const val TAG = "PriorityDispatcher"

        /** Ambient scene summary interval in milliseconds */
        const val AMBIENT_SUMMARY_INTERVAL_MS = 6_000L

        /** Maximum queue depth to prevent memory issues */
        const val MAX_QUEUE_SIZE = 20
    }

    private val eventQueue = PriorityBlockingQueue<NarrationEvent>(MAX_QUEUE_SIZE)
    private val deduplication = DeduplicationEngine()
    private var dispatcherJob: Job? = null
    private var ambientTimerJob: Job? = null
    private val dispatcherScope = CoroutineScope(
        Dispatchers.Default + SupervisorJob() + CoroutineName("NarrationDispatcher")
    )

    /** Last scene summary for ambient narration */
    @Volatile
    private var lastSceneSummary: String = ""

    /** Whether the dispatcher is running */
    @Volatile
    var isRunning = false
        private set

    /**
     * Starts the dispatch loop and ambient timer.
     */
    fun start() {
        if (isRunning) return
        isRunning = true

        // Start the main dispatch loop
        dispatcherJob = dispatcherScope.launch {
            Log.i(TAG, "Dispatch loop started")
            while (isActive) {
                try {
                    // Poll with timeout to allow cancellation checks
                    val event = withTimeoutOrNull(500) {
                        withContext(Dispatchers.IO) {
                            eventQueue.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS)
                        }
                    }

                    if (event != null) {
                        processEvent(event)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Dispatch error: ${e.message}")
                }
            }
        }

        // Start ambient summary timer
        ambientTimerJob = dispatcherScope.launch {
            while (isActive) {
                delay(AMBIENT_SUMMARY_INTERVAL_MS)
                if (lastSceneSummary.isNotBlank()) {
                    submitEvent(
                        NarrationEvent(
                            text = lastSceneSummary,
                            priority = Priority.AMBIENT
                        )
                    )
                }
            }
        }
    }

    /**
     * Processes a single narration event.
     */
    private fun processEvent(event: NarrationEvent) {
        // Apply deduplication
        if (!deduplication.shouldAnnounce(event)) return

        // Dispatch haptic feedback first (immediate)
        if (event.hapticPattern != HapticPattern.NONE) {
            haptic.play(event.hapticPattern)
        }

        // Dispatch TTS based on priority
        when (event.priority) {
            Priority.HAZARD -> {
                tts.interrupt(event.text)
                Log.w(TAG, "⚠️ HAZARD: ${event.text}")
            }
            Priority.NAVIGATION -> {
                tts.speak(event.text, interrupt = false)
                Log.i(TAG, "📍 NAV: ${event.text}")
            }
            Priority.AMBIENT -> {
                // Only speak ambient if TTS is not currently busy with higher priority
                if (!tts.isSpeaking) {
                    tts.speak(event.text, interrupt = false)
                    Log.d(TAG, "🌍 AMBIENT: ${event.text}")
                }
            }
        }
    }

    /**
     * Submits a narration event to the priority queue.
     */
    fun submitEvent(event: NarrationEvent) {
        if (!isRunning) return

        // Drop events if queue is full (keep space for hazards)
        if (eventQueue.size >= MAX_QUEUE_SIZE) {
            if (event.priority != Priority.HAZARD) {
                Log.w(TAG, "Queue full, dropping ${event.priority}: ${event.text.take(30)}")
                return
            }
            // For hazards, remove the lowest priority event
            val lowest = eventQueue.maxByOrNull { it.priority.level }
            if (lowest != null) {
                eventQueue.remove(lowest)
            }
        }

        eventQueue.offer(event)
    }

    /**
     * Processes a frame detection result and generates appropriate narration events.
     *
     * @param result The frame detection result from the ML pipeline.
     */
    fun onDetectionResult(result: FrameDetectionResult) {
        // Process hazards first (immediate)
        for (detection in result.hazards()) {
            submitEvent(
                NarrationEvent(
                    text = detection.toNarration(),
                    priority = Priority.HAZARD,
                    hapticPattern = HapticPattern.HAZARD_PULSE
                )
            )
        }

        // Update ambient scene summary
        lastSceneSummary = result.sceneSummary()
    }

    /**
     * Processes an OCR result and generates a navigation-priority narration event.
     *
     * @param result The OCR result from the text recognition engine.
     */
    fun onOcrResult(result: OcrResult) {
        submitEvent(
            NarrationEvent(
                text = "Sign reads: ${result.text}",
                priority = Priority.NAVIGATION
            )
        )
    }

    /**
     * Forces an immediate scene summary announcement.
     * Used when the user taps the screen.
     */
    fun announceCurrentScene() {
        val summary = lastSceneSummary.ifBlank { "No objects detected. Path appears clear." }
        tts.interrupt(summary)
    }

    /**
     * Stops the dispatch loop and clears the queue.
     */
    fun stop() {
        isRunning = false
        dispatcherJob?.cancel()
        ambientTimerJob?.cancel()
        eventQueue.clear()
        deduplication.clear()
        Log.i(TAG, "Dispatch loop stopped")
    }

    /**
     * Clears deduplication cache. Useful when switching modes.
     */
    fun clearDeduplication() {
        deduplication.clear()
    }
}
