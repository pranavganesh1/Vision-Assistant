package com.vinaycap.accessvision.narration

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages on-device Text-to-Speech with speech rate control and interrupt support.
 *
 * Features:
 * - Uses Android's built-in TTS engine (fully offline with downloaded voice data)
 * - Adjustable speech rate (1.0x to 1.75x) for user preference
 * - Support for immediate interrupt (for hazard alerts)
 * - Utterance completion callbacks for queue management
 * - Mute/unmute support
 *
 * Usage:
 *   val tts = TtsController(context) {
 *       // called when an utterance completes
 *   }
 *   tts.speak("Hello", interrupt = false)
 *   tts.interrupt("Warning: obstacle ahead!")
 *   tts.shutdown()
 */
class TtsController(
    context: Context,
    private val onUtteranceComplete: () -> Unit = {}
) {

    companion object {
        private const val TAG = "TtsController"
        const val DEFAULT_SPEECH_RATE = 1.2f
        const val MIN_SPEECH_RATE = 1.0f
        const val MAX_SPEECH_RATE = 1.75f
        private const val SPEECH_RATE_STEP = 0.25f
    }

    private var tts: TextToSpeech? = null
    private val isReady = AtomicBoolean(false)
    private val isMuted = AtomicBoolean(false)
    private val utteranceCounter = AtomicInteger(0)
    private var speechRate = DEFAULT_SPEECH_RATE

    /** Whether TTS is currently speaking */
    val isSpeaking: Boolean
        get() = tts?.isSpeaking == true

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    Log.e(TAG, "English language not supported on this device")
                } else {
                    tts?.setSpeechRate(speechRate)
                    tts?.setPitch(1.0f)
                    isReady.set(true)
                    Log.i(TAG, "TTS initialized successfully at ${speechRate}x")
                }
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
            }
        }

        setupUtteranceListener()
    }

    /**
     * Speaks the given text. Queues behind any currently playing utterance.
     *
     * @param text Text to speak.
     * @param interrupt If true, stops current speech and speaks immediately.
     */
    fun speak(text: String, interrupt: Boolean = false) {
        if (!isReady.get() || isMuted.get()) return

        val utteranceId = "utt_${utteranceCounter.incrementAndGet()}"
        val queueMode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD

        tts?.speak(text, queueMode, null, utteranceId)
        Log.d(TAG, "Speaking [${if (interrupt) "INTERRUPT" else "QUEUE"}]: \"${text.take(60)}\"")
    }

    /**
     * Immediately interrupts current speech and announces the given text.
     * Used for hazard-level events.
     */
    fun interrupt(text: String) {
        speak(text, interrupt = true)
    }

    /**
     * Stops all current and queued speech.
     */
    fun stop() {
        tts?.stop()
        Log.d(TAG, "TTS stopped")
    }

    /**
     * Toggles mute state. Returns the new mute state.
     */
    fun toggleMute(): Boolean {
        val newState = !isMuted.get()
        isMuted.set(newState)
        if (newState) {
            tts?.stop()
        }
        Log.i(TAG, "TTS muted: $newState")
        return newState
    }

    /** Whether TTS is currently muted */
    val muted: Boolean
        get() = isMuted.get()

    /**
     * Adjusts the speech rate. Cycles: 1.0 → 1.25 → 1.5 → 1.75 → 1.0
     * Returns the new speech rate.
     */
    fun cycleSpeechRate(): Float {
        speechRate += SPEECH_RATE_STEP
        if (speechRate > MAX_SPEECH_RATE) {
            speechRate = MIN_SPEECH_RATE
        }
        tts?.setSpeechRate(speechRate)
        Log.i(TAG, "Speech rate: ${speechRate}x")
        return speechRate
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "Utterance started: $utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "Utterance done: $utteranceId")
                onUtteranceComplete()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "Utterance error: $utteranceId")
                onUtteranceComplete()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e(TAG, "Utterance error: $utteranceId code=$errorCode")
                onUtteranceComplete()
            }
        })
    }

    /**
     * Releases TTS resources.
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady.set(false)
        Log.i(TAG, "TTS shutdown")
    }
}
