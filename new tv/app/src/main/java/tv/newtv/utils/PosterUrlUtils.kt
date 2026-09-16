package tv.newtv.utils

import org.jsoup.nodes.Element
import java.net.URI

object PosterUrlUtils {

    /**
     * Afiş URL'sini temizler, tam ve geçerli bir HTTP(S) adresine dönüştürür.
     * data:image placeholder'larını ve kırık proxy kalıplarını eler.
     */
    fun normalize(posterUrl: String, provider: String = "", isMovie: Boolean = true): String {
        var p = posterUrl.trim()
        if (p.isEmpty() || p.startsWith("data:image", ignoreCase = true)) {
            return ""
        }

        // Protokolsüz adresleri HTTPS ile tamamla
        if (p.startsWith("//")) {
            p = "https:$p"
        }

        // Nispi (relative) adresleri doğru sağlayıcı domaini ile birleştir
        if (p.startsWith("/")) {
            val provLower = provider.lowercase()
            val base = when {
                provLower.contains("filmmodu") -> "https://filmmodu.cc"
                provLower.contains("jetfilm") -> "https://jetfilmizle.top"
                provLower.contains("sezonluk") -> "https://sezonlukdizi.cc"
                provLower.contains("dizilla") -> "https://dizilla.now"
                provLower.contains("hdfilm") || provLower.contains("cehennem") -> "https://www.hdfilmcehennemi.nl"
                provLower.contains("fullhd") -> "https://fullhdfilmizlesene.co"
                provLower.contains("dizigom") -> "https://dizigomv1.com"
                provLower.contains("diziwatch") -> "https://diziwatch.ac"
                isMovie -> "https://www.hdfilmcehennemi.nl"
                else -> "https://sezonlukdizi.cc"
            }
            p = "$base$p"
        }

        // Google AMP proxy kalıplarını güvenilir gerçek CDN adresine dönüştür
        p = p.replace("https://images-macellan-online.cdn.ampproject.org/i/s/", "https://")
            .replace("http://images-macellan-online.cdn.ampproject.org/i/s/", "https://")

        return p
    }

    /**
     * Görsel CDN'leri için uygun ve geçerli Referer başlığını döndürür.
     * Alakasız veya sahte alan adları göndermez.
     */
    fun getReferer(posterUrl: String, provider: String = "", isMovie: Boolean = true): String {
        val provLower = provider.lowercase()
        return when {
            provLower.contains("filmmodu") -> "https://filmmodu.cc/"
            provLower.contains("jetfilm") -> "https://jetfilmizle.top/"
            provLower.contains("sezonluk") -> "https://sezonlukdizi.cc/"
            provLower.contains("dizilla") -> "https://dizilla.now/"
            provLower.contains("hdfilm") || provLower.contains("cehennem") -> "https://www.hdfilmcehennemi.nl/"
            provLower.contains("fullhd") -> "https://fullhdfilmizlesene.co/"
            provLower.contains("dizigom") -> "https://dizigomv1.com/"
            provLower.contains("diziwatch") -> "https://diziwatch.ac/"
            posterUrl.startsWith("http", ignoreCase = true) -> {
                try {
                    val uri = URI(posterUrl)
                    if (uri.host.isNullOrBlank()) "" else "${uri.scheme ?: "https"}://${uri.host}/"
                } catch (_: Exception) {
                    ""
                }
            }
            else -> ""
        }
    }

    /**
     * JSoup Element içerisinden lazy-load ve responsive öznitelikleri tarayarak
     * gerçek afiş adresini çeker (data:image şeffaf placeholder'ları atlar).
     */
    fun extractImgUrl(element: Element, baseUrl: String): String {
        val img = if (element.tagName().equals("img", ignoreCase = true)) element else element.selectFirst("img")

        // 1. img özniteliklerini öncelik sırasına göre kontrol et
        val directCandidates = if (img != null) {
            listOf(
                img.attr("data-src"),
                img.attr("data-srcset").substringBefore(" ").trim(),
                img.attr("srcset").substringBefore(" ").trim(),
                img.attr("data-original"),
                img.attr("data-lazy-src"),
                img.attr("src")
            )
        } else emptyList()

        // 2. picture source özniteliklerini kontrol et
        val pictureSource = element.selectFirst("picture source")
        val pictureCandidates = if (pictureSource != null) {
            listOf(
                pictureSource.attr("data-srcset").substringBefore(" ").trim(),
                pictureSource.attr("srcset").substringBefore(" ").trim()
            )
        } else emptyList()

        val allCandidates = directCandidates + pictureCandidates
        var found = allCandidates.firstOrNull { it.isNotBlank() && !it.startsWith("data:image", ignoreCase = true) } ?: ""

        if (found.startsWith("//")) {
            found = "https:$found"
        } else if (found.startsWith("/")) {
            found = if (baseUrl.endsWith("/")) "${baseUrl.removeSuffix("/")}$found" else "$baseUrl$found"
        }

        return found
    }
}
