package com.vinaycap.accessvision.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min

/**
 * On-device OCR engine using ML Kit's bundled (offline) text recognition.
 *
 * Features:
 * - Processes bitmaps through ML Kit's on-device text recognizer
 * - Estimates text area ratio to determine if text is significant (> 15% of frame)
 * - Provides Levenshtein distance-based deduplication to prevent repeating reads
 * - Supports both continuous scanning and snap-and-read modes
 *
 * Usage:
 *   val ocr = OcrEngine(context)
 *   val result = ocr.recognizeText(bitmap)
 *   if (result != null && !ocr.isDuplicate(result.text)) { ... }
 *   ocr.close()
 */
class OcrEngine(context: Context) : AutoCloseable {

    companion object {
        private const val TAG = "OcrEngine"

        /** Minimum text area ratio to consider text significant */
        const val MIN_TEXT_AREA_RATIO = 0.15f

        /** Levenshtein similarity threshold for deduplication (0.8 = 80% similar) */
        const val DEDUP_SIMILARITY_THRESHOLD = 0.8f

        /** Time window for deduplication (don't repeat same text within this window) */
        const val DEDUP_WINDOW_MS = 10_000L

        /** Minimum text length to consider valid */
        const val MIN_TEXT_LENGTH = 3
    }

    private val recognizer: TextRecognizer = TextRecognition.getClient(
        TextRecognizerOptions.Builder().build()
    )

    /** Recent text reads for deduplication */
    private val recentReads = mutableListOf<Pair<String, Long>>()

    /**
     * Performs text recognition on a bitmap frame.
     *
     * @param bitmap Input frame to scan for text.
     * @return OcrResult if text was found and significant, null otherwise.
     */
    suspend fun recognizeText(bitmap: Bitmap): OcrResult? {
        return suspendCancellableCoroutine { continuation ->
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val fullText = visionText.text.trim()

                    if (fullText.length < MIN_TEXT_LENGTH) {
                        continuation.resume(null)
                        return@addOnSuccessListener
                    }

                    // Estimate text area ratio from text block bounding boxes
                    val frameArea = bitmap.width.toFloat() * bitmap.height.toFloat()
                    var textArea = 0f
                    var totalConfidence = 0f
                    var blockCount = 0

                    for (block in visionText.textBlocks) {
                        block.boundingBox?.let { box ->
                            textArea += box.width().toFloat() * box.height().toFloat()
                        }
                        // ML Kit doesn't expose confidence directly per block,
                        // but we can use line-level confidence where available
                        for (line in block.lines) {
                            totalConfidence += (line.confidence ?: 0.8f)
                            blockCount++
                        }
                    }

                    val textAreaRatio = textArea / frameArea
                    val avgConfidence = if (blockCount > 0) totalConfidence / blockCount else 0f

                    val result = OcrResult(
                        text = fullText,
                        confidence = avgConfidence,
                        textAreaRatio = textAreaRatio
                    )

                    Log.d(TAG, "OCR: \"${fullText.take(50)}...\" area=${textAreaRatio} conf=$avgConfidence")
                    continuation.resume(result)
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "OCR failed: ${exception.message}")
                    continuation.resume(null)
                }
        }
    }

    /**
     * Checks if recognized text is significant enough to announce.
     * Text must occupy at least [MIN_TEXT_AREA_RATIO] of the frame.
     */
    fun isSignificant(result: OcrResult): Boolean {
        return result.textAreaRatio >= MIN_TEXT_AREA_RATIO &&
                result.text.length >= MIN_TEXT_LENGTH
    }

    /**
     * Checks if the text is a duplicate of a recent read using Levenshtein distance.
     * Prevents repeating the same text within the deduplication window.
     *
     * @param text The recognized text to check.
     * @return true if this text is too similar to a recent read.
     */
    fun isDuplicate(text: String): Boolean {
        val now = System.currentTimeMillis()

        // Purge expired reads
        recentReads.removeAll { (_, timestamp) ->
            now - timestamp > DEDUP_WINDOW_MS
        }

        // Check similarity against recent reads
        for ((recentText, _) in recentReads) {
            val similarity = computeLevenshteinSimilarity(text, recentText)
            if (similarity >= DEDUP_SIMILARITY_THRESHOLD) {
                Log.d(TAG, "Duplicate text detected (similarity=$similarity)")
                return true
            }
        }

        // Record this read
        recentReads.add(Pair(text, now))

        // Keep list bounded
        if (recentReads.size > 20) {
            recentReads.removeAt(0)
        }

        return false
    }

    /**
     * Clears the deduplication history.
     * Useful when switching modes or when user explicitly requests a new read.
     */
    fun clearHistory() {
        recentReads.clear()
    }

    /**
     * Computes Levenshtein similarity between two strings.
     * Returns a value between 0.0 (completely different) and 1.0 (identical).
     */
    private fun computeLevenshteinSimilarity(s1: String, s2: String): Float {
        val distance = levenshteinDistance(s1.lowercase(), s2.lowercase())
        val maxLen = maxOf(s1.length, s2.length)
        return if (maxLen == 0) 1.0f else 1.0f - (distance.toFloat() / maxLen)
    }

    /**
     * Computes the Levenshtein edit distance between two strings.
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length

        // Optimization: use two rows instead of full matrix
        var prevRow = IntArray(len2 + 1) { it }
        var currRow = IntArray(len2 + 1)

        for (i in 1..len1) {
            currRow[0] = i
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                currRow[j] = min(
                    min(currRow[j - 1] + 1, prevRow[j] + 1),
                    prevRow[j - 1] + cost
                )
            }
            val temp = prevRow
            prevRow = currRow
            currRow = temp
        }

        return prevRow[len2]
    }

    override fun close() {
        recognizer.close()
        recentReads.clear()
        Log.i(TAG, "OcrEngine resources released")
    }
}
