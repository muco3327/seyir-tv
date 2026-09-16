# ============================================================
# New TV - ProGuard/R8 Kuralları (Mi Box Gen 2 / Android TV)
# ============================================================

# ---- OkHttp ----
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ---- Jsoup ----
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# ---- Room Database ----
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.paging.**

# ---- Jetpack Compose ----
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# ---- Media3 / ExoPlayer ----
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ---- Coil ----
-keep class coil.** { *; }
-dontwarn coil.**

# ---- Kotlin Coroutines ----
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ---- Data Classes (Model sınıfları) ----
-keep class tv.newtv.data.models.** { *; }
-keep class tv.newtv.data.local.** { *; }

# ---- JSON / org.json ----
-keep class org.json.** { *; }

# ---- Genel R8 ----
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
