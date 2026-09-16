package tv.newtv

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import tv.newtv.network.OkHttpClientProvider
import tv.newtv.ui.navigation.TvAppNavigation

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Beklenmeyen çökmeleri engelleyip loglayan koruma kalkanı
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("NewTV", "Uncaught exception on thread ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Cihaz uykudan uyandığında veya oynatıcı açıkken ekranın kapanmasını engelle
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Android TV (Mi Box Gen 2 vb.) için kararlı, yüksek eşzamanlı ve şeffaflık/WebP destekli Coil yapılandırması
        val imageLoader = ImageLoader.Builder(this)
            .okHttpClient(OkHttpClientProvider.getImageOkHttpClient())
            .allowHardware(false) // TV GPU doku belleği aşımını ve siyah kutu çizim hatalarını önler
            .crossfade(false)     // TV cihazlarında çizim yükünü ve çift tamponlamayı önler
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.12) // Düşük RAM'li TV cihazları için dengeli afiş önbelleği
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(200L * 1024 * 1024) // 200MB afiş önbelleği
                    .build()
            }
            .build()
        Coil.setImageLoader(imageLoader)

        setContent {
            // Android TV Dark Theme Arka Planı (Netflix tarzı koyu gri/siyah)
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A0A0A))
            ) {
                // Ensure text is white globally
                androidx.compose.material3.ProvideTextStyle(
                    value = androidx.compose.ui.text.TextStyle(color = Color.White)
                ) {
                    TvAppNavigation()
                }
            }
        }
    }

    companion object {
        var onActivityKeyEvent: ((android.view.KeyEvent) -> Boolean)? = null
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (onActivityKeyEvent?.invoke(event) == true) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
