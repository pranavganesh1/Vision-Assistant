package com.vinaycap.accessvision.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.vinaycap.accessvision.ml.FrameDetectionResult
import com.vinaycap.accessvision.ml.ObjectDetectorEngine
import com.vinaycap.accessvision.ml.OcrEngine
import com.vinaycap.accessvision.ml.OcrResult
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Analyzes camera frames through the ML pipeline.
 *
 * Manages:
 * - YUV to Bitmap conversion (off UI thread)
 * - Frame throttling to maintain 30–60ms budget
 * - Object detection via [ObjectDetectorEngine]
 * - Optional OCR via [OcrEngine] when OCR mode is active
 * - Skips frames if the previous analysis hasn't completed (backpressure)
 *
 * Callbacks deliver results asynchronously:
 * - [onDetectionResult]: Called for every analyzed frame with detection results
 * - [onOcrResult]: Called when OCR produces a new, non-duplicate text result
 *
 * Usage:
 *   val analyzer = FrameAnalyzer(detector, ocr) { detectionResult ->
 *       // handle detections
 *   }
 *   // Set OCR callback when needed:
 *   analyzer.onOcrResult = { ocrResult -> ... }
 *   analyzer.ocrEnabled = true
 */
class FrameAnalyzer(
    private val detector: ObjectDetectorEngine,
    private val ocrEngine: OcrEngine,
    var onDetectionResult: (FrameDetectionResult) -> Unit = {},
    var onOcrResult: (OcrResult) -> Unit = {}
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "FrameAnalyzer"

        /** Minimum interval between frame analyses in milliseconds */
        const val MIN_ANALYSIS_INTERVAL_MS = 33L // ~30fps cap

        /** Run OCR every N frames to reduce overhead */
        const val OCR_FRAME_INTERVAL = 5
    }

    /** Whether OCR is enabled (toggled by double-tap gesture) */
    @Volatile
    var ocrEnabled = false

    /** Whether the analyzer is actively processing */
    private val isProcessing = AtomicBoolean(false)

    /** Timestamp of the last analysis */
    private val lastAnalysisTimestamp = AtomicLong(0)

    /** Frame counter for OCR interval */
    private var frameCount = 0L

    /** Coroutine scope for async processing */
    private val analysisScope = CoroutineScope(
        Dispatchers.Default + SupervisorJob() + CoroutineName("FrameAnalysis")
    )

    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()

        // Frame throttling: skip if too soon or already processing
        if (currentTime - lastAnalysisTimestamp.get() < MIN_ANALYSIS_INTERVAL_MS ||
            !isProcessing.compareAndSet(false, true)
        ) {
            imageProxy.close()
            return
        }

        lastAnalysisTimestamp.set(currentTime)
        frameCount++

        // Convert YUV to Bitmap off the main thread
        val bitmap = try {
            imageProxyToBitmap(imageProxy)
        } catch (e: Exception) {
            Log.e(TAG, "YUV conversion failed: ${e.message}")
            imageProxy.close()
            isProcessing.set(false)
            return
        }

        // Close the image proxy immediately to free the camera buffer
        imageProxy.close()

        if (bitmap == null) {
            isProcessing.set(false)
            return
        }

        // Launch async analysis
        analysisScope.launch {
            try {
                // Run object detection
                val detectionResult = detector.detect(bitmap)
                onDetectionResult(detectionResult)

                // Run OCR if enabled and at the right interval
                if (ocrEnabled && frameCount % OCR_FRAME_INTERVAL == 0L) {
                    val ocrResult = ocrEngine.recognizeText(bitmap)
                    if (ocrResult != null &&
                        ocrEngine.isSignificant(ocrResult) &&
                        !ocrEngine.isDuplicate(ocrResult.text)
                    ) {
                        onOcrResult(ocrResult)
                    }
                }
            } catch (e: CancellationException) {
                throw e // Don't catch cancellation
            } catch (e: Exception) {
                Log.e(TAG, "Frame analysis error: ${e.message}", e)
            } finally {
                bitmap.recycle()
                isProcessing.set(false)
            }
        }
    }

    /**
     * Converts an ImageProxy (YUV_420_888) to a Bitmap.
     *
     * Uses Android's YuvImage and JPEG compression as a reliable conversion path.
     * While not the fastest method, it's compatible across all devices.
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        // NV21 format: Y followed by interleaved VU
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null
        )

        val outputStream = ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            Rect(0, 0, imageProxy.width, imageProxy.height),
            85, // JPEG quality - balance between speed and quality
            outputStream
        )

        val jpegBytes = outputStream.toByteArray()
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    }

    /**
     * Releases resources used by the analyzer.
     */
    fun shutdown() {
        analysisScope.cancel()
        Log.i(TAG, "FrameAnalyzer shut down")
    }
}
