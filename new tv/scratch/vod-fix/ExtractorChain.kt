package tv.newtv.extractor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import tv.newtv.data.models.VideoSource

class ExtractorChain(private val client: OkHttpClient) {

    // Video çözücü sunucular (Vidmoly, Pichive, Sibnet, Okru, Vidmixi, Vidload vb.)
    private val extractors = listOf(
        VidmolyExtractor(client),
        PichiveExtractor(client),
        SibnetExtractor(client),
        OkruExtractor(client),
        VidmixiExtractor(client),
        VidloadExtractor(client)
    )

    /**
     * Gelen iframe kaynaklarını sırayla dener. İlk başarılı çözülen
     * video kaynağını döndürür. (Hata Toleranslı Fallback Zinciri)
     */
    suspend fun resolveSource(iframeUrls: List<String>): VideoSource? = withContext(Dispatchers.IO) {
        val all = resolveAllSources(iframeUrls)
        all.firstOrNull()
    }

    /**
     * Tüm iframe ve sunucu kaynaklarını PARALEL çözümler, çalışan alternatif video kaynaklarını listeler.
     * - Paralel çözümleme (supervisorScope + async) ile hız kazanır
     * - Her iframe için maks 8 saniye timeout
     * - Aynı host'tan maks 2 kaynak (host bazlı deduplicate)
     * - Toplam maks 10 kaynak limiti
     */
    suspend fun resolveAllSources(iframeUrls: List<String>): List<VideoSource> = withContext(Dispatchers.IO) {
        val uniqueIframes = iframeUrls
            .filter { it.isNotBlank() }
            .map { if (it.startsWith("//")) "https:$it" else it }
            .distinct()

        // ÖNEMLİ: OkHttp maxRequestsPerHost=6 limitine takılıp timeout (8-12 sn) yememek için 
        // iframe'leri daha çözmeye başlamadan host bazında limitle.
        val hostCountBefore = mutableMapOf<String, Int>()
        val limitedIframes = uniqueIframes.filter { url ->
            val host = hostOf(url)
            val count = hostCountBefore.getOrDefault(host, 0)
            if (count < MAX_PER_HOST) {
                hostCountBefore[host] = count + 1
                true
            } else false
        }

        // Paralel çözümleme: her iframe URL'si bağımsız async ile çözülür
        val resolved = supervisorScope {
            val deferreds = limitedIframes.mapIndexed { index, url ->
                async(Dispatchers.IO) {
                    try {
                        withTimeout(12_000L) {
                            resolveIframeSource(url, index)
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("ExtractorChain", "Extractor failed for url: $url", e)
                        null
                    }
                }
            }
            deferreds.awaitAll().filterNotNull()
        }

        // URL bazlı deduplicate
        val seenUrls = mutableSetOf<String>()
        val deduplicated = resolved.filter { seenUrls.add(it.url) }

        // Host bazlı deduplicate: aynı host'tan maks 2 kaynak
        val hostCount = mutableMapOf<String, Int>()
        val hostLimited = deduplicated.filter { source ->
            val host = hostOf(source.url)
            val count = hostCount.getOrDefault(host, 0)
            if (count < MAX_PER_HOST) {
                hostCount[host] = count + 1
                true
            } else false
        }

        // Maks 10 kaynak limiti (öncelik: tanınan sunucular önce)
        val prioritized = hostLimited.sortedByDescending { source ->
            val host = hostOf(source.url)
            when {
                host.contains("vidmoly") -> 4
                host.contains("pichive") -> 3
                host.contains("vidmixi") -> 3
                host.contains("vidload") -> 2
                host.contains("sibnet") -> 2
                host.contains("okru") || host.contains("ok.ru") -> 2
                else -> 1
            }
        }

        prioritized.take(MAX_SOURCES)
    }

    /** Tek bir iframe URL'sini çözümler (paralel çağrı için) */
    private suspend fun resolveIframeSource(url: String, index: Int): VideoSource? {
        val extractor = extractors.find { it.canHandle(url) }
        val resolved: VideoSource? = if (extractor != null) {
            val src = extractor.extract(url) ?: extractFromEmbedPage(url)
            if (src != null) {
                val hostLabel = when {
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
