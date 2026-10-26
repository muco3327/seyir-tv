# Seyir TV ProGuard / R8 Optimization Rules

# 1. Keep Seyir TV Application Code completely intact
-keep class tv.seyir.app.** { *; }
-keepclassmembers class tv.seyir.app.** { *; }

# 2. Keep AndroidX Media3 (ExoPlayer)
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.ui.** { *; }
-keep class androidx.media3.datasource.** { *; }
-keep interface androidx.media3.** { *; }
-dontwarn androidx.media3.**

# 3. Keep JSON
-keep class org.json.** { *; }
-keepclassmembers class * {
    *** parseJson(...);
}

# 4. Standard Attributes
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# 5. Suppress warnings from unused transitive dependencies
-dontwarn kotlin.**
-dontwarn kotlinx.**
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.**
-dontwarn javax.annotation.**
