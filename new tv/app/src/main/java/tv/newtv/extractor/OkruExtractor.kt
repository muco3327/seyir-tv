package tv.newtv.extractor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import tv.newtv.data.models.VideoSource

class OkruExtractor(private val client: OkHttpClient) : VideoExtractor {
    override val host: String = "ok.ru"

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    override suspend fun extract(url: String): VideoSource? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Referer", "https://ok.ru/")
                .build()

            val response = client.newCall(req).execute()
            if (!response.isSuccessful) {
                response.close()
                return@withContext null
            }

            val html = response.body?.string() ?: ""
            response.close()

            val doc = Jsoup.parse(html)
            val dataOptions = doc.selectFirst("div[data-options]")?.attr("data-options") ?: ""
            if (dataOptions.isNotEmpty()) {
                val json = JSONObject(dataOptions)
                val flashvars = json.optJSONObject("flashvars")
                if (flashvars != null) {
                    val hlsManifestUrl = flashvars.optString("hlsManifestUrl")
                    if (hlsManifestUrl.isNotEmpty()) {
                        return@withContext VideoSource(
                            url = hlsManifestUrl,
                            quality = "Auto HLS",
                            mimeType = androidx.media3.common.MimeTypes.APPLICATION_M3U8,
                            serverName = "Okru HLS Sunucusu"
                        )
                    }

                    val metadataStr = flashvars.optString("metadata")
                    if (metadataStr.isNotEmpty()) {
                        val metaJson = JSONObject(metadataStr)
                        val videosArr = metaJson.optJSONArray("videos")
                        if (videosArr != null && videosArr.length() > 0) {
                            // En yüksek kaliteli videoyu seç (1080p > 720p > 480p)
                            var bestUrl = ""
                            var bestQuality = ""
                            for (i in 0 until videosArr.length()) {
                                val vObj = videosArr.optJSONObject(i) ?: continue
                                val vUrl = vObj.optString("url")
                                val vName = vObj.optString("name")
                                if (vUrl.isNotEmpty()) {
                                    bestUrl = vUrl
                                    bestQuality = vName
                                }
                            }
                            if (bestUrl.isNotEmpty()) {
                                return@withContext VideoSource(
                                    url = bestUrl,
                                    quality = bestQuality,
                                    mimeType = androidx.media3.common.MimeTypes.VIDEO_MP4,
                                    serverName = "Okru Sunucusu"
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }
}
