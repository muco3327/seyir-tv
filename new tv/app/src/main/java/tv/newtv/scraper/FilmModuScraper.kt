package tv.newtv.scraper

import okio.ByteString.Companion.decodeBase64
import tv.newtv.utils.VodSourceUrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tv.newtv.data.models.MovieDetail
import tv.newtv.data.models.MovieItem
import tv.newtv.data.models.VodCategory
import java.net.URLEncoder

class FilmModuScraper(private val client: OkHttpClient) : MovieScraper {
    override val name: String = "FilmModu"
    private val baseUrl = "https://filmmodu.cc"
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private fun fetchDocument(url: String): Document? {
        return try {
            val formattedUrl = if (url.startsWith("http")) url else if (url.startsWith("/")) "$baseUrl$url" else "$baseUrl/$url"
            val request = Request.Builder()
                .url(formattedUrl)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Referer", baseUrl)
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                return null
            }
            val html = response.body?.string() ?: ""
            response.close()
            Jsoup.parse(html, formattedUrl)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseMovieItem(element: Element, seen: MutableSet<String>): MovieItem? {
        val linkElem = element.selectFirst("a.image, a[href*='/film/']") ?: return null
        val href = linkElem.attr("href")
        if (href.isEmpty() || href == "#") return null

        val fullUrl = if (href.startsWith("http")) href else "$baseUrl$href"
        if (!seen.add(fullUrl)) return null

        val title = element.selectFirst("h2, .title h2, .title")?.text()?.trim()
            ?: linkElem.attr("title").replace(" izle", "").trim()
        if (title.length < 2) return null

        var poster = tv.newtv.utils.PosterUrlUtils.extractImgUrl(element, baseUrl)
        poster = tv.newtv.utils.PosterUrlUtils.normalize(poster, name, true)

        val year = element.selectFirst(".year, .box.year")?.text()?.trim() ?: ""

        val langList = mutableListOf<String>()
        val typeText = element.selectFirst(".type, .box.type")?.text()?.lowercase() ?: ""
        val typeTitle = element.selectFirst(".type, .box.type")?.attr("title")?.lowercase() ?: ""
        if (typeText.contains("dual") || typeTitle.contains("dublaj & altyazı") || typeText.contains("dublaj - altyazı")) {
            langList.add("TR Dublaj")
            langList.add("TR Altyazı")
        } else if (typeText.contains("dublaj") || typeTitle.contains("dublaj")) {
            langList.add("TR Dublaj")
        } else if (typeText.contains("altyazı") || typeTitle.contains("altyazı")) {
            langList.add("TR Altyazı")
        } else {
            langList.add("TR Dublaj")
            langList.add("TR Altyazı")
        }

        return MovieItem(
            title = title,
            url = fullUrl,
            posterUrl = poster,
            year = year,
            provider = name,
            languages = langList
        )
    }

    override suspend fun getRecent(page: Int): List<MovieItem> = withContext(Dispatchers.IO) {
        val doc = fetchDocument(baseUrl) ?: return@withContext emptyList()
        val seen = mutableSetOf<String>()
        val items = mutableListOf<MovieItem>()

        val cards = doc.select(".movie_box, .editor_selection .own-carousel__item, article.movie_box")
        for (card in cards) {
            parseMovieItem(card, seen)?.let { items.add(it) }
        }
        items
    }

    override suspend fun getCategories(page: Int): List<VodCategory<MovieItem>> = withContext(Dispatchers.IO) {
        val catDefs = listOf(
            "Son Eklenen Filmler" to listOf(
                "$baseUrl/yeni-filmler/1",
                "$baseUrl/yeni-filmler/2",
                "$baseUrl/yeni-filmler/3"
            ),
            "Trend Filmler" to listOf(
                "$baseUrl/trend-filmler",
                "$baseUrl/trend-filmler/2"
            ),
            "Popüler Filmler" to listOf(
                "$baseUrl/populer-filmler",
                "$baseUrl/populer-filmler/2"
            ),
            "IMDb Puanı Yüksek" to listOf(
                "$baseUrl/filmizle/imdb-puani-yuksek-filmler",
                "$baseUrl/filmizle/imdb-puani-yuksek-filmler/2"
            ),
            "Aksiyon & Macera" to listOf(
                "$baseUrl/filmizle/aksiyon-filmleri-izle",
                "$baseUrl/filmizle/aksiyon-filmleri-izle/2",
                "$baseUrl/filmizle/macera-filmleri-izle"
            ),
            "Bilim Kurgu & Fantastik" to listOf(
                "$baseUrl/filmizle/bilim-kurgu-filmleri-izle",
                "$baseUrl/filmizle/bilim-kurgu-filmleri-izle/2",
                "$baseUrl/filmizle/fantastik-filmler-izle"
            ),
            "Komedi Filmleri" to listOf(
                "$baseUrl/filmizle/komedi-filmleri-hd-izle",
                "$baseUrl/filmizle/komedi-filmleri-hd-izle/2"
            ),
            "Korku & Gerilim" to listOf(
                "$baseUrl/filmizle/korku-filmleri-izle",
                "$baseUrl/filmizle/korku-filmleri-izle/2",
                "$baseUrl/filmizle/gerilim-filmleri-hd-izle"
            ),
            "Animasyon & Aile" to listOf(
                "$baseUrl/filmizle/animasyon-filmleri-izle",
                "$baseUrl/filmizle/animasyon-filmleri-izle/2",
                "$baseUrl/filmizle/aile-filmleri-izle"
            ),
            "Suç & Polisiye" to listOf(
                "$baseUrl/filmizle/polisiye-filmleri-izle",
                "$baseUrl/filmizle/polisiye-filmleri-izle/2",
                "$baseUrl/filmizle/suc-filmleri-izle"
            ),
            "Dram & Romantik" to listOf(
                "$baseUrl/filmizle/dram-filmleri-izle",
                "$baseUrl/filmizle/dram-filmleri-izle/2",
                "$baseUrl/filmizle/romantik-filmler-izle"
            ),
            "Yerli Türk Filmleri" to listOf(
                "$baseUrl/filmizle/yerli-film-izle",
                "$baseUrl/filmizle/yerli-film-izle/2"
            )
        )

        catDefs.map { (catTitle, pageUrls) ->
            async {
                try {
                    val seen = mutableSetOf<String>()
                    val items = mutableListOf<MovieItem>()
                    for (pageUrl in pageUrls) {
                        val doc = fetchDocument(pageUrl) ?: continue
                        val cards = doc.select(".movie_box, article.movie_box, .flat_box, .trend_box")
                        for (card in cards) {
                            parseMovieItem(card, seen)?.let { items.add(it) }
                            if (items.size >= 80) break
                        }
                    }
                    if (items.isNotEmpty()) VodCategory(catTitle, items) else null
                } catch (e: Exception) {
                    null
                }
            }
        }.mapNotNull { it.await() }
    }

    override suspend fun search(query: String, page: Int): List<MovieItem> = withContext(Dispatchers.IO) {
        val seen = mutableSetOf<String>()
        val items = mutableListOf<MovieItem>()
        try {
            // 1. AJAX Search POST
            val formBody = FormBody.Builder()
                .add("action", "ajax_search")
                .add("arama_kelime", query)
                .build()
            val request = Request.Builder()
                .url("$baseUrl/arama/")
                .post(formBody)
                .header("User-Agent", userAgent)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", baseUrl)
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                response.close()
                val doc = Jsoup.parse(html, baseUrl)
                val searchCards = doc.select("a.search_s")
                for (card in searchCards) {
                    val href = card.attr("href")
                    val fullUrl = if (href.startsWith("http")) href else "$baseUrl$href"
                    if (seen.add(fullUrl)) {
                        val title = card.selectFirst(".title")?.text()?.trim()
                            ?: card.attr("title").trim()
                        val poster = card.selectFirst("img")?.attr("src") ?: ""
                        val year = card.selectFirst(".year")?.text()?.trim() ?: ""
                        items.add(
                            MovieItem(
                                title = title,
                                url = fullUrl,
                                posterUrl = poster,
                                year = year,
                                provider = name,
                                languages = listOf("TR Dublaj", "TR Altyazı")
                            )
                        )
                    }
                }
            } else {
                response.close()
            }

            // 2. Fallback: GET search page
            if (items.isEmpty()) {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val doc = fetchDocument("$baseUrl/arama/$encoded")
                if (doc != null) {
                    val cards = doc.select(".movie_box, article.movie_box, .flat_box, .search_s")
                    for (card in cards) {
                        parseMovieItem(card, seen)?.let { items.add(it) }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        items
    }

    override suspend fun getMovieDetail(movieUrl: String): MovieDetail = withContext(Dispatchers.IO) {
        val doc = fetchDocument(movieUrl) ?: throw Exception("Film detayları yüklenemedi: $movieUrl")

        val title = doc.selectFirst("h1, .title h1, meta[property='og:title']")?.let {
            if (it.hasAttr("content")) it.attr("content") else it.text()
        }?.replace(" izle", "")?.trim() ?: "Film"

        val description = doc.selectFirst("meta[name='description'], meta[property='og:description']")?.attr("content")?.trim()
            ?: doc.selectFirst(".ozet, .desc, .info .desc")?.text()?.trim()
            ?: ""

        var poster = doc.selectFirst("meta[property='og:image']")?.attr("content")?.trim() ?: ""
        if (poster.isEmpty() || poster.startsWith("data:image")) {
            val img = doc.selectFirst(".afis, picture img, .poster img")
            if (img != null) {
                poster = tv.newtv.utils.PosterUrlUtils.extractImgUrl(img, baseUrl)
            }
        }
        poster = tv.newtv.utils.PosterUrlUtils.normalize(poster, name, true)

        // Language detection with English and original language support
        val languages = mutableListOf<String>()
        val dilElem = doc.selectFirst("li:contains(Dil:)")?.text()?.lowercase() ?: ""
        // Detect Turkish
        if (dilElem.contains("dublaj") && dilElem.contains("altyaz")) {
            languages.add("Türkçe Dublaj")
            languages.add("Türkçe Altyazı")
        } else if (dilElem.contains("dublaj")) {
            languages.add("Türkçe Dublaj")
        } else if (dilElem.contains("altyaz")) {
            languages.add("Türkçe Altyazı")
        }
        // Detect English
        if (dilElem.contains("english") || dilElem.contains("ingilizce")) {
            if (dilElem.contains("dublaj")) languages.add("İngilizce Dublaj")
            if (dilElem.contains("altyaz")) languages.add("İngilizce Altyazı")
        }
        // Detect other original language if mentioned (e.g., "Orijinal: German")
        val originalLangMatch = Regex("""orijinal[:\s]+([a-zA-Z]+)""", RegexOption.IGNORE_CASE).find(dilElem)
        originalLangMatch?.groupValues?.get(1)?.let { orig ->
            val origCap = orig.replaceFirstChar { it.uppercase() }
            if (!languages.any { it.contains(origCap) }) {
                languages.add("${origCap} Dublaj")
                languages.add("${origCap} Altyazı")
            }
        }
        // Fallback to Turkish if no language detected
        if (languages.isEmpty()) {
            languages.add("Türkçe Dublaj")
            languages.add("Türkçe Altyazı")
        }

        // Extract iframes from Base64 scripts
        val iframes = mutableListOf<String>()
        val dubbingIframes = mutableListOf<String>()
        val subtitleIframes = mutableListOf<String>()

        val html = doc.html()

        // 1. Check direct iframes in DOM
        val directIframes = doc.select("iframe[src]")
        for (iframe in directIframes) {
            val src = iframe.attr("src")
            if (src.isNotEmpty() && !src.contains("vr_set=") && !src.contains("youtube.com") && !src.contains("google")) {
                val fullSrc = if (src.startsWith("//")) "https:$src" else src
                if (!iframes.contains(fullSrc)) iframes.add(fullSrc)
            }
        }

        // 2. gruphtmls üzerinden part id -> dil/etiket haritası oluştur (Örn: fast0 -> TR Dublaj, fast1 -> TR Altyazı)
        val partLangMap = mutableMapOf<String, String>()
        val grupRegex = Regex("""gruphtmls\[\d+\]\s*=\s*['"](.*?)['"];""")
        val liRegex = Regex("""<li\s+id=['"]([^'"]+)['"][^>]*>.*?<a[^>]*>([^<]+)</a>""", RegexOption.DOT_MATCHES_ALL)
        grupRegex.findAll(html).forEach { gMatch ->
            val gContent = gMatch.groupValues[1]
            liRegex.findAll(gContent).forEach { liMatch ->
                val pId = liMatch.groupValues[1].trim()
                val pName = liMatch.groupValues[2].trim()
                partLangMap["prt_$pId"] = pName
                partLangMap[pId] = pName
            }
        }

        // 3. Decode ilkpartkod (Genellikle varsayılan oynatıcı - Vidmixi)
        val ilkPartRegex = Regex("""var\s+ilkpartkod\s*=\s*['"]([A-Za-z0-9+/=]+)['"]""")
        ilkPartRegex.find(html)?.let { match ->
            val b64 = match.groupValues[1]
            try {
                val decoded = b64.decodeBase64()?.utf8().orEmpty()
                val srcMatch = Regex("""src=['"]([^'"]+)['"]""").find(decoded)
                srcMatch?.groupValues?.get(1)?.let { src ->
                    val fullSrc = if (src.startsWith("//")) "https:$src" else src
                    if (!iframes.contains(fullSrc)) iframes.add(fullSrc)
                    // Vidmixi çoklu ses/altyazı içerdiğinden her iki listeye de ekle
                    if (!dubbingIframes.contains(fullSrc)) dubbingIframes.add(fullSrc)
                    if (!subtitleIframes.contains(fullSrc)) subtitleIframes.add(fullSrc)
                }
            } catch (e: Exception) { android.util.Log.d("FilmModuScraper", "ilkpartkod decode failed", e) }
        }

        // 4. Decode pdata entries: pdata['prt_...'] = '...'
        val pdataRegex = Regex("""pdata\['([^']+)'\]\s*=\s*['"]([A-Za-z0-9+/=]+)['"]""")
        val iframePrefix = Regex("""(?:var\s+)?bakbuna\s*=\s*['"]([A-Za-z0-9+/=]+)['"]""")
            .find(html)?.groupValues?.get(1) ?: "PGlmcmFtZSB"

        pdataRegex.findAll(html).forEach { match ->
            val partId = match.groupValues[1]
            val b64Part = match.groupValues[2]
            try {
                val fullB64 = if (!b64Part.startsWith("PGlmcmFtZ")) {
                    iframePrefix + b64Part
                } else {
                    b64Part
                }
                val decoded = fullB64.decodeBase64()?.utf8().orEmpty()
                val srcMatch = Regex("""src=['"]([^'"]+)['"]""").find(decoded)
                srcMatch?.groupValues?.get(1)?.let { src ->
                    val fullSrc = if (src.startsWith("//")) "https:$src" else src
                    if (!iframes.contains(fullSrc)) iframes.add(fullSrc)

                    val label = (partLangMap[partId] ?: "").lowercase()
                    val isDub = label.contains("dublaj") || label.contains("tr ") || partId.contains("dub") || partId.contains("tr")
                    val isSub = label.contains("altyaz") || label.contains("sub") || partId.contains("alt") || partId.contains("sub")
                    val isDual = label.contains("dual") || label.contains("silver") || label.contains("atom") || src.contains("vidmixi")

                    if (isDual) {
                        if (!dubbingIframes.contains(fullSrc)) dubbingIframes.add(fullSrc)
                        if (!subtitleIframes.contains(fullSrc)) subtitleIframes.add(fullSrc)
                    } else if (isDub) {
                        if (!dubbingIframes.contains(fullSrc)) dubbingIframes.add(fullSrc)
                    } else if (isSub) {
                        if (!subtitleIframes.contains(fullSrc)) subtitleIframes.add(fullSrc)
                    } else {
                        // Belirsiz ise her ikisine de ekle
                        if (!dubbingIframes.contains(fullSrc)) dubbingIframes.add(fullSrc)
                        if (!subtitleIframes.contains(fullSrc)) subtitleIframes.add(fullSrc)
                    }
                }
            } catch (e: Exception) { android.util.Log.d("FilmModuScraper", "pdata decode failed", e) }
        }

        // Kodlanmış fragmanlar da DOM'daki fragmanlar gibi film kaynağı değildir.
        val trailerOnly = iframes.any(VodSourceUrls::isTrailer) && VodSourceUrls.playable(iframes).isEmpty()
        val cleanIframes = VodSourceUrls.playable(iframes)
        val cleanDub = VodSourceUrls.playable(dubbingIframes)
        val cleanSub = VodSourceUrls.playable(subtitleIframes)
        iframes.clear()
        iframes.addAll(cleanIframes)
        dubbingIframes.clear()
        dubbingIframes.addAll(cleanDub)
        subtitleIframes.clear()
        subtitleIframes.addAll(cleanSub)

        // Dil etiketlerini doldur
        languages.clear()
        if (dubbingIframes.isNotEmpty()) languages.add("Türkçe Dublaj")
        if (subtitleIframes.isNotEmpty()) languages.add("Türkçe Altyazı")
        if (languages.isEmpty() && iframes.isNotEmpty()) {
            languages.add("Türkçe Dublaj")
            languages.add("Türkçe Altyazı")
        }

        if (dubbingIframes.isEmpty() && iframes.isNotEmpty()) {
            dubbingIframes.addAll(iframes)
        }
        if (subtitleIframes.isEmpty() && iframes.isNotEmpty()) {
            subtitleIframes.addAll(iframes)
        }

        MovieDetail(
            title = title,
            description = description,
            posterUrl = poster,
            iframes = iframes,
            provider = name,
            languages = languages,
            dubbingIframes = dubbingIframes,
            subtitleIframes = subtitleIframes,
            hasDub = dubbingIframes.isNotEmpty(),
            hasSubtitle = subtitleIframes.isNotEmpty(),
            hasTrailerOnly = trailerOnly
        )
    }
}
