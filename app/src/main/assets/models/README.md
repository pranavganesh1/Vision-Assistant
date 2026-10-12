# Model Assets Directory

Place your quantized TFLite model files here.

## Required Model

**`yolov8n_int8.tflite`** — INT8 quantized YOLOv8n object detection model.

### How to obtain:

1. Use the Python scripts in `model_prep/` to convert and quantize a YOLOv8n model
2. Or download a pre-trained YOLOv8n and export it using Ultralytics:

```bash
pip install ultralytics
yolo export model=yolov8n.pt format=tflite int8=True imgsz=640
```

3. Copy the resulting `.tflite` file to this directory

## Model Specifications

| Property | Value |
|----------|-------|
| Input Shape | `[1, 640, 640, 3]` float32 |
| Output Shape | `[1, 84, 8400]` float32 |
| Quantization | INT8 (post-training) |
| Expected Size | ~4-6 MB |
| Target Latency | <30ms on Snapdragon 8 Gen 2 |
