package tv.newtv.extractor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import tv.newtv.data.models.Subtitle
import tv.newtv.data.models.VideoSource

class PichiveExtractor(private val client: OkHttpClient) : VideoExtractor {
    override val host = "pichive"

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    override suspend fun extract(url: String): VideoSource? = withContext(Dispatchers.IO) {
        try {
            val formattedUrl = if (url.startsWith("//")) "https:$url" else url
            val parsedHttpUrl = formattedUrl.toHttpUrlOrNull() ?: return@withContext null
            val domain = "${parsedHttpUrl.scheme}://${parsedHttpUrl.host}"

            var html = ""
            var token: String? = null
            val referers = listOf("https://selcukflix.com/", "https://dizilla.now/")
            val tokenRegex = Regex("""window\.openPlayer\s*\(\s*['"]([^'"]+)['"]""")

            for (ref in referers) {
                val request = Request.Builder()
                    .url(formattedUrl)
                    .header("User-Agent", userAgent)
                    .header("Referer", ref)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7")
                    .build()

                try {
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        response.close()
                        val foundToken = tokenRegex.find(body)?.groupValues?.get(1)
                        if (!foundToken.isNullOrEmpty()) {
                            token = foundToken
                            html = body
                            break
                        }
                    } else {
                        response.close()
                    }
                } catch (_: Exception) {}
            }

            if (token == null) return@withContext null

            // Parse subtitles if present in HTML
            val subtitles = mutableListOf<Subtitle>()
            val subRegex = Regex("""\{"file"\s*:\s*"([^"]+)"\s*,\s*"label"\s*:\s*"([^"]+)"""")
            subRegex.findAll(html).forEach { match ->
                val rawFile = match.groupValues[1].replace("\\/", "/")
                val label = match.groupValues[2]
                subtitles.add(Subtitle(label, rawFile))
            }

            // Request source2.php
            val source2Url = "$domain/source2.php?v=$token"
            val apiRequest = Request.Builder()
                .url(source2Url)
                .header("User-Agent", userAgent)
                .header("Referer", formattedUrl)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("Accept-Language", "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7")
                .build()

            val apiResponse = client.newCall(apiRequest).execute()
            if (!apiResponse.isSuccessful) {
                apiResponse.close()
                return@withContext null
            }
            val apiJsonStr = apiResponse.body?.string() ?: return@withContext null

            val apiJson = JSONObject(apiJsonStr)
            val playlist = apiJson.optJSONArray("playlist") ?: return@withContext null
            if (playlist.length() == 0) return@withContext null

            val firstPlaylistItem = playlist.getJSONObject(0)
            val sources = firstPlaylistItem.optJSONArray("sources") ?: return@withContext null
            if (sources.length() == 0) return@withContext null

            val streamUrl = sources.getJSONObject(0).optString("file")
            if (streamUrl.isEmpty()) return@withContext null

            val headers = mapOf(
                "User-Agent" to userAgent,
                "Referer" to formattedUrl
            )

            VideoSource(
                url = streamUrl,
                quality = "1080P",
                headers = headers,
                subtitles = subtitles,
                mimeType = androidx.media3.common.MimeTypes.APPLICATION_M3U8
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
