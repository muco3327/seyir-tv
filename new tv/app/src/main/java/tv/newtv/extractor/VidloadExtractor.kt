package tv.newtv.extractor

import androidx.media3.common.MimeTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import tv.newtv.data.models.Subtitle
import tv.newtv.data.models.VideoSource

class VidloadExtractor(private val client: OkHttpClient) : VideoExtractor {
    override val host = "hdload,vidload"

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    override suspend fun extract(url: String): VideoSource? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Referer", "https://jetfilmizle.top/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return@withContext null
            }

            val html = response.body?.string() ?: return@withContext null
            response.close()

            // 1. m3u8 veya mp4 dosyasını ayıkla
            val fileRegex = Regex("""(?:file|src)\s*:\s*["'](https?://[^"']+\.(?:m3u8|mp4)[^"']*)["']""")
            val match = fileRegex.find(html)
            val streamUrl = match?.groupValues?.get(1) ?: run {
                val directRegex = Regex("""(https?://[^\s"']+\.m3u8[^\s"']*)""")
                directRegex.find(html)?.groupValues?.get(1)
            } ?: return@withContext null

            // 2. Altyazıları ayıkla
            val subtitles = mutableListOf<Subtitle>()
            val subRegex = Regex("""subtitleTracks\s*=\s*(\[\{.*?\}\]);""", RegexOption.DOT_MATCHES_ALL)
            val subMatch = subRegex.find(html)
            if (subMatch != null) {
                try {
                    val arr = JSONArray(subMatch.groupValues[1])
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val file = obj.optString("file")
                        val label = obj.optString("label").ifEmpty { "Türkçe" }
                        if (file.isNotEmpty()) {
                            subtitles.add(Subtitle(language = label, url = file))
                        }
                    }
                } catch (e: Exception) { android.util.Log.d("VidloadExtractor", "subtitle parse failed", e) }
            }

            val headers = mapOf(
                "Referer" to "https://hdload.top/",
                "User-Agent" to userAgent
            )

            VideoSource(
                url = streamUrl,
                quality = "1080p",
                mimeType = if (streamUrl.contains(".m3u8")) MimeTypes.APPLICATION_M3U8 else MimeTypes.APPLICATION_MP4,
                headers = headers,
                subtitles = subtitles,
                serverName = "JetFilm VIP"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
