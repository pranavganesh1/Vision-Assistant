package com.vinaycap.accessvision.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * On-device object detection engine using TensorFlow Lite.
 *
 * Supports INT8 quantized YOLOv8n / YOLO11n models exported to TFLite format.
 * Uses NNAPI/GPU delegate for hardware acceleration when available.
 *
 * Expected model input:  [1, 640, 640, 3] float32 (normalized 0–1)
 * Expected model output: [1, N, 6] where each row is [x1, y1, x2, y2, confidence, classId]
 *                    or: [1, 84, 8400] (YOLOv8 transposed format)
 *
 * Usage:
 *   val engine = ObjectDetectorEngine(context, "yolov8n_int8.tflite")
 *   val results = engine.detect(bitmap)
 *   engine.close()
 */
class ObjectDetectorEngine(
    context: Context,
    modelFileName: String = MODEL_FILENAME,
    private val confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD,
    private val inputSize: Int = MODEL_INPUT_SIZE,
    private val numClasses: Int = NUM_COCO_CLASSES,
    useGpu: Boolean = true
) : AutoCloseable {

    companion object {
        private const val TAG = "ObjectDetectorEngine"
        const val MODEL_FILENAME = "yolov8n_int8.tflite"
        const val MODEL_INPUT_SIZE = 640
        const val NUM_COCO_CLASSES = 80
        const val DEFAULT_CONFIDENCE_THRESHOLD = 0.45f
        private const val NMS_IOU_THRESHOLD = 0.5f
        private const val MAX_DETECTIONS = 25
        // YOLOv8 outputs [1, 84, 8400] -> 84 = 4 bbox + 80 classes, 8400 anchor points
        private const val YOLOV8_NUM_ANCHORS = 8400
        private const val YOLOV8_OUTPUT_FEATURES = 84 // 4 + NUM_CLASSES
    }

    private val interpreter: Interpreter
    private var gpuDelegate: GpuDelegate? = null
    private val inputBuffer: ByteBuffer

    init {
        val modelBuffer = loadModelFile(context, modelFileName)

        val options = Interpreter.Options().apply {
            setNumThreads(4)

            if (useGpu) {
                try {
                    gpuDelegate = GpuDelegate()
                    addDelegate(gpuDelegate)
                    Log.i(TAG, "GPU delegate enabled")
                } catch (e: Exception) {
                    Log.w(TAG, "GPU delegate unavailable, falling back to CPU: ${e.message}")
                    gpuDelegate = null
                }
            }
        }

        interpreter = Interpreter(modelBuffer, options)

        // Pre-allocate input buffer: [1, inputSize, inputSize, 3] float32
        val inputBytes = 1 * inputSize * inputSize * 3 * 4 // 4 bytes per float
        inputBuffer = ByteBuffer.allocateDirect(inputBytes).apply {
            order(ByteOrder.nativeOrder())
        }

        Log.i(TAG, "Model loaded: $modelFileName | Input: ${inputSize}x${inputSize}")
        logTensorInfo()
    }

    /**
     * Run object detection on a bitmap frame.
     *
     * @param bitmap Input frame (will be resized to inputSize x inputSize).
     * @return FrameDetectionResult containing all filtered detections with spatial info.
     */
    fun detect(bitmap: Bitmap): FrameDetectionResult {
        val startTime = System.currentTimeMillis()

        // Resize to model input size
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        // Fill input buffer with normalized pixel values
        preprocessBitmap(resized)

        // Prepare output buffer for YOLOv8 format: [1, 84, 8400]
        val outputArray = Array(1) { Array(YOLOV8_OUTPUT_FEATURES) { FloatArray(YOLOV8_NUM_ANCHORS) } }

        // Run inference
        interpreter.run(inputBuffer, outputArray)

        // Parse and filter detections
        val rawDetections = parseYoloV8Output(outputArray[0])
        val nmsDetections = applyNMS(rawDetections)

        val inferenceTime = System.currentTimeMillis() - startTime
        Log.d(TAG, "Inference: ${inferenceTime}ms | Detections: ${nmsDetections.size}")

        if (resized !== bitmap) {
            resized.recycle()
        }

        return FrameDetectionResult(
            detections = nmsDetections,
            inferenceTimeMs = inferenceTime
        )
    }

    /**
     * Preprocesses a bitmap into the input ByteBuffer.
     * Normalizes pixel values to [0, 1] range.
     */
    private fun preprocessBitmap(bitmap: Bitmap) {
        inputBuffer.rewind()
        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in pixels) {
            // Extract RGB and normalize to [0, 1]
            inputBuffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f) // R
            inputBuffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)  // G
            inputBuffer.putFloat((pixel and 0xFF) / 255.0f)          // B
        }
    }

    /**
     * Parses YOLOv8 output format [84, 8400].
     * Row 0–3: cx, cy, w, h (center format, normalized)
     * Row 4–83: class probabilities
     *
     * Converts to xyxy format and filters by confidence and allowed classes.
     */
    private fun parseYoloV8Output(output: Array<FloatArray>): List<DetectionResult> {
        val results = mutableListOf<DetectionResult>()

        for (i in 0 until YOLOV8_NUM_ANCHORS) {
            // Find best class
            var maxClassScore = 0f
            var maxClassId = -1
            for (c in 4 until YOLOV8_OUTPUT_FEATURES) {
                if (output[c][i] > maxClassScore) {
                    maxClassScore = output[c][i]
                    maxClassId = c - 4
                }
            }

            // Filter by confidence and allowed classes
            if (maxClassScore < confidenceThreshold) continue
            if (!DetectionClasses.isAllowed(maxClassId)) continue

            // Convert from center format to xyxy (normalized 0–1)
            val cx = output[0][i] / inputSize
            val cy = output[1][i] / inputSize
            val w = output[2][i] / inputSize
            val h = output[3][i] / inputSize

            val x1 = (cx - w / 2f).coerceIn(0f, 1f)
            val y1 = (cy - h / 2f).coerceIn(0f, 1f)
            val x2 = (cx + w / 2f).coerceIn(0f, 1f)
            val y2 = (cy + h / 2f).coerceIn(0f, 1f)

            val normalizedBox = RectF(x1, y1, x2, y2)
            val zone = SpatialMapper.classifyZone(normalizedBox)
            val proximity = SpatialMapper.classifyProximity(normalizedBox)

            results.add(
                DetectionResult(
                    classId = maxClassId,
                    label = DetectionClasses.getLabel(maxClassId),
                    confidence = maxClassScore,
                    boundingBox = normalizedBox,
                    zone = zone,
                    proximity = proximity
                )
            )
        }

        return results
    }

    /**
     * Applies Non-Maximum Suppression (NMS) to remove overlapping detections.
     */
    private fun applyNMS(detections: List<DetectionResult>): List<DetectionResult> {
        if (detections.isEmpty()) return emptyList()

        val sorted = detections.sortedByDescending { it.confidence }.toMutableList()
        val selected = mutableListOf<DetectionResult>()

        while (sorted.isNotEmpty() && selected.size < MAX_DETECTIONS) {
            val best = sorted.removeAt(0)
            selected.add(best)

            sorted.removeAll { other ->
                computeIoU(best.boundingBox, other.boundingBox) > NMS_IOU_THRESHOLD &&
                        best.classId == other.classId
            }
        }

        return selected
    }

    /**
     * Computes Intersection over Union between two bounding boxes.
     */
    private fun computeIoU(a: RectF, b: RectF): Float {
        val interLeft = maxOf(a.left, b.left)
        val interTop = maxOf(a.top, b.top)
        val interRight = minOf(a.right, b.right)
        val interBottom = minOf(a.bottom, b.bottom)

        val interArea = maxOf(0f, interRight - interLeft) * maxOf(0f, interBottom - interTop)
        val aArea = a.width() * a.height()
        val bArea = b.width() * b.height()
        val unionArea = aArea + bArea - interArea

        return if (unionArea > 0) interArea / unionArea else 0f
    }

    /**
     * Loads a TFLite model from the assets folder using memory-mapped file.
     */
    private fun loadModelFile(context: Context, filename: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd("models/$filename")
        val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            assetFileDescriptor.startOffset,
            assetFileDescriptor.declaredLength
        )
    }

    /**
     * Logs model input/output tensor information for debugging.
     */
    private fun logTensorInfo() {
        val inputTensor = interpreter.getInputTensor(0)
        val outputTensor = interpreter.getOutputTensor(0)
        Log.d(TAG, "Input tensor: ${inputTensor.shape().contentToString()} ${inputTensor.dataType()}")
        Log.d(TAG, "Output tensor: ${outputTensor.shape().contentToString()} ${outputTensor.dataType()}")
    }

    override fun close() {
        interpreter.close()
        gpuDelegate?.close()
        Log.i(TAG, "ObjectDetectorEngine resources released")
    }
}
