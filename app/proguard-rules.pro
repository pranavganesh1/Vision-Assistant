# Add project specific ProGuard rules here.

# TensorFlow Lite
-keep class org.tensorflow.** { *; }
-dontwarn org.tensorflow.**

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Keep model classes
-keep class com.vinaycap.accessvision.ml.** { *; }
-keep class com.vinaycap.accessvision.narration.** { *; }
