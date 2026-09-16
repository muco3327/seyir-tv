package tv.newtv.extractor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import tv.newtv.data.models.VideoSource
import tv.newtv.utils.VodSourceUrls

class ExtractorChain(private val client: OkHttpClient) {

    // Video çözücü sunucular (Rapidrame, Setplay, Fastplay, Vidmoly, Pichive, Sibnet, Okru, Vidmixi, Vidload vb.)
    private val extractors = listOf(
        RapidrameExtractor(client),
        SetplayExtractor(client),
        VidmolyExtractor(client),
        PichiveExtractor(client),
        SibnetExtractor(client),
        OkruExtractor(client),
        VidmixiExtractor(client),
        VidloadExtractor(client)
    )

    /** Kaynakları sırayla dene; ilk sonuç bulunduğunda ağ işini durdur. */
    suspend fun resolveSource(iframeUrls: List<String>): VideoSource? =
        resolveAllSources(iframeUrls, maxSources = 1).firstOrNull()

    suspend fun resolveAllSources(
        iframeUrls: List<String>,
        maxSources: Int = MAX_SOURCES
    ): List<VideoSource> = withContext(Dispatchers.IO) {
        val resolved = mutableListOf<VideoSource>()
        val urls = VodSourceUrls.playable(iframeUrls)
        for ((index, url) in urls.withIndex()) {
            coroutineContext.ensureActive()
            val source = try {
                withTimeoutOrNull(45_000L) { resolveIframeSource(url, index) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("ExtractorChain", "Source resolution failed", error)
                null
            }
            coroutineContext.ensureActive()
            if (source != null && resolved.none { it.url == source.url }) {
                resolved.add(source)
                if (resolved.size >= maxSources.coerceAtLeast(1)) break
            }
        }
        resolved
    }

    /** Tek bir iframe URL'sini çözümler. */
    private suspend fun resolveIframeSource(url: String, index: Int): VideoSource? {
        val extractor = extractors.find { it.canHandle(url) }
        val resolved: VideoSource? = if (extractor != null) {
            val src = extractor.extract(url) ?: extractFromEmbedPage(url)
            if (src != null) {
                val hostLabel = when {
                    extractor.host.contains("rapidrame", ignoreCase = true) || extractor.host.contains("rplayer", ignoreCase = true) -> "Rapidrame"
                    extractor.host.contains("vidmoly", ignoreCase = true) -> "Vidmoly"
                    extractor.host.contains("pichive", ignoreCase = true) -> "Pichive"
                    extractor.host.contains("vidmixi", ignoreCase = true) -> "Vidmixi"
                    extractor.host.contains("vidload", ignoreCase = true) -> "Vidload"
                    else -> extractor.host.replaceFirstChar { it.uppercase() }
                }
                src.copy(serverName = "$hostLabel Sunucusu")
            } else null
        } else {
            val direct = extractDirect(url) ?: extractFromEmbedPage(url)
            if (direct != null) {
                direct.copy(serverName = "Doğrudan Akış ${index + 1}")
            } else null
        }

        return if (resolved != null && resolved.serverName.isBlank()) {
            resolved.copy(serverName = "Sunucu ${index + 1}")
        } else resolved
    }

    private fun hostOf(url: String): String = runCatching {
        java.net.URI(url).host?.lowercase() ?: ""
    }.getOrDefault("")

    private fun extractDirect(url: String): VideoSource? {
        val clean = url.trim()
        if (clean.contains(".m3u8", ignoreCase = true)) {
            return VideoSource(
                url = clean,
                mimeType = androidx.media3.common.MimeTypes.APPLICATION_M3U8,
                serverName = "HLS Akışı"
            )
        } else if (clean.contains(".mp4", ignoreCase = true)) {
            return VideoSource(
                url = clean,
                mimeType = androidx.media3.common.MimeTypes.VIDEO_MP4,
                serverName = "MP4 Akışı"
            )
        }
        return null
    }

    /** Bilinmeyen embed sağlayıcılarında HTML/JavaScript içindeki gerçek HLS/MP4 adresini bulur. */
    private fun extractFromEmbedPage(embedUrl: String): VideoSource? {
        val request = Request.Builder()
            .url(embedUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
            .header("Referer", embedUrl)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val html = response.body?.string().orEmpty()
            if (html.isBlank()) return null
            val normalized = html
                .replace("\\/", "/")
                .replace("\\u0026", "&")
                .replace("&amp;", "&")
            val document = Jsoup.parse(normalized, embedUrl)
            val candidates = linkedSetOf<String>()

            document.select("video[src], source[src]").forEach { element ->
                element.absUrl("src").ifBlank { element.attr("src") }.takeIf { it.isNotBlank() }?.let(candidates::add)
            }
            val mediaRegex = Regex("""https?://[^\s"'<>\\]+?(?:\.m3u8|\.mp4)(?:\?[^\s"'<>\\]*)?""", RegexOption.IGNORE_CASE)
            mediaRegex.findAll(normalized).forEach { candidates.add(it.value) }

            val headers = mapOf(
                "User-Agent" to USER_AGENT,
                "Referer" to embedUrl,
                "Origin" to originOf(embedUrl)
            ).filterValues { it.isNotBlank() }

            candidates.firstNotNullOfOrNull { candidate ->
                extractDirect(candidate)?.copy(headers = headers)
            }?.let { return it }

            // Bir katman daha iç içe iframe kullanan sağlayıcıları destekle; döngüye girme.
            document.select("iframe[src], iframe[data-src]").forEach { iframe ->
                val nested = iframe.absUrl("src").ifBlank {
                    iframe.absUrl("data-src").ifBlank { iframe.attr("src").ifBlank { iframe.attr("data-src") } }
                }
                extractDirect(nested)?.copy(headers = headers)?.let { return it }
            }
        }
        return null
    }

    private fun originOf(url: String): String = runCatching {
        val uri = java.net.URI(url)
        "${uri.scheme}://${uri.host}"
    }.getOrDefault("")

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        const val MAX_SOURCES = 10
        const val MAX_PER_HOST = 2
    }
}
