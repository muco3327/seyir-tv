package tv.newtv.utils

/** Source-independent allowlist. Unrecognised names must never enter the live catalog. */
object LiveChannelPolicy {
    data class Channel(val key: String, val name: String, val category: String)
    private val aliases = buildMap<String, Channel> {
        fun add(name: String, sports: Boolean = false, vararg alternatives: String) {
            val channel = Channel(normalize(name), name, if (sports) "Spor Kanalları" else "Ulusal Kanallar")
            (listOf(name) + alternatives).forEach { put(normalize(it), channel) }
        }
        for (number in 1..5) add("beIN Sports $number", true, "beinsport$number", "bein$number")
        for (number in 1..2) {
            add("beIN Sports Max $number", true, "bein max $number")
            add("S Sport $number", true, "ssport$number")
            add("Eurosport $number", true)
            add("Smart Spor $number", true, "spor smart $number")
        }
        add("beIN Sports Haber", true, "bein haber")
        for (number in 1..4) add("Tivibu Spor $number", true)
        for (number in 1..8) add("Exxen Spor $number", true, "exxen $number")
        add("Exxen", true)
        add("TRT Spor", true, "trt spor hd")
        add("TRT Spor Yıldız", true, "trt spor yildiz")
        add("A Spor", true)
        add("HT Spor", true)
        add("Sports TV", true, "sportstv")
        add("TJK TV", true, "tjk")
        add("FB TV", true, "fbtv")
        add("GS TV", true, "gstv")
        add("Tabii Spor", true)
        for (number in 1..6) add("Tabii Spor $number", true)
        add("TRT 1")
        add("TRT 2")
        add("ATV")
        add("Kanal D", false, "kanald")
        add("Show TV", false, "show")
        add("Star TV", false, "star")
        add("NOW", false, "now tv", "fox", "fox tv")
        add("TV8")
        add("TV8.5", false, "tv8 5", "tv85")
        add("Kanal 7", false, "kanal7")
        add("Beyaz TV", false, "beyaz")
        add("Teve2", false, "teve 2")
        add("360", false, "tv360", "360 tv")
        add("TLC")
        add("DMAX")
        add("TRT Haber")
        add("CNN Türk", false, "cnnturk")
        add("Habertürk", false, "haber turk", "haberturk tv")
        add("NTV")
        add("A Haber", false, "ahaber")
        add("Halk TV", false, "halk")
        add("Sözcü TV", false, "sozcu", "szc", "szc tv")
        add("Tele1", false, "tele 1")
        add("TGRT Haber")
        add("24 TV", false, "24", "kanal 24")
        add("Ülke TV", false, "ulke")
        add("TV100")
        add("TVNET")
        add("Bloomberg HT", false, "bloomberght")
        add("EKOL TV", false, "ekol")
        add("Haber Global")
        add("TRT World")
        add("TRT 4K", false, "trt4k", "trt 4k uhd")
        add("beIN Sports 4K", true, "bein 4k", "beinsports 4k")
        add("Bengü Türk", false, "benguturk", "bengu turk tv")
    }

    private fun extractTagsAndQuality(rawName: String): Pair<List<String>, String> {
        val lower = rawName.lowercase(java.util.Locale.ROOT)
        val tags = mutableListOf<String>()

        if (lower.contains("zeus")) tags.add("Zeus")
        if (lower.contains("atom")) tags.add("Atom")
        if (lower.contains("mahsun")) tags.add("Mahsun")
        if (lower.contains("cdn")) tags.add("CDN")
        if (lower.contains("tv247") || lower.contains("-247")) tags.add("TV247")
        if (lower.contains("forestgump")) tags.add("Forestgump")
        if (lower.contains("ace")) tags.add("Ace")
        if (lower.contains("talip") || (lower.contains("tal") && lower.contains("-tal"))) tags.add("Tal")
        if (lower.contains("tulix") || (lower.contains("tul") && lower.contains("-tul"))) tags.add("Tulix")
        if (lower.contains("vodafone") || lower.contains("turkcell")) tags.add("Vodafone/Turkcell")
        if (lower.contains("dusuk") || lower.contains("düşük")) tags.add("Düşük Kalite")
        if (lower.contains("yedek") || lower.contains("backup")) tags.add("Yedek")
        if (lower.contains("mac zamani") || lower.contains("maç zamanı")) tags.add("Maç Zamanı")
        if (lower.contains("50fps") || lower.contains("50 fps")) tags.add("50FPS")

        val qual = when {
            lower.contains("4k") || lower.contains("2160") -> "4K"
            lower.contains("uhd") -> "UHD"
            lower.contains("fhd") || lower.contains("1080") -> "FHD"
            lower.contains("hevc") || lower.contains("h265") || lower.contains("h.265") -> "HEVC"
            lower.contains("hd") || lower.contains("720") -> "HD"
            lower.contains("sd") -> "SD"
            else -> ""
        }
        return Pair(tags, qual)
    }

    private fun formatName(base: String, tags: List<String>, qual: String): String {
        val qualPart = if (qual.isNotEmpty() && !base.contains(qual, ignoreCase = true) && !tags.any { it.contains(qual, ignoreCase = true) }) " $qual" else ""
        val tagPart = if (tags.isNotEmpty()) " (${tags.joinToString(", ")})" else ""
        return "$base$qualPart$tagPart".trim()
    }

    private fun normalize(name: String): String = M3uParser.getCanonicalKey(name)
        .replace(" ", "")

    fun resolve(name: String, group: String? = null): Channel? {
        if (name.isBlank()) return null
        val rawLower = name.trim().lowercase(java.util.Locale.ROOT)
        val grpLower = group?.trim()?.lowercase(java.util.Locale.ROOT) ?: ""

        // Yabancı ülkeleri / yayınları filtrele
        val foreignTerms = listOf(
            "france", "arabic", "australia", "malaysia", "usa", "espanol", "español", "mena",
            "-fr", "-qa", "-au", "-my", "-us", "qatar", "russia", "germany", "italia", "spain", "poland"
        )
        val foreignGroups = setOf("world", "fr", "qa", "my", "au", "us", "ar", "de", "ru", "es", "it", "ca", "nl")
        if (grpLower.isNotEmpty() && grpLower != "tr" && foreignGroups.contains(grpLower)) return null
        if (foreignTerms.any { rawLower.contains(it) || grpLower.contains(it) }) return null

        val cleaned = name.replace(Regex("""^(?:(?:TR|TURK|TURKEY)\s*[:|\-]\s*|\[(?:TR|TURK)\]\s*|\|(?:TR|TURK)\|\s*)""", RegexOption.IGNORE_CASE), "").trim()
        val cleanedLower = cleaned.lowercase(java.util.Locale.ROOT)
        val (tags, qual) = extractTagsAndQuality(name)

        // 1. beIN Sports Haber
        if ((cleanedLower.contains("bein") || cleanedLower.contains("bnsport")) && cleanedLower.contains("haber")) {
            val base = "beIN Sports Haber"
            return Channel("beinsportshaber", formatName(base, tags, qual), "Spor Kanalları")
        }

        // 2. beIN Sports Max 1..2
        val matchMax = Regex("""(?:bein|bnsport).*?max\s*([1-2])""", RegexOption.IGNORE_CASE).find(cleanedLower)
        if (matchMax != null) {
            val num = matchMax.groupValues[1]
            val base = "beIN Sports Max $num"
            return Channel("beinsportsmax$num", formatName(base, tags, qual), "Spor Kanalları")
        }

        // 3. beIN Connect 1..6
        val matchConn = Regex("""(?:bein|bnsport).*?connect\s*([1-6])""", RegexOption.IGNORE_CASE).find(cleanedLower)
        if (matchConn != null) {
            val num = matchConn.groupValues[1]
            val base = "beIN Connect $num"
            return Channel("beinconnect$num", formatName(base, tags, qual), "Spor Kanalları")
        }

        // 4. beIN Sports 4K (Özel Maç Kanalı)
        val isBein4k = (cleanedLower.contains("bein") || cleanedLower.contains("bnsport")) &&
                (cleanedLower.contains("4k") || cleanedLower.contains("2160")) &&
                !Regex("""(?:sports?|spor|s)\s*[1-5]\b""", RegexOption.IGNORE_CASE).containsMatchIn(cleanedLower)
        if (isBein4k) {
            val base = "beIN Sports 4K"
            return Channel("beinsports4k", formatName(base, tags, "4K"), "Spor Kanalları")
        }

        // 5. beIN Sports 1..5
        val isBeinNonSport = listOf("box office", "series", "movies", "sinema", "gurme", "iz").any { cleanedLower.contains(it) }
        if (!isBeinNonSport) {
            val matchBein = Regex("""(?:(?:tr)?bein|bnsport).*?(?:sports?|spors?|s)?\s*([1-5])\b""", RegexOption.IGNORE_CASE).find(cleanedLower)
                ?: Regex("""(?:(?:tr)?bein|bnsport).*?([1-5])(?:[^0-9]|$)""", RegexOption.IGNORE_CASE).find(cleanedLower)
            if (matchBein != null) {
                val num = matchBein.groupValues[1]
                val base = "beIN Sports $num"
                return Channel("beinsports$num", formatName(base, tags, qual), "Spor Kanalları")
            }
        }

        // 5. S Sport 1..2
        val matchSsport = Regex("""(?:s\s*sport|ssport)\s*([1-2])?""", RegexOption.IGNORE_CASE).find(cleanedLower)
        if (matchSsport != null) {
            val num = matchSsport.groupValues[1].ifEmpty { "1" }
            val base = "S Sport $num"
            return Channel("ssport$num", formatName(base, tags, qual), "Spor Kanalları")
        }

        // 6. Tivibu Spor 1..4
        val matchTivibu = Regex("""tivibu\s*spor\s*([1-4])?""", RegexOption.IGNORE_CASE).find(cleanedLower)
        if (matchTivibu != null) {
            val num = matchTivibu.groupValues[1].ifEmpty { "1" }
            val base = "Tivibu Spor $num"
            return Channel("tivibuspor$num", formatName(base, tags, qual), "Spor Kanalları")
        }

        // 7. Exxen Spor 1..8
        val matchExxen = Regex("""exxen\s*(?:spor)?\s*([1-8])?""", RegexOption.IGNORE_CASE).find(cleanedLower)
        if (matchExxen != null && (cleanedLower.contains("spor") || grpLower.contains("spor"))) {
            val num = matchExxen.groupValues[1].ifEmpty { "1" }
            val base = "Exxen Spor $num"
            return Channel("exxenspor$num", formatName(base, tags, qual), "Spor Kanalları")
        }

        // 8. Smart Spor 1..2
        val matchSmart = Regex("""(?:smart\s*spor|spor\s*smart)\s*([1-2])?""", RegexOption.IGNORE_CASE).find(cleanedLower)
        if (matchSmart != null) {
            val num = matchSmart.groupValues[1].ifEmpty { "1" }
            val base = "Smart Spor $num"
            return Channel("smartspor$num", formatName(base, tags, qual), "Spor Kanalları")
        }

        // 9. Eurosport 1..2
        val matchEuro = Regex("""eurosport\s*([1-2])?""", RegexOption.IGNORE_CASE).find(cleanedLower)
        if (matchEuro != null) {
            val num = matchEuro.groupValues[1].ifEmpty { "1" }
            val base = "Eurosport $num"
            return Channel("eurosport$num", formatName(base, tags, qual), "Spor Kanalları")
        }

        // 10. Tabii Spor 1..6
        val matchTabii = Regex("""tabii\s*spor\s*([1-6])?""", RegexOption.IGNORE_CASE).find(cleanedLower)
        if (matchTabii != null) {
            val num = matchTabii.groupValues[1].ifEmpty { "1" }
            val base = "Tabii Spor $num"
            return Channel("tabiispor$num", formatName(base, tags, qual), "Spor Kanalları")
        }

        // 11. Diğer Türk Spor Kanalları
        if (cleanedLower.contains("trt spor yildiz") || cleanedLower.contains("trt spor yıldız")) {
            return Channel("trtsporyildiz", formatName("TRT Spor Yıldız", tags, qual), "Spor Kanalları")
        }
        if (cleanedLower.contains("trt spor")) {
            return Channel("trtspor", formatName("TRT Spor", tags, qual), "Spor Kanalları")
        }
        if (cleanedLower.contains("a spor") || cleanedLower == "aspor") {
            return Channel("aspor", formatName("A Spor", tags, qual), "Spor Kanalları")
        }
        if (cleanedLower.contains("ht spor") || cleanedLower == "htspor") {
            return Channel("htspor", formatName("HT Spor", tags, qual), "Spor Kanalları")
        }
        if (cleanedLower.contains("sports tv") || cleanedLower.contains("sportstv")) {
            return Channel("sportstv", formatName("Sports TV", tags, qual), "Spor Kanalları")
        }
        if (cleanedLower.contains("tjk")) {
            return Channel("tjktv", formatName("TJK TV", tags, qual), "Spor Kanalları")
        }
        if (cleanedLower.contains("fb tv") || cleanedLower == "fbtv") {
            return Channel("fbtv", formatName("FB TV", tags, qual), "Spor Kanalları")
        }
        if (cleanedLower.contains("gs tv") || cleanedLower == "gstv") {
            return Channel("gstv", formatName("GS TV", tags, qual), "Spor Kanalları")
        }

        // 12. Standart Ulusal Kanallar Allowlist
        val normalized = normalize(name)
        aliases[normalized]?.let { matched ->
            val finalName = if (qual.isNotEmpty() && !matched.name.contains(qual, ignoreCase = true)) {
                "${matched.name} $qual"
            } else {
                matched.name
            }
            return matched.copy(name = finalName)
        }

        return null
    }

    fun isSports(name: String, group: String? = null): Boolean {
        if (group?.contains("Spor", ignoreCase = true) == true) return true
        val resolved = resolve(name, group)
        if (resolved?.category == "Spor Kanalları") return true
        val lower = name.lowercase(java.util.Locale.ROOT)
        return lower.contains("spor") || lower.contains("sport") || lower.contains("bein") ||
                lower.contains("exxen") || lower.contains("tivibu") || lower.contains("eurosport") ||
                lower.contains("smart spor") || lower.contains("ssport")
    }
}

