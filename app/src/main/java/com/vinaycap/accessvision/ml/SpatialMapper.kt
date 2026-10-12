package com.vinaycap.accessvision.ml

import android.graphics.RectF

/**
 * Maps bounding boxes to spatial zones and proximity levels.
 *
 * Frame coordinate system:
 *   Left zone:   x ∈ [0.0, 0.33]
 *   Center zone: x ∈ [0.33, 0.66]
 *   Right zone:  x ∈ [0.66, 1.0]
 *
 * Proximity estimation via bounding box height ratio:
 *   Far:         height < 25% of frame
 *   Approaching: 25% – 50% of frame
 *   Hazard:      > 50% of frame
 */
object SpatialMapper {

    private const val LEFT_BOUNDARY = 0.33f
    private const val RIGHT_BOUNDARY = 0.66f

    private const val FAR_THRESHOLD = 0.25f
    private const val APPROACHING_THRESHOLD = 0.50f

    /**
     * Determines which spatial zone the center of a bounding box falls into.
     *
     * @param box Bounding box in normalized coordinates [0, 1].
     * @return The spatial zone (LEFT, CENTER, or RIGHT).
     */
    fun classifyZone(box: RectF): SpatialZone {
        val centerX = (box.left + box.right) / 2f
        return when {
            centerX < LEFT_BOUNDARY -> SpatialZone.LEFT
            centerX > RIGHT_BOUNDARY -> SpatialZone.RIGHT
            else -> SpatialZone.CENTER
        }
    }

    /**
     * Estimates proximity based on bounding box height relative to frame height.
     * Larger bounding boxes indicate closer objects.
     *
     * @param box Bounding box in normalized coordinates [0, 1].
     * @return The proximity classification (FAR, APPROACHING, or HAZARD).
     */
    fun classifyProximity(box: RectF): Proximity {
        val heightRatio = box.height()
        return when {
            heightRatio > APPROACHING_THRESHOLD -> Proximity.HAZARD
            heightRatio > FAR_THRESHOLD -> Proximity.APPROACHING
            else -> Proximity.FAR
        }
    }

    /**
     * Normalizes a bounding box from pixel coordinates to [0, 1] range.
     *
     * @param box Bounding box in pixel coordinates.
     * @param frameWidth Width of the frame in pixels.
     * @param frameHeight Height of the frame in pixels.
     * @return Normalized bounding box.
     */
    fun normalizeBox(box: RectF, frameWidth: Int, frameHeight: Int): RectF {
        return RectF(
            box.left / frameWidth,
            box.top / frameHeight,
            box.right / frameWidth,
            box.bottom / frameHeight
        )
    }
}
