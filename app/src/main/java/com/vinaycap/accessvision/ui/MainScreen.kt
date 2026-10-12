package com.vinaycap.accessvision.ui

import android.util.Log
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.vinaycap.accessvision.camera.CameraManager
import com.vinaycap.accessvision.camera.FrameAnalyzer
import com.vinaycap.accessvision.gesture.GestureOverlay
import com.vinaycap.accessvision.gesture.ModeManager
import com.vinaycap.accessvision.gesture.TwoFingerGestureOverlay
import com.vinaycap.accessvision.ml.ObjectDetectorEngine
import com.vinaycap.accessvision.ml.OcrEngine
import com.vinaycap.accessvision.narration.*

/**
 * Main screen composable that assembles all components:
 * - Camera preview (background)
 * - Status overlay (mode indicator, debug info)
 * - Gesture overlay (transparent, full-screen touch handler)
 *
 * This screen manages the lifecycle of all ML and narration components.
 */
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val TAG = "MainScreen"

    // ─── State ─────────────────────────────────────────────────────
    var currentMode by remember { mutableStateOf(ModeManager.Mode.FULL_NAVIGATION) }
    var isMuted by remember { mutableStateOf(false) }
    var isOcrActive by remember { mutableStateOf(false) }
    var lastInferenceMs by remember { mutableLongStateOf(0L) }
    var detectionCount by remember { mutableIntStateOf(0) }
    var statusMessage by remember { mutableStateOf("Initializing...") }

    // ─── Component Initialization ──────────────────────────────────
    val ttsController = remember {
        TtsController(context) {
            // Utterance complete callback
        }
    }

    val hapticController = remember {
        HapticController(context)
    }

    val dispatcher = remember {
        PriorityDispatcher(ttsController, hapticController)
    }

    val modeManager = remember {
        ModeManager().apply {
            onModeChanged = { mode ->
                currentMode = mode
                dispatcher.clearDeduplication()
            }
        }
    }

    val detector = remember {
        try {
            ObjectDetectorEngine(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load detection model: ${e.message}", e)
            null
        }
    }

    val ocrEngine = remember {
        OcrEngine(context)
    }

    val frameAnalyzer = remember {
        if (detector != null) {
            FrameAnalyzer(
                detector = detector,
                ocrEngine = ocrEngine,
                onDetectionResult = { result ->
                    lastInferenceMs = result.inferenceTimeMs
                    detectionCount = result.detections.size

                    // Only process detections if detection is active in current mode
                    if (modeManager.isDetectionActive) {
                        dispatcher.onDetectionResult(result)
                    }
                },
                onOcrResult = { result ->
                    if (modeManager.isOcrActive) {
                        dispatcher.onOcrResult(result)
                    }
                }
            )
        } else null
    }

    val cameraManager = remember { CameraManager(context) }

    // ─── Lifecycle Management ──────────────────────────────────────
    DisposableEffect(lifecycleOwner) {
        dispatcher.start()

        onDispose {
            dispatcher.stop()
            frameAnalyzer?.shutdown()
            detector?.close()
            ocrEngine.close()
            ttsController.shutdown()
            cameraManager.stopCamera()
            Log.i(TAG, "All resources released")
        }
    }

    // Announce readiness after initialization
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1500) // Wait for TTS to initialize
        statusMessage = "Ready"
        ttsController.speak("AccessVision is ready. Tap the screen to hear your surroundings.")
    }

    // ─── UI Layout ─────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Layer 1: Camera Preview
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    previewView.implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                    previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewView.contentDescription = "Camera preview showing your surroundings"

                    if (frameAnalyzer != null) {
                        cameraManager.startCamera(lifecycleOwner, previewView, frameAnalyzer)
                    } else {
                        statusMessage = "Model not found. Place model in assets/models/"
                        ttsController.speak("Error: Detection model not found. Please install the model file.")
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Layer 2: Status Overlay (semi-transparent, top of screen)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics {
                    contentDescription = "Status: $currentMode mode. ${if (isMuted) "Audio muted." else ""}"
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = currentMode.displayName,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isMuted) "🔇 Muted" else "🔊 Active",
                    color = if (isMuted) Color(0xFFFFB74D) else Color(0xFF81C784),
                    fontSize = 12.sp
                )
                Text(
                    text = "${lastInferenceMs}ms | ${detectionCount} objects",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Text(
                    text = statusMessage,
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            }
        }

        // Layer 3: Bottom hint bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Tap: Scene • Double-tap: Text • Swipe: Mode",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }

        // Layer 4: Two-finger gesture detector
        TwoFingerGestureOverlay(
            onTwoFingerDoubleTap = {
                hapticController.play(HapticPattern.TOGGLE_CLICK)
                isMuted = ttsController.toggleMute()
                if (isMuted) {
                    // Use haptic-only feedback since audio is muted
                    hapticController.play(HapticPattern.ERROR_BUZZ)
                } else {
                    ttsController.speak("Audio unmuted")
                }
            }
        )

        // Layer 5: Gesture Overlay (transparent, on top of everything)
        GestureOverlay(
            onSingleTap = {
                hapticController.play(HapticPattern.CONFIRM_TAP)
                dispatcher.announceCurrentScene()
                Log.d(TAG, "Gesture: Single tap → Scene summary")
            },
            onDoubleTap = {
                isOcrActive = !isOcrActive
                frameAnalyzer?.ocrEnabled = isOcrActive
                hapticController.play(HapticPattern.TOGGLE_CLICK)

                if (isOcrActive) {
                    ttsController.speak("Text capture activated")
                    ocrEngine.clearHistory()
                } else {
                    ttsController.speak("Text capture deactivated")
                }
                Log.d(TAG, "Gesture: Double tap → OCR ${if (isOcrActive) "ON" else "OFF"}")
            },
            onSwipeRight = {
                val newMode = modeManager.nextMode()
                hapticController.play(HapticPattern.TOGGLE_CLICK)
                ttsController.speak(newMode.description)
                Log.d(TAG, "Gesture: Swipe right → ${newMode.displayName}")
            },
            onSwipeLeft = {
                val newMode = modeManager.previousMode()
                hapticController.play(HapticPattern.TOGGLE_CLICK)
                ttsController.speak(newMode.description)
                Log.d(TAG, "Gesture: Swipe left → ${newMode.displayName}")
            },
            onLongPress = {
                hapticController.play(HapticPattern.CONFIRM_TAP)
                // Provide detailed scene information
                dispatcher.announceCurrentScene()
                Log.d(TAG, "Gesture: Long press → Detailed scene")
            }
        )
    }
}
