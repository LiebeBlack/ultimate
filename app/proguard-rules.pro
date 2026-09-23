# PayLens U — R8 full mode.

# ML Kit usa reflection interna; mantener modelos y carga de nativos.
-keep class com.google.mlkit.vision.text.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text* { *; }
-keep class com.google.mlkit.vision.common.** { *; }

# CameraX
-keep class androidx.camera.core.** { *; }
-keep class androidx.camera.camera2.** { *; }

# Mantener lineas para simbolizacion en release
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
