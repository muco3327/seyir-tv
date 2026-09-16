package tv.newtv.extractor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.newtv.data.models.VideoSource

class PlayableMovieSource(private val client: OkHttpClient) {
    private val chain = ExtractorChain(client)

    suspend fun resolve(urls: List<String>): VideoSource? = withContext(Dispatchers.IO) {
        for (url in urls) {
            val source = chain.resolveSource(listOf(url)) ?: continue
            if (source.url.contains(".m3u8", ignoreCase = true) || source.url.contains(".mp4", ignoreCase = true)) {
                return@withContext source
            }
            try {
                val request = Request.Builder().url(source.url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                source.headers.forEach { (key, value) -> request.header(key, value) }
                client.newCall(request.build()).execute().use { response ->
                    if (response.isSuccessful || response.code == 416 || response.code == 206) {
                        return@withContext source
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (source.url.startsWith("http")) return@withContext source
            }
        }
        null
    }
}
