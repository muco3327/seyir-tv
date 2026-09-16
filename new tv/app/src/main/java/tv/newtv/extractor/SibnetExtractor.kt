package tv.newtv.extractor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.newtv.data.models.VideoSource

class SibnetExtractor(private val client: OkHttpClient) : VideoExtractor {
    override val host: String = "sibnet.ru"

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    override suspend fun extract(url: String): VideoSource? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Referer", "https://video.sibnet.ru/")
                .build()

            val response = client.newCall(req).execute()
            if (!response.isSuccessful) {
                response.close()
                return@withContext null
            }

            val html = response.body?.string() ?: ""
            response.close()

            // sibnet mp4 linki regex: src: "/v/...mp4" veya '/v/...mp4'
            val regex = Regex("""src\s*:\s*["'](/v/[^"']+\.mp4)""")
            val match = regex.find(html) ?: Regex("""["'](/v/[a-zA-Z0-9]+/[0-9]+\.mp4)""").find(html)

            if (match != null) {
                val path = match.groupValues[1]
                val videoUrl = "https://video.sibnet.ru$path"
                val headers = mapOf(
                    "User-Agent" to userAgent,
                    "Referer" to "https://video.sibnet.ru/"
                )
                return@withContext VideoSource(
                    url = videoUrl,
                    quality = "1080p / 720p",
                    headers = headers,
                    mimeType = androidx.media3.common.MimeTypes.VIDEO_MP4,
                    serverName = "Sibnet Sunucusu"
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }
}
