package tv.newtv.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import tv.newtv.data.local.ChannelEntity
import tv.newtv.data.local.ChannelStatus
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object M3uParser {

    /**
     * Kanal isminde kaynakta ne yazıyorsa (örn: "bein sport 1 hd", "bein sport sd") onu korur.
     * Sadece başta gereksiz bulunan "TR:" veya "[TR]" gibi ülke etiketlerini temizler.
     */
    fun cleanChannelName(rawName: String): String {
        var clean = rawName.trim()
        clean = clean.replace(Regex("""^(?:(?:TR|TURK|TURKEY)\s*[:|\-]\s*|\[(?:TR|TURK)\]\s*|\|(?:TR|TURK)\|\s*)""", RegexOption.IGNORE_CASE), "")
        clean = clean.replace(Regex("""\s+"""), " ").trim()
        return if (clean.isNotEmpty()) clean else rawName.trim()
    }

    /**
     * StreamScope mantığıyla kanalları birleştirmek için standart normalize edilmiş anahtar üretir.
     * Çözünürlük etiketleri (HD, FHD, 4K, 1080p), FPS bilgileri ve ülke ön ekleri temizlenir.
     * Örn: "[TR] beIN Sports 1 FHD (50fps)" -> "bein sports 1"
     */
    fun getCanonicalKey(rawName: String): String {
        var text = cleanChannelName(rawName).lowercase()

        // Türkçe karakterleri normalize et
        text = text.replace('ı', 'i')
            .replace('ğ', 'g')
            .replace('ü', 'u')
            .replace('ş', 's')
            .replace('ö', 'o')
            .replace('ç', 'c')

        // Parantez içindeki kalite / yedek / fps bilgilerini temizle: (50fps), [fhd], (yedek)
        text = text.replace(Regex("""\([^\d\)]*(?:fps|fhd|hd|sd|uhd|4k|raw|yedek|backup|vip)[^\)]*\)""", RegexOption.IGNORE_CASE), " ")
        text = text.replace(Regex("""\[[^\d\]]*(?:fps|fhd|hd|sd|uhd|4k|raw|yedek|backup|vip)[^\]]*\]""", RegexOption.IGNORE_CASE), " ")

        // Kalite ve teknik terimleri temizle
        val junkWords = listOf(
            "1080p", "720p", "4k", "fhd", "uhd", "1080i", "576i", "50fps", "60fps", "100fps",
            "hevc", "h265", "h264", "h.265", "h.264", "raw", "vip", "yedek", "backup", "donmasiz",
            "hq", "hd", "sd", "zeus"
        )
        for (w in junkWords) {
            text = text.replace(Regex("""\b${Regex.escape(w)}\b""", RegexOption.IGNORE_CASE), " ")
        }

        // Spor tekil/çoğul eşitleme (sport -> sports)
        text = text.replace(Regex("""\bspor\b"""), "sports")
        text = text.replace(Regex("""\bsport\b"""), "sports")

        // Özel karakterleri boşluğa dönüştür, sadece alfanümerik kalsın
        text = text.replace(Regex("""[^a-z0-9]"""), " ")
        text = text.replace(Regex("""\s+"""), " ").trim()

        // Spor kanallarında 1 numarasız halleri (S Sport -> S Sport 1, beIN Sports -> beIN Sports 1 vb.) eşitle
        if (text == "s sports") {
            text = "s sports 1"
        } else if (text == "bein sports") {
            text = "bein sports 1"
        } else if (text == "tivibu sports") {
            text = "tivibu sports 1"
        } else if (text == "smart sports") {
            text = "smart sports 1"
        } else if (text == "exxen sports") {
            text = "exxen sports 1"
        }

        return if (text.isNotEmpty()) text else rawName.trim().lowercase()
    }

    private fun extractQuality(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.contains("4k") || lower.contains("2160") || lower.contains("uhd") -> "4K"
            lower.contains("fhd") || lower.contains("1080") -> "FHD"
            lower.contains("hevc") || lower.contains("h.265") || lower.contains("h265") -> "HEVC"
            lower.contains("hd") || lower.contains("720") -> "HD"
            else -> ""
        }
    }

    /**
     * Yanıltıcı / sahte veya yanlış yönlendirilmiş akışları tespit eder.
     * Örneğin bir spor kanalı altında Show TV veya alakasız yayın linki verilmişse reddeder.
     */
    fun isBogusStream(name: String, streamUrl: String): Boolean {
        val urlLower = streamUrl.lowercase()
        val nameLower = name.lowercase()

        val isSportsName = nameLower.contains("s sport") || nameLower.contains("ssport") ||
                nameLower.contains("bein") || nameLower.contains("tivibu") ||
                nameLower.contains("exxen") || nameLower.contains("smart spor") ||
                nameLower.contains("eurosport")

        if (isSportsName) {
            // S Sport veya diğer spor kanalları altında Show TV veya Brightcove Ciner akışı
            if (urlLower.contains("showtv") || urlLower.contains("show_tv")) return true
            if (urlLower.contains("6415845530001")) return true
        }
        return false
    }

    /**
     * Sadece "Spor Kanalları" ve "Ulusal Kanallar"ı kabul eder.
     * Belgesel, Sinema, Dizi, Çocuk, Müzik, Yabancı vb. kanallar null döner ve elenir.
     */
    fun determineCategory(name: String, groupTitle: String?): String? =
        LiveChannelPolicy.resolve(name, groupTitle)?.category
    /**
     * Büyük M3U dosyalarında OOM (Out of Memory) oluşmasını engellemek için
     * InputStream üzerinden satır satır okuma yapar ve yakaladığı her kanalı Flow olarak yayar (emit).
     */
    fun parse(inputStream: InputStream, sourceUrl: String): Flow<ChannelEntity> = flow {
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            var line: String?
            var currentName = ""
            var currentLogo: String? = null
            var currentGroup: String? = null
            var currentUa: String? = null
            var currentReferer: String? = null
            var currentOrigin: String? = null

            // Regex desenleri
            val logoRegex = Regex("""tvg-logo="([^"]+)"""")
            val groupRegex = Regex("""group-title="([^"]+)"""")

            while (reader.readLine().also { line = it } != null) {
                currentCoroutineContext().ensureActive()
                val currentLine = line?.trim() ?: continue

                if (currentLine.startsWith("#EXTINF:")) {
                    currentUa = null
                    currentReferer = null
                    currentOrigin = null

                    // Logoyu çıkar
                    val logoMatch = logoRegex.find(currentLine)
                    currentLogo = logoMatch?.groupValues?.get(1)

                    // Kategoriyi çıkar
                    val groupMatch = groupRegex.find(currentLine)
                    currentGroup = groupMatch?.groupValues?.get(1)

                    // Kanal adını çıkar (virgülden sonraki kısmın tamamı)
                    val lastComma = currentLine.lastIndexOf(',')
                    currentName = if (lastComma != -1 && lastComma < currentLine.length - 1) {
                        currentLine.substring(lastComma + 1).trim()
                    } else {
                        val tvgNameMatch = Regex("""tvg-name="([^"]+)"""").find(currentLine)
                        tvgNameMatch?.groupValues?.get(1)?.trim() ?: "Bilinmeyen Kanal"
                    }
                    if (currentName.startsWith("group-title", ignoreCase = true) || currentName.startsWith("tvg-", ignoreCase = true)) {
                        val tvgNameMatch = Regex("""tvg-name="([^"]+)"""").find(currentLine)
                        currentName = tvgNameMatch?.groupValues?.get(1)?.trim() ?: currentName
                    }
                } else if (currentLine.startsWith("#EXTVLCOPT:")) {
                    val opt = currentLine.substringAfter("#EXTVLCOPT:").trim()
                    if (opt.startsWith("http-user-agent=", ignoreCase = true)) {
                        currentUa = opt.substringAfter("http-user-agent=").trim()
                    } else if (opt.startsWith("http-referrer=", ignoreCase = true)) {
                        currentReferer = opt.substringAfter("http-referrer=").trim()
                    } else if (opt.startsWith("http-origin=", ignoreCase = true)) {
                        currentOrigin = opt.substringAfter("http-origin=").trim()
                    }
                } else if (currentLine.startsWith("#KODIPROP:inputstream.adaptive.stream_headers=")) {
                    val headersStr = currentLine.substringAfter("#KODIPROP:inputstream.adaptive.stream_headers=").trim()
                    val parts = headersStr.split("&")
                    for (part in parts) {
                        if (part.startsWith("User-Agent=", ignoreCase = true)) {
                            currentUa = part.substringAfter("User-Agent=").trim()
                        } else if (part.startsWith("Referer=", ignoreCase = true)) {
                            currentReferer = part.substringAfter("Referer=").trim()
                        } else if (part.startsWith("Origin=", ignoreCase = true)) {
                            currentOrigin = part.substringAfter("Origin=").trim()
                        }
                    }
                } else if (!currentLine.startsWith("#") && currentLine.isNotEmpty()) {
                    // Yorum veya başlık değilse, ve boş değilse akış linkidir
                    var streamUrl = currentLine

                    // StreamScope / VLC başlıklarını pipe sözdizimi ile URL'e ekle (Örn: url|Referer=...&User-Agent=...)
                    if (!streamUrl.contains("|")) {
                        val headerList = mutableListOf<String>()
                        currentUa?.let { headerList.add("User-Agent=$it") }
                        currentReferer?.let { headerList.add("Referer=$it") }
                        currentOrigin?.let { headerList.add("Origin=$it") }
                        if (headerList.isNotEmpty()) {
                            streamUrl = "$streamUrl|${headerList.joinToString("&")}"
                        }
                    }

                    // Xtream Codes canlı yayınlarında .m3u dosyaları arızalı HLS (404) döndürürken,
                    // doğrudan .ts canlı MPEG-TS akışı %100 çalışmaktadır.
                    var primaryStreamUrl = streamUrl
                    val backupList = mutableListOf<String>()
                    val baseNoHeaders = if (streamUrl.contains("|")) streamUrl.substringBefore("|") else streamUrl
                    val headersPart = if (streamUrl.contains("|")) "|" + streamUrl.substringAfter("|") else ""
                    if (streamUrl.contains("/live/", ignoreCase = true) && baseNoHeaders.endsWith(".m3u", ignoreCase = true)) {
                        val tsUrl = baseNoHeaders.substring(0, baseNoHeaders.length - 4) + ".ts" + headersPart
                        val m3u8Url = baseNoHeaders.substring(0, baseNoHeaders.length - 4) + ".m3u8" + headersPart
                        primaryStreamUrl = tsUrl
                        backupList.add(tsUrl)
                        backupList.add(m3u8Url)
                        backupList.add(streamUrl)
                    } else {
                        backupList.add(streamUrl)
                    }

                    if (currentName.isNotEmpty() && primaryStreamUrl.isNotEmpty() && !isBogusStream(currentName, primaryStreamUrl)) {
                        val accepted = LiveChannelPolicy.resolve(currentName, currentGroup)
                        if (accepted != null) {
                            emit(
                                ChannelEntity(
                                    name = currentName,
                                    logoUrl = currentLogo,
                                    groupTitle = accepted.category,
                                    streamUrl = primaryStreamUrl,
                                    sourceListUrl = sourceUrl,
                                    canonicalKey = accepted.key,
                                    streamUrlsJson = org.json.JSONArray(backupList.distinct()).toString(),
                                    sourceCount = 1,
                                    isFavorite = false,
                                    status = ChannelStatus.UNKNOWN
                                )
                            )
                        }
                    }

                    // Bir sonraki kanal için değişkenleri sıfırla
                    currentName = ""
                    currentLogo = null
                    currentGroup = null
                    currentUa = null
                    currentReferer = null
                    currentOrigin = null
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}
