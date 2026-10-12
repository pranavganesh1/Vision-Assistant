package com.vinaycap.accessvision.ml

import android.graphics.RectF

/**
 * Spatial zones for direction estimation.
 * Frame is divided into 3 vertical zones: Left (0–33%), Center (33–66%), Right (66–100%).
 */
enum class SpatialZone(val label: String) {
    LEFT("on your left"),
    CENTER("ahead"),
    RIGHT("on your right")
}

/**
 * Proximity classification based on bounding box height relative to frame height.
 */
enum class Proximity(val label: String, val isHazard: Boolean) {
    FAR("far", false),             // Box height < 25% of frame
    APPROACHING("approaching", false), // 25% – 50% of frame
    HAZARD("obstacle direct", true)    // > 50% of frame (~1.5m)
}

/**
 * Allowed detection classes restricted to navigation hazards.
 */
object DetectionClasses {
    // COCO class indices mapped to navigation-relevant labels
    val ALLOWED_CLASSES: Map<Int, String> = mapOf(
        0 to "person",
        1 to "bicycle",
        2 to "car",
        3 to "motorcycle",
        5 to "bus",
        11 to "stop sign",
        13 to "bench",
        16 to "dog",
        // Custom classes (if trained): stairs=80, door=81
        // For standard COCO, these won't fire; add them to custom model
        56 to "chair",
        58 to "potted plant"
    )

    fun isAllowed(classId: Int): Boolean = ALLOWED_CLASSES.containsKey(classId)

    fun getLabel(classId: Int): String = ALLOWED_CLASSES[classId] ?: "unknown"
}

/**
 * A single detected object with spatial and proximity information.
 */
data class DetectionResult(
    val classId: Int,
    val label: String,
    val confidence: Float,
    val boundingBox: RectF,
    val zone: SpatialZone,
    val proximity: Proximity,
    val timestampMs: Long = System.currentTimeMillis()
) {
    /**
     * Human-readable narration string for this detection.
     * Examples: "Warning: person obstacle direct ahead"
     *           "car approaching on your left"
     */
    fun toNarration(): String {
        val prefix = if (proximity.isHazard) "Warning: " else ""
        return "$prefix$label ${proximity.label} ${zone.label}"
    }
}

/**
 * Container for a complete frame analysis result.
 */
data class FrameDetectionResult(
    val detections: List<DetectionResult>,
    val inferenceTimeMs: Long,
    val frameTimestampMs: Long = System.currentTimeMillis()
) {
    /** Returns only hazard-level detections for priority interrupt */
    fun hazards(): List<DetectionResult> =
        detections.filter { it.proximity.isHazard }

    /** Returns non-hazard detections sorted by proximity */
    fun ambient(): List<DetectionResult> =
        detections.filter { !it.proximity.isHazard }
            .sortedBy { it.proximity.ordinal }

    /** Generates a scene summary for ambient narration */
    fun sceneSummary(): String {
        if (detections.isEmpty()) return "Path appears clear"
        return detections.joinToString(", ") { "${it.label} ${it.zone.label}" }
    }
}

/**
 * OCR result with deduplication support.
 */
data class OcrResult(
    val text: String,
    val confidence: Float,
    val textAreaRatio: Float, // Fraction of frame occupied by text
    val timestampMs: Long = System.currentTimeMillis()
)
