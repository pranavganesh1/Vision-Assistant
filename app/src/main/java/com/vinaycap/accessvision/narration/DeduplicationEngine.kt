package com.vinaycap.accessvision.narration

import android.util.Log
import kotlin.math.min

/**
 * Deduplication engine for narration events.
 *
 * Prevents cognitive overload by filtering out repeated announcements:
 * - Identical or similar hazard warnings within a short window
 * - Repeated ambient scene descriptions
 * - Duplicate OCR reads (handled separately by OcrEngine)
 *
 * Uses a time-windowed cache with configurable expiry per priority level.
 */
class DeduplicationEngine {

    companion object {
        private const val TAG = "DeduplicationEngine"

        /** Hazard events can repeat every 2 seconds (urgent) */
        const val HAZARD_COOLDOWN_MS = 2_000L

        /** Navigation events can repeat every 8 seconds */
        const val NAVIGATION_COOLDOWN_MS = 8_000L

        /** Ambient events can repeat every 12 seconds */
        const val AMBIENT_COOLDOWN_MS = 12_000L

        /** Similarity threshold for fuzzy matching (prevents "car left" vs "car on left" duplicates) */
        const val SIMILARITY_THRESHOLD = 0.75f

        /** Maximum cache size */
        const val MAX_CACHE_SIZE = 50
    }

    /** Recent events cache: event text hash → timestamp */
    private val recentEvents = LinkedHashMap<String, Long>(MAX_CACHE_SIZE, 0.75f, true)

    /**
     * Checks if an event should be announced or filtered as a duplicate.
     *
     * @param event The narration event to check.
     * @return true if the event should be spoken, false if it's a duplicate.
     */
    fun shouldAnnounce(event: NarrationEvent): Boolean {
        val now = System.currentTimeMillis()
        val cooldown = getCooldown(event.priority)

        // Clean expired entries
        purgeExpired(now)

        // Check for exact match
        val normalizedText = event.text.lowercase().trim()
        val lastTime = recentEvents[normalizedText]
        if (lastTime != null && (now - lastTime) < cooldown) {
            Log.d(TAG, "Duplicate filtered: \"${event.text.take(40)}\" (${now - lastTime}ms ago)")
            return false
        }

        // Check for fuzzy match against recent events
        for ((cachedText, cachedTime) in recentEvents) {
            if ((now - cachedTime) < cooldown) {
                val similarity = computeTokenSimilarity(normalizedText, cachedText)
                if (similarity >= SIMILARITY_THRESHOLD) {
                    Log.d(TAG, "Fuzzy duplicate: \"${event.text.take(30)}\" ~ \"${cachedText.take(30)}\" (sim=$similarity)")
                    return false
                }
            }
        }

        // Record this event
        recentEvents[normalizedText] = now

        // Bound cache size
        if (recentEvents.size > MAX_CACHE_SIZE) {
            val oldest = recentEvents.entries.first()
            recentEvents.remove(oldest.key)
        }

        return true
    }

    /**
     * Gets the cooldown period for a given priority level.
     */
    private fun getCooldown(priority: Priority): Long = when (priority) {
        Priority.HAZARD -> HAZARD_COOLDOWN_MS
        Priority.NAVIGATION -> NAVIGATION_COOLDOWN_MS
        Priority.AMBIENT -> AMBIENT_COOLDOWN_MS
    }

    /**
     * Removes expired entries from the cache.
     */
    private fun purgeExpired(now: Long) {
        val maxCooldown = AMBIENT_COOLDOWN_MS // Longest cooldown
        recentEvents.entries.removeAll { (_, timestamp) ->
            now - timestamp > maxCooldown
        }
    }

    /**
     * Computes token-level Jaccard similarity between two strings.
     * More efficient than full Levenshtein for short narration strings.
     *
     * @return Similarity score between 0.0 and 1.0.
     */
    private fun computeTokenSimilarity(s1: String, s2: String): Float {
        val tokens1 = s1.split(" ", ",", ".").filter { it.isNotBlank() }.toSet()
        val tokens2 = s2.split(" ", ",", ".").filter { it.isNotBlank() }.toSet()

        if (tokens1.isEmpty() && tokens2.isEmpty()) return 1.0f
        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0.0f

        val intersection = tokens1.intersect(tokens2).size
        val union = tokens1.union(tokens2).size

        return intersection.toFloat() / union.toFloat()
    }

    /**
     * Clears all cached events. Useful when switching modes.
     */
    fun clear() {
        recentEvents.clear()
        Log.d(TAG, "Deduplication cache cleared")
    }
}
