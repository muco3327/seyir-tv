package tv.newtv.extractor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.newtv.data.models.Subtitle
import tv.newtv.data.models.VideoSource
import tv.newtv.utils.JsUnpacker

class VidmolyExtractor(private val client: OkHttpClient) : VideoExtractor {
    override val host = "vidmoly,vidmoxy"

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    override suspend fun extract(url: String): VideoSource? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Referer", url) // Anti-Bot: CDN bypass için kaynak site referans gösterilir
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7")
                .build()
                
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return@withContext null
            }
            
            val html = response.body?.string() ?: return@withContext null
            
            // 1. JsUnpacker ile şifreli (packed) Javascript kodunu çöz
            val unpackedHtml = JsUnpacker.unpack(html)
            
            // 2. Çözülen kodun içinden .m3u8 veya .mp4 yayın dosyasını bul
            val streamRegex = Regex("""file:\s*["'](https?://[^"']+\.(?:m3u8|mp4)[^"']*)["']""")
            val streamMatch = streamRegex.find(unpackedHtml) ?: streamRegex.find(html) // Çözülememişse ham HTML'de ara
            
            val streamUrl = streamMatch?.groupValues?.get(1) ?: return@withContext null
            
            // 3. (Opsiyonel) Harici Altyazı (VTT/SRT) dosyalarını bul
            val subtitleRegex = Regex("""file:\s*["'](https?://[^"']+\.(?:vtt|srt)[^"']*)["'](?:\s*,\s*label:\s*["']([^"']+)["'])?""")
            val subtitles = mutableListOf<Subtitle>()
            
            subtitleRegex.findAll(unpackedHtml).forEach { match ->
                val subUrl = match.groupValues[1]
                val label = if (match.groupValues.size > 2) match.groupValues[2] else "Türkçe"
                subtitles.add(Subtitle(label, subUrl))
            }
            
            // ExoPlayer için özel başlıkları (Header) hazırlama
            val headers = mapOf(
                "Referer" to url,
                "Origin" to "https://vidmoly.to"
            )
            
            val mimeType = if (streamUrl.contains(".m3u8", ignoreCase = true)) {
                androidx.media3.common.MimeTypes.APPLICATION_M3U8
            } else {
                androidx.media3.common.MimeTypes.VIDEO_MP4
            }

            VideoSource(
                url = streamUrl,
                quality = "Auto",
                headers = headers,
                subtitles = subtitles, // Harici Altyazıları StreamModele dahil ettik
                mimeType = mimeType
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
