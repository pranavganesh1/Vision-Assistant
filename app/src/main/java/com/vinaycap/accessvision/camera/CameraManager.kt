package com.vinaycap.accessvision.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

/**
 * Manages CameraX setup and lifecycle for the vision pipeline.
 *
 * Configures:
 * - Back camera with autofocus
 * - Preview stream bound to a PreviewView
 * - ImageAnalysis with a target resolution of 640x640
 * - Frame analysis via a pluggable [FrameAnalyzer]
 *
 * Usage:
 *   val cameraManager = CameraManager(context)
 *   cameraManager.startCamera(lifecycleOwner, previewView, frameAnalyzer)
 *   // ... later
 *   cameraManager.stopCamera()
 */
class CameraManager(private val context: Context) {

    companion object {
        private const val TAG = "CameraManager"
    }

    private var cameraProvider: ProcessCameraProvider? = null

    /**
     * Initializes and starts the camera with preview and frame analysis.
     *
     * @param lifecycleOwner Activity or Fragment lifecycle owner.
     * @param previewView The PreviewView composable host.
     * @param frameAnalyzer The frame analyzer for ML processing.
     */
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        frameAnalyzer: FrameAnalyzer
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                // Preview use case
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                // ImageAnalysis use case
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(640, 640))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                    .also {
                        it.setAnalyzer(
                            ContextCompat.getMainExecutor(context),
                            frameAnalyzer
                        )
                    }

                // Use back camera
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // Unbind all existing use cases before rebinding
                provider.unbindAll()

                // Bind use cases to lifecycle
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                Log.i(TAG, "Camera started successfully with back camera")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start camera: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Stops the camera and releases resources.
     */
    fun stopCamera() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        Log.i(TAG, "Camera stopped")
    }
}
