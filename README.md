# 🦯 AccessVision — Offline Real-Time Accessibility Vision Assistant

A production-grade, **100% offline**, on-device assistive Android application for blind and visually impaired users. Provides real-time obstacle detection, sign reading, and spatial audio feedback using on-device ML.

## 🏗️ Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                      Gesture Overlay                         │
│  Tap: Scene │ Double-tap: OCR │ Swipe: Mode │ 2F-Tap: Mute │
├──────────────────────────────────────────────────────────────┤
│                     Priority Dispatcher                      │
│  P1: Hazard (interrupt) → P2: Navigation (queue) → P3: Ambient│
├───────────┬──────────────┬───────────────────────────────────┤
│    TTS    │    Haptic    │        Deduplication              │
│ Controller│   Controller │          Engine                   │
├───────────┴──────────────┴───────────────────────────────────┤
│              ML Inference Pipeline                            │
│  ┌──────────────┐  ┌───────────┐  ┌────────────────────┐    │
│  │ YOLOv8n INT8 │  │ Spatial   │  │ ML Kit Offline OCR │    │
│  │ TFLite+GPU   │  │ Mapper    │  │ Text Recognition   │    │
│  └──────────────┘  └───────────┘  └────────────────────┘    │
├──────────────────────────────────────────────────────────────┤
│                    Camera Pipeline                            │
│  CameraX → YUV_420_888 → Bitmap → Frame Analyzer            │
└──────────────────────────────────────────────────────────────┘
```

## ✨ Features

### 🎯 Object Detection
- **YOLOv8n INT8** quantized model via TensorFlow Lite
- GPU/NNAPI acceleration for <30ms inference
- Filtered to navigation hazards: person, bicycle, car, motorcycle, bus, stop sign, bench, dog, chair, potted plant
- **Spatial zones**: Left, Center, Right (frame divided into thirds)
- **Proximity estimation**: Far (<25%), Approaching (25-50%), Hazard (>50%)

### 📖 Offline OCR
- ML Kit bundled text recognition (zero network calls)
- Text area significance detection (>15% of frame)
- Levenshtein distance deduplication prevents repeated reads
- Continuous scanning and snap-and-read modes

### 🔊 Smart Narration
- **Priority 1 (Hazard)**: Immediate haptic pulse + TTS interrupt — "Warning: car obstacle direct ahead"
- **Priority 2 (Navigation)**: Queued TTS — "Sign reads: Restroom to your right"
- **Priority 3 (Ambient)**: Periodic summary every 6 seconds — "Person on left, bench ahead"
- Adjustable speech rate (1.0x – 1.75x)
- Token-level deduplication with priority-specific cooldowns

### 🤚 Screenless Gesture Interface
| Gesture | Action |
|---------|--------|
| Single Tap | Speak current scene summary |
| Double Tap | Toggle OCR text capture |
| Two-Finger Double Tap | Mute/Unmute audio |
| Swipe Right | Next mode |
| Swipe Left | Previous mode |
| Long Press | Detailed scene readout |

### 🔄 Operational Modes
1. **Full Navigation** — Detection + OCR + Ambient narration
2. **Text Reader** — OCR-only mode for reading signs
3. **Hazard Warning** — Detection-only, immediate threats only

## 📁 Project Structure

```
VinayCAP/
├── app/
│   ├── build.gradle.kts              # Dependencies & build config
│   ├── proguard-rules.pro            # R8 shrinking rules
│   └── src/main/
│       ├── AndroidManifest.xml        # Permissions, offline enforcement
│       ├── assets/models/             # ← Drop .tflite models here
│       │   └── README.md
│       ├── java/com/vinaycap/accessvision/
│       │   ├── AccessVisionApp.kt     # Application singleton
│       │   ├── MainActivity.kt        # Entry point + permissions
│       │   ├── camera/
│       │   │   ├── CameraManager.kt   # CameraX lifecycle management
│       │   │   └── FrameAnalyzer.kt   # YUV→Bitmap + ML pipeline
│       │   ├── ml/
│       │   │   ├── DetectionResult.kt  # Data models & COCO classes
│       │   │   ├── ObjectDetectorEngine.kt # TFLite YOLOv8 inference
│       │   │   ├── OcrEngine.kt        # ML Kit offline OCR + dedup
│       │   │   └── SpatialMapper.kt    # Zone & proximity classification
│       │   ├── narration/
│       │   │   ├── NarrationEvent.kt   # Event + Priority + Haptic models
│       │   │   ├── PriorityDispatcher.kt # Priority queue + dispatch loop
│       │   │   ├── TtsController.kt    # Android TTS wrapper
│       │   │   ├── HapticController.kt # Vibration patterns
│       │   │   └── DeduplicationEngine.kt # Token similarity filtering
│       │   ├── gesture/
│       │   │   ├── GestureOverlay.kt   # Compose gesture handlers
│       │   │   └── ModeManager.kt      # Operational mode cycling
│       │   └── ui/
│       │       ├── theme/Theme.kt      # Material3 dark theme
│       │       └── MainScreen.kt       # Main composable assembly
│       └── res/values/
│           ├── strings.xml             # All string resources
│           └── themes.xml              # App theme
├── build.gradle.kts                    # Root build file
├── settings.gradle.kts                 # Project settings
├── gradle.properties                   # Build properties
├── gradle/libs.versions.toml           # Version catalog
├── model_prep/                         # Python model preparation
│   ├── requirements.txt
│   ├── convert_model.py                # YOLOv8 → TFLite export
│   └── quantize_model.py              # Quantization verification
└── README.md                          # This file
```

## 🚀 Setup Instructions

### Prerequisites
- Android Studio Ladybug (2024.2+) or later
- JDK 17+
- Android SDK 35
- Python 3.10+ (for model preparation)

### 1. Clone & Open
```bash
git clone <repo-url>
cd VinayCAP
# Open in Android Studio
```

### 2. Prepare the Model (Python venv)

```bash
cd model_prep

# Create and activate virtual environment
python -m venv venv
venv\Scripts\activate          # Windows
# source venv/bin/activate     # Linux/Mac

# Install dependencies
pip install -r requirements.txt

# Convert YOLOv8n to INT8 TFLite
python convert_model.py --model yolov8n.pt --int8 --imgsz 640

# Model will be copied to app/src/main/assets/models/yolov8n_int8.tflite
```

### 3. Build & Run
```bash
# In Android Studio: Build → Make Project
# Or via command line:
./gradlew assembleDebug

# Install on device:
./gradlew installDebug
```

### 4. First Launch
1. Grant camera permission when prompted
2. Point camera at surroundings
3. Tap screen to hear current scene
4. Swipe left/right to change modes

## ⚡ Performance Targets

| Metric | Target | Method |
|--------|--------|--------|
| Detection latency | <30ms | INT8 quantization + NNAPI |
| Frame processing | 30fps | KEEP_ONLY_LATEST backpressure |
| Memory usage | <200MB | Model memory mapping |
| Battery drain | <10%/hr | Frame skipping + throttling |
| Model size | <6MB | INT8 quantization |

## 🔒 Privacy & Offline Guarantee

- **INTERNET permission explicitly removed** in AndroidManifest
- Zero telemetry, analytics, or cloud dependencies
- All ML inference runs entirely on-device
- Camera frames never leave the device
- Firebase/Google Analytics collection disabled

## 📋 Accessibility Compliance

- Full TalkBack screen reader compatibility
- Semantic content descriptions on all interactive elements
- Haptic feedback patterns for every gesture
- Screen-reader-friendly mode announcements
- High-contrast status overlay
- Portrait-locked for consistent spatial mapping

## 📄 License

This project is for assistive technology purposes. See LICENSE file for details.
