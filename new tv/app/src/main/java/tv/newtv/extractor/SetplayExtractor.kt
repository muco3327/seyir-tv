package tv.newtv.extractor

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.newtv.data.models.Subtitle
import tv.newtv.data.models.VideoSource
import java.util.regex.Pattern

class SetplayExtractor(private val client: OkHttpClient) : VideoExtractor {
    override val host = "setplay,fastplay"

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    override suspend fun extract(url: String): VideoSource? = withContext(Dispatchers.IO) {
        try {
            var targetVideoUrl = url
            // 1. Eğer SetPlay sayfası ise SPG.cerceve şifresini çözerek FastPlay video adresine ulaş
            if (url.contains("setplay", ignoreCase = true)) {
                val setplayReq = Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .header("Referer", "https://www.hdfilmcehennemi.nl/")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .build()

                client.newCall(setplayReq).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val html = response.body?.string().orEmpty()
                    val resolved = decryptFastplayUrlFromSetplay(html)
                    if (!resolved.isNullOrBlank()) {
                        targetVideoUrl = resolved
                    }
                }
            }

            if (!targetVideoUrl.contains("fastplay", ignoreCase = true) && !targetVideoUrl.contains("video", ignoreCase = true)) {
                return@withContext null
            }

            // 2. FastPlay video sayfasını yükle
            val fastplayReq = Request.Builder()
                .url(targetVideoUrl)
                .header("User-Agent", userAgent)
                .header("Referer", "https://setplay.shop/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            val fastplayHtml = client.newCall(fastplayReq).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.string().orEmpty()
            }

            // 3. FastPlay SPG_A ve stream manifest bilgilerini ayrıştır
            val spMatch = Regex(""""sp"\s*:\s*"([^"]+)"""").find(fastplayHtml)
            val sptMatch = Regex(""""spT"\s*:\s*(\d+)""").find(fastplayHtml)
            val streamMatch = Regex("""stream\s*:\s*"([^"]+)"""").find(fastplayHtml)

            if (spMatch != null && sptMatch != null && streamMatch != null) {
                val sp = spMatch.groupValues[1]
                val spT = sptMatch.groupValues[1].toLongOrNull() ?: (System.currentTimeMillis() / 1000)
                val streamPath = streamMatch.groupValues[1]

                // Fastplay oturumunu Interceptor için kaydet
                tv.newtv.network.FastplayTokenProvider.setSession(sp, spT, targetVideoUrl)

                val streamUrl = if (streamPath.startsWith("http")) {
                    streamPath
                } else {
                    "https://fastplay.mom${if (streamPath.startsWith("/")) "" else "/"}$streamPath"
                }

                // Subtitle tespiti (tracks JSON veya regex)
                val subtitles = mutableListOf<Subtitle>()
                val tracksMatch = Regex("""tracks\s*:\s*(\[[^\]]+\])""").find(fastplayHtml)
                if (tracksMatch != null) {
                    try {
                        val tracksArray = org.json.JSONArray(tracksMatch.groupValues[1])
                        for (i in 0 until tracksArray.length()) {
                            val trackObj = tracksArray.getJSONObject(i)
                            val kind = trackObj.optString("kind")
                            val file = trackObj.optString("file")
                            val label = trackObj.optString("label")
                            if (kind != "thumbnails" && file.isNotEmpty()) {
                                val subLabel = if (label.isNotBlank()) label else "Altyazı ${i + 1}"
                                subtitles.add(Subtitle(subLabel, file))
                            }
                        }
                    } catch (_: Exception) {}
                }
                if (subtitles.isEmpty()) {
                    val subRegex = Regex("""file:\s*["'](https?://[^"']+\.(?:vtt|srt)[^"']*)["'](?:\s*,\s*label:\s*["']([^"']+)["'])?""")
                    subRegex.findAll(fastplayHtml).forEach { match ->
                        val subUrl = match.groupValues[1]
                        if (!subUrl.contains("thumbnail", ignoreCase = true)) {
                            val label = if (match.groupValues.size > 2 && match.groupValues[2].isNotBlank()) match.groupValues[2] else "Türkçe"
                            subtitles.add(Subtitle(label, subUrl))
                        }
                    }
                }

                val headers = mapOf(
                    "User-Agent" to userAgent,
                    "Referer" to targetVideoUrl,
                    "Origin" to "https://fastplay.mom"
                )

                return@withContext VideoSource(
                    url = streamUrl,
                    quality = "Auto",
                    headers = headers,
                    subtitles = subtitles,
                    mimeType = androidx.media3.common.MimeTypes.APPLICATION_M3U8
                )
            }

            // Fallback: Doğrudan m3u8 veya mp4 bağlantısı varsa
            val directStreamMatch = Regex("""file:\s*["'](https?://[^"']+\.(?:m3u8|mp4)[^"']*)["']""").find(fastplayHtml)
            if (directStreamMatch != null) {
                val directUrl = directStreamMatch.groupValues[1]
                val headers = mapOf(
                    "User-Agent" to userAgent,
                    "Referer" to targetVideoUrl,
                    "Origin" to "https://fastplay.mom"
                )
                return@withContext VideoSource(
                    url = directUrl,
                    quality = "Auto",
                    headers = headers,
                    mimeType = if (directUrl.contains(".m3u8", ignoreCase = true)) {
                        androidx.media3.common.MimeTypes.APPLICATION_M3U8
                    } else {
                        androidx.media3.common.MimeTypes.VIDEO_MP4
                    }
                )
            }

            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * SetPlay sayfasındaki SPG.cerceve("b2", "...", "...") şifresini XOR ile çözer
     */
    private fun decryptFastplayUrlFromSetplay(html: String): String? {
        try {
            val cerceveRegex = Regex("""SPG\.cerceve\s*\(\s*["'][^"']+["']\s*,\s*["']([^"']+)["']\s*,\s*["']([^"']+)["']\s*\)""")
            val match = cerceveRegex.find(html) ?: return null
            val t = match.groupValues[1]
            val n = match.groupValues[2]

            val r = Base64.decode(t, Base64.DEFAULT)
            val o = Base64.decode(n, Base64.DEFAULT)
            if (r.isEmpty() || o.isEmpty()) return null

            val sb = StringBuilder()
            for (i in r.indices) {
                val b = (r[i].toInt() xor o[i % o.size].toInt()).toChar()
                sb.append(b)
            }
            val parts = sb.toString().split("|")
            return parts.firstOrNull { it.startsWith("http", ignoreCase = true) }
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * FastPlay FNV-1a tabanlı X-Sp doğrulama token'ı üretimi
     */
    private fun generateXSp(sp: String, spT: Long): String {
        val n = spT
        val randVal = (Math.random() * 2176782336.0).toLong()
        val r = java.lang.Long.toString(randVal, 36)
        val combined = "$sp|$n|$r"
        var t = 2166136261L
        for (ch in combined) {
            t = t xor ch.code.toLong()
            t = (t * 16777619L) and 0xFFFFFFFFL
        }
        val hashHex = java.lang.Long.toHexString(t)
        return "$n.$r.$hashHex"
    }
}
