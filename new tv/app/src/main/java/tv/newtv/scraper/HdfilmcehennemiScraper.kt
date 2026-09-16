package tv.newtv.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import tv.newtv.data.models.Episode
import tv.newtv.data.models.EpisodeSources
import tv.newtv.data.models.MovieDetail
import tv.newtv.data.models.MovieItem
import tv.newtv.data.models.Season
import tv.newtv.data.models.VodCategory
import tv.newtv.utils.PosterUrlUtils

class HdfilmcehennemiScraper(private val client: OkHttpClient) : MovieScraper {
    override val name = "HDFilmcehennemi"
    private val baseUrl = "https://www.hdfilmcehennemi.nl"
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private fun fetchDocument(url: String): Document {
        val formattedUrl = if (url.startsWith("http")) url else if (url.startsWith("/")) "$baseUrl$url" else "$baseUrl/$url"
        val request = Request.Builder()
            .url(formattedUrl)
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7")
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("Failed to fetch: ${response.code}")
        val html = response.body?.string().orEmpty()
        return Jsoup.parse(html, formattedUrl)
    }

    /**
     * HDFilmcehennemi kartlarından filmleri ayrıştırır.
     * Hem anasayfa/kategori grid kartlarını hem de slider ve search kartlarını destekler.
     */
    fun parseMoviesFromDoc(doc: Document, excludeSliders: Boolean = false): List<MovieItem> {
        val list = mutableListOf<MovieItem>()
        val seen = mutableSetOf<String>()

        val selector = if (excludeSliders) {
            ".posters-4-col a.poster, .posters-4-col a, a.poster:not(.poster-slider)"
        } else {
            "a.poster, .posters-4-col a, a.search-result, article.item, .poster"
        }

        doc.select(selector).forEach { itemEl ->
            val aTag = (if (itemEl.tagName().equals("a", ignoreCase = true)) itemEl else null)
                ?: itemEl.selectFirst("a")
                ?: return@forEach

            val href = aTag.attr("href").trim()
            if (href.isBlank() || href == "#" || href == "/" || href == "$baseUrl/" || href.contains("javascript:", ignoreCase = true)) {
                return@forEach
            }

            // Site menü, tür, kategori vb. sayfalarını film olarak ekleme
            if (href.contains("/tur/", ignoreCase = true) ||
                href.contains("/yil/", ignoreCase = true) ||
                href.contains("/category/", ignoreCase = true) ||
                href.contains("/dil/", ignoreCase = true) ||
                href.contains("/ulke/", ignoreCase = true) ||
                href.contains("/oyuncu/", ignoreCase = true) ||
                href.contains("/yonetmen/", ignoreCase = true) ||
                href.contains("/etiket/", ignoreCase = true) ||
                href.contains("/hesabim/", ignoreCase = true) ||
                href.contains("/iletisim/", ignoreCase = true) ||
                href.contains("/dist/", ignoreCase = true) ||
                href.contains("/assets/", ignoreCase = true)) {
                return@forEach
            }

            val imgTag = aTag.selectFirst("img") ?: itemEl.selectFirst("img") ?: return@forEach
            val titleEl = aTag.selectFirst(".poster-title, strong, h2, h3, .title, .flbaslik, .data")
            val rawTitle = titleEl?.text()?.ifEmpty { aTag.attr("title") }?.ifEmpty { imgTag.attr("alt") } ?: ""
            val title = rawTitle.replace(" izle", "", ignoreCase = true)
                .replace(" Poster", "", ignoreCase = true)
                .replace(" izleyin", "", ignoreCase = true)
                .trim()

            val poster = PosterUrlUtils.extractImgUrl(imgTag, baseUrl)
            val fullUrl = if (href.startsWith("http")) href else if (href.startsWith("/")) "$baseUrl$href" else "$baseUrl/$href"

            val cardYear = aTag.selectFirst(".poster-meta span, .anayil, .yil")?.text()?.trim().orEmpty()
            val year = if (Regex("""^\d{4}$""").matches(cardYear)) {
                cardYear
            } else {
                Regex("""\b(19\d\d|20\d\d)\b""").find("$href $title $cardYear")?.value ?: ""
            }

            val rawRating = aTag.selectFirst(".imdb, [class*='imdb'], .rating, .score, .puan, [class*='rating']")?.text().orEmpty()
            val cardRating = Regex("""\b(\d+[\.,]\d+|\d+)\b""").find(rawRating)?.value?.replace(',', '.').orEmpty()

            val langText = aTag.selectFirst(".poster-lang, .dil, .anadil")?.text()?.trim().orEmpty()
            val languages = mutableListOf<String>()
            if (langText.contains("Dublaj", ignoreCase = true)) languages.add("Türkçe Dublaj")
            if (langText.contains("Altyaz", ignoreCase = true)) languages.add("Türkçe Altyazılı")
            if (languages.isEmpty() && (langText.contains("dual", ignoreCase = true) || langText.isNotBlank())) {
                languages.add("Dublaj & Altyazılı")
            }

            if (title.isNotEmpty() && !title.equals("film", ignoreCase = true) && poster.isNotBlank() && seen.add(fullUrl)) {
                list.add(MovieItem(title = title, url = fullUrl, posterUrl = poster, year = year, provider = name, rating = cardRating, languages = languages))
            }
        }
        return list
    }

    /**
     * HDFilmcehennemi AJAX sayfa içeriğini çeker (/load/page/{page}/{actionPath}/)
     */
    private fun fetchAjaxPage(actionPath: String, page: Int): List<MovieItem> {
        return try {
            val actClean = actionPath.trim().trim('/')
            val url = "$baseUrl/load/page/$page/$actClean/"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Referer", "$baseUrl/")
                .header("X-Requested-With", "fetch")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val bodyStr = resp.body?.string().orEmpty()
                val json = try { JSONObject(bodyStr) } catch (_: Exception) { null }
                val html = json?.optString("html") ?: bodyStr
                if (html.isBlank()) return emptyList()
                val doc = Jsoup.parse(html, baseUrl)
                parseMoviesFromDoc(doc, excludeSliders = true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchAjaxPage failed for $actionPath page $page", e)
            emptyList()
        }
    }

    /**
     * 1. Yeni Eklenenler (Sitedeki canlı ana akış - sayfa 1 ana sayfa gridi, 2+ AJAX)
     */
    override suspend fun getRecent(page: Int): List<MovieItem> = withContext(Dispatchers.IO) {
        try {
            if (page <= 1) {
                val doc = fetchDocument(baseUrl)
                val items = parseMoviesFromDoc(doc, excludeSliders = true)
                if (items.isNotEmpty()) items else parseMoviesFromDoc(doc, excludeSliders = false)
            } else {
                fetchAjaxPage("home", page)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getRecent failed", e)
            emptyList()
        }
    }

    override suspend fun getCategories(page: Int): List<VodCategory<MovieItem>> = withContext(Dispatchers.IO) {
        val movies = getRecent(page)
        if (movies.isNotEmpty()) {
            listOf(VodCategory("Tüm Filmler", movies))
        } else {
            emptyList()
        }
    }

    /**
     * HDFilmcehennemi manşet slider / carousel alanındaki öne çıkan filmleri çeker.
     */
    suspend fun getFeaturedMovies(): List<MovieItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MovieItem>()
        val seen = mutableSetOf<String>()
        try {
            val doc = fetchDocument(baseUrl)
            val sliderItems = doc.select("a.poster.poster-slider, #slider-master a.poster, .slider a.poster, .adv_slider a.poster")
            for (item in sliderItems) {
                val href = item.attr("href").trim()
                if (href.isBlank() || href == "#" || href == "/") continue
                val rawTitle = item.selectFirst(".poster-title, strong, h2, h3, .title")?.text()
                    ?: item.attr("title").ifEmpty { item.selectFirst("img")?.attr("alt") }
                    ?: ""
                val title = rawTitle.replace(" izle", "", ignoreCase = true)
                    .replace(" Poster", "", ignoreCase = true)
                    .trim()
                val img = item.selectFirst("img")
                val poster = if (img != null) PosterUrlUtils.extractImgUrl(img, baseUrl) else ""
                val year = item.selectFirst(".poster-meta span, .anayil, .yil")?.text()?.trim()
                    ?: Regex("""\b(19\d\d|20\d\d)\b""").find("$href $title")?.value.orEmpty()
                val rawRating = item.selectFirst(".imdb, .rating, [class*='rating']")?.text().orEmpty()
                val rating = Regex("""\b(\d+[\.,]\d+|\d+)\b""").find(rawRating)?.value?.replace(',', '.').orEmpty()
                val langText = item.selectFirst(".poster-lang, .dil, .anadil")?.text()?.trim().orEmpty()
                val languages = mutableListOf<String>()
                if (langText.contains("Dublaj", ignoreCase = true)) languages.add("Türkçe Dublaj")
                if (langText.contains("Altyaz", ignoreCase = true)) languages.add("Türkçe Altyazılı")
                if (languages.isEmpty()) {
                    languages.add("Dublaj & Altyazılı")
                }
                val fullUrl = if (href.startsWith("http")) href else if (href.startsWith("/")) "$baseUrl$href" else "$baseUrl/$href"
                if (title.isNotEmpty() && fullUrl.isNotEmpty() && poster.isNotBlank() && seen.add(fullUrl)) {
                    list.add(
                        MovieItem(
                            title = title,
                            url = fullUrl,
                            posterUrl = poster,
                            year = year,
                            rating = rating,
                            languages = languages,
                            provider = name
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getFeaturedMovies failed", e)
        }
        list
    }

    /**
     * 2. Tavsiye Filmler
     */
    suspend fun getRecommendedMovies(page: Int = 1): List<MovieItem> = withContext(Dispatchers.IO) {
        try {
            if (page <= 1) {
                val doc = fetchDocument("$baseUrl/category/tavsiye-filmler-izle2/")
                parseMoviesFromDoc(doc, excludeSliders = true).ifEmpty { parseMoviesFromDoc(doc) }
            } else {
                fetchAjaxPage("categories/tavsiye-filmler-izle3", page)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getRecommendedMovies failed", e)
            emptyList()
        }
    }

    /**
     * 3. Imdb 7+ Filmler
     */
    suspend fun getImdb7Movies(page: Int = 1): List<MovieItem> = withContext(Dispatchers.IO) {
        try {
            if (page <= 1) {
                val doc = fetchDocument("$baseUrl/imdb-7-puan-uzeri-filmler-2/")
                parseMoviesFromDoc(doc, excludeSliders = true).ifEmpty { parseMoviesFromDoc(doc) }
            } else {
                fetchAjaxPage("imdb7", page)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getImdb7Movies failed", e)
            emptyList()
        }
    }

    /**
     * 4. En Çok Yorumlananlar
     */
    suspend fun getMostCommentedMovies(page: Int = 1): List<MovieItem> = withContext(Dispatchers.IO) {
        try {
            if (page <= 1) {
                val doc = fetchDocument("$baseUrl/en-cok-yorumlananlar-2/")
                parseMoviesFromDoc(doc, excludeSliders = true).ifEmpty { parseMoviesFromDoc(doc) }
            } else {
                fetchAjaxPage("mostCommented", page)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMostCommentedMovies failed", e)
            emptyList()
        }
    }

    /**
     * 5. En Çok Beğenilenler
     */
    suspend fun getMostLikedMovies(page: Int = 1): List<MovieItem> = withContext(Dispatchers.IO) {
        try {
            if (page <= 1) {
                val doc = fetchDocument("$baseUrl/en-cok-begenilen-filmleri-izle-4/")
                parseMoviesFromDoc(doc, excludeSliders = true).ifEmpty { parseMoviesFromDoc(doc) }
            } else {
                fetchAjaxPage("mostLiked", page)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMostLikedMovies failed", e)
            emptyList()
        }
    }

    /**
     * Kategoriye göre film listeleme (AJAX sayfalamalı)
     */
    suspend fun getMoviesByGenre(genreSlug: String, page: Int = 1): List<MovieItem> = withContext(Dispatchers.IO) {
        try {
            if (genreSlug.isBlank() || genreSlug.equals("tum", ignoreCase = true)) {
                return@withContext getRecent(page)
            }
            val url = if (genreSlug.startsWith("http")) {
                genreSlug
            } else if (genreSlug.contains("yerli") || genreSlug.contains("turkce-dublaj") || genreSlug.contains("populer") || genreSlug.contains("imdb")) {
                "$baseUrl/$genreSlug/"
            } else {
                "$baseUrl/tur/$genreSlug/"
            }

            if (page <= 1) {
                val doc = fetchDocument(url)
                parseMoviesFromDoc(doc, excludeSliders = true).ifEmpty { parseMoviesFromDoc(doc) }
            } else {
                fetchAjaxPage("genres/$genreSlug", page)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMoviesByGenre failed for $genreSlug", e)
            emptyList()
        }
    }

    /**
     * Yıla göre film listeleme (AJAX sayfalamalı)
     */
    suspend fun getMoviesByYear(year: String, page: Int = 1): List<MovieItem> = withContext(Dispatchers.IO) {
        try {
            if (year.isBlank() || year.equals("tum", ignoreCase = true)) {
                return@withContext getRecent(page)
            }
            val yearSlug = when (year.trim()) {
                "2025" -> "2025-filmleri-izle-3"
                "2024" -> "2024-2"
                "2023" -> "2023-yapimi-filmler-4"
                "2022" -> "2022-filmleri-izle-3"
                "2021" -> "2021-filmleri-izle-3"
                "2020" -> "2020-yapimi-film-izle-2"
                "2019" -> "2-2019-filmleri-izleyin"
                "2018" -> "2018-film-izle-2"
                else -> year.trim()
            }
            val url = "$baseUrl/yil/$yearSlug/"
            if (page <= 1) {
                val doc = fetchDocument(url)
                parseMoviesFromDoc(doc, excludeSliders = true).ifEmpty { parseMoviesFromDoc(doc) }
            } else {
                fetchAjaxPage("years/$yearSlug", page)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getMoviesByYear failed for $year", e)
            emptyList()
        }
    }

    /**
     * Hem Kategori Hem Yıl birlikte seçildiğinde iki kriteri birleştiren sorgulama
     */
    suspend fun getMoviesByGenreAndYear(genreSlug: String, year: String, maxPages: Int = 8): List<MovieItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<MovieItem>()
        val seenUrls = mutableSetOf<String>()
        for (page in 1..maxPages) {
            val pageItems = getMoviesByGenre(genreSlug, page)
            if (pageItems.isEmpty()) break
            val matching = pageItems.filter { item ->
                val matchesYear = item.year.contains(year) || item.url.contains(year) || item.title.contains(year)
                matchesYear && seenUrls.add(item.url)
            }
            result.addAll(matching)
            if (result.size >= 24) break
        }
        result
    }

    override suspend fun search(query: String, page: Int): List<MovieItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        try {
            val encoded = java.net.URLEncoder.encode(trimmed, "UTF-8")
            val searchUrl = "$baseUrl/search?q=$encoded"
            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", userAgent)
                .header("Referer", "$baseUrl/")
                .header("X-Requested-With", "fetch")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .build()
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string().orEmpty()
                    val json = try { JSONObject(bodyStr) } catch (_: Exception) { null }
                    val resultsArray = json?.optJSONArray("results")
                    if (resultsArray != null && resultsArray.length() > 0) {
                        val combinedHtml = (0 until resultsArray.length()).joinToString("\n") { idx ->
                            resultsArray.optString(idx)
                        }
                        val doc = Jsoup.parse(combinedHtml, baseUrl)
                        val parsed = parseMoviesFromDoc(doc)
                        if (parsed.isNotEmpty()) return@withContext parsed
                    }
                }
            }
            // Fallback: Standart arama sayfası
            val doc = fetchDocument("$baseUrl/?s=$encoded")
            parseMoviesFromDoc(doc)
        } catch (e: Exception) {
            Log.e(TAG, "search failed for $trimmed", e)
            emptyList()
        }
    }

    override suspend fun getMovieDetail(movieUrl: String): MovieDetail = withContext(Dispatchers.IO) {
        val doc = fetchDocument(movieUrl)
        val html = doc.html()

        val rawTitle = doc.selectFirst("h1")?.text()?.ifEmpty { doc.selectFirst(".flbaslik")?.text() } ?: ""
        val title = rawTitle.replace(" izle", "", ignoreCase = true).trim()

        val descEl = doc.selectFirst(".wp-content p, .wp-content, article p, .story, .overview, .entry-content p, .post-content p, .film-content p, .summary, .description, p.desc")
        var description = descEl?.text()?.trim().orEmpty()
        if (description.isBlank() || description.contains("kesintisiz, sansürsüz", ignoreCase = true) || description.length < 30) {
            val bestP = doc.select("article p, .wp-content p, div.content p, div p").map { it.text().trim() }
                .firstOrNull { 
                    it.length > 50 && 
                    !it.contains("kesintisiz", ignoreCase = true) && 
                    !it.contains("Yorum yazabilmek için", ignoreCase = true) &&
                    !it.contains("Giriş yapmalısınız", ignoreCase = true) &&
                    !it.contains("telif", ignoreCase = true) &&
                    !it.contains("cookie", ignoreCase = true)
                }
            if (!bestP.isNullOrBlank()) {
                description = bestP
            }
        }
        if (description.isBlank()) {
            val meta = doc.selectFirst("meta[name=description]")?.attr("content")?.trim().orEmpty()
            if (!meta.contains("kesintisiz, sansürsüz", ignoreCase = true)) {
                description = meta
            }
        }

        val posterEl = doc.selectFirst("aside.post-info-poster img, .post-info-poster img, .post-info img")
        var poster = if (posterEl != null) PosterUrlUtils.extractImgUrl(posterEl, baseUrl) else ""
        if (poster.isBlank()) {
            val schemaImg = Regex(""""image"\s*:\s*"([^"]+)"""").find(html)?.groupValues?.get(1)?.trim().orEmpty()
            poster = if (schemaImg.isNotBlank()) schemaImg else doc.selectFirst("meta[property='og:image']")?.attr("content")?.trim().orEmpty()
        }
        if (poster.isBlank()) {
            val fallbackEl = doc.selectFirst(".poster img, img[data-src*='uploads']")
            if (fallbackEl != null) poster = PosterUrlUtils.extractImgUrl(fallbackEl, baseUrl)
        }

        val iframes = mutableListOf<String>()
        val dubbingIframes = mutableListOf<String>()
        val subtitleIframes = mutableListOf<String>()
        val languages = mutableListOf<String>()
        var isSeries = false
        val seasonsMap = sortedMapOf<Int, MutableList<Episode>>()

        val hasDubHtml = html.contains("Dublaj", ignoreCase = true)
        val hasSubHtml = html.contains("Altyaz", ignoreCase = true)
        if (hasDubHtml) languages.add("Türkçe Dublaj")
        if (hasSubHtml) languages.add("Türkçe Altyazılı")

        // 1. Yeni HDFilmcehennemi /video/{id}/ alternatif buton sorguları
        try {
            val videoButtons = doc.select("button[data-video], [data-video]")
            if (videoButtons.isNotEmpty()) {
                coroutineScope {
                    val deferreds = videoButtons.map { btn ->
                        async(Dispatchers.IO) {
                            try {
                                val vid = btn.attr("data-video").trim()
                                if (vid.isBlank()) return@async null
                                val btnText = btn.text().lowercase()
                                val parentLang = btn.parents().firstOrNull { it.hasAttr("data-lang") }?.attr("data-lang")?.lowercase().orEmpty()
                                val req = Request.Builder()
                                    .url("$baseUrl/video/$vid/")
                                    .header("User-Agent", userAgent)
                                    .header("Referer", movieUrl)
                                    .header("X-Requested-With", "fetch")
                                    .header("Content-Type", "application/json")
                                    .build()
                                client.newCall(req).execute().use { resp ->
                                    if (resp.isSuccessful) {
                                        val body = resp.body?.string().orEmpty()
                                        val json = try { JSONObject(body) } catch (_: Exception) { null }
                                        val htmlData = json?.optJSONObject("data")?.optString("html")
                                            ?: json?.optString("html")
                                            ?: body
                                        val ifr = Jsoup.parse(htmlData).selectFirst("iframe")
                                        val src = ifr?.attr("data-src")?.ifEmpty { ifr.attr("src") }
                                        if (!src.isNullOrBlank()) {
                                            Triple(src, btnText, parentLang)
                                        } else null
                                    } else null
                                }
                            } catch (_: Exception) { null }
                        }
                    }
                    val results = deferreds.awaitAll().filterNotNull()
                    for ((pUrl, btnText, parentLang) in results) {
                        val formattedUrl = if (pUrl.startsWith("//")) "https:$pUrl" else pUrl
                        if (!iframes.contains(formattedUrl)) {
                            iframes.add(formattedUrl)
                            val isSub = btnText.contains("altyaz") || parentLang.contains("sub") || parentLang.contains("altyaz")
                            val isDub = btnText.contains("dublaj") || parentLang.contains("dub")
                            if (isDub) {
                                dubbingIframes.add(formattedUrl)
                                if (!languages.contains("Türkçe Dublaj")) languages.add("Türkçe Dublaj")
                            }
                            if (isSub) {
                                subtitleIframes.add(formattedUrl)
                                if (!languages.contains("Türkçe Altyazılı")) languages.add("Türkçe Altyazılı")
                            }
                            if (!isDub && !isSub) {
                                dubbingIframes.add(formattedUrl)
                                subtitleIframes.add(formattedUrl)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve /video/ endpoints", e)
        }

        // 2. WordPress AJAX video URL sorgusu (fallback)
        try {
            val ajaxNonce = Regex("""videoAjax\s*=\s*\{[^}]*nonce\s*:\s*['"]([a-f0-9]+)['"]""").find(html)?.groupValues?.get(1)
                ?: Regex("""['"]nonce['"]\s*:\s*['"]([a-f0-9]+)['"]""").find(html)?.groupValues?.get(1)
            val postId = Regex("""data-post-id=['"](\d+)['"]""").find(html)?.groupValues?.get(1)

            val playerNames = Regex("""data-player-name=['"]([^'"]+)['"]""")
                .findAll(html)
                .map { it.groupValues[1].trim() }
                .filter { it.isNotEmpty() && !it.contains("$") && !it.contains("+") }
                .distinct()
                .toList()
                .ifEmpty { listOf("SetPlay") }

            val partKeys = Regex("""data-part-key=['"]([^'"]+)['"]""")
                .findAll(html)
                .map { it.groupValues[1].trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .toList()
                .ifEmpty { listOf("turkcedublaj", "turkcealtyazi", "") }

            partKeys.forEach { pk ->
                if (pk.contains("dublaj", ignoreCase = true) && !languages.contains("Türkçe Dublaj")) languages.add("Türkçe Dublaj")
                if (pk.contains("altyaz", ignoreCase = true) && !languages.contains("Türkçe Altyazılı")) languages.add("Türkçe Altyazılı")
            }

            if (!ajaxNonce.isNullOrBlank() && !postId.isNullOrBlank()) {
                val selectedPlayers = playerNames.take(2)
                val targetPartKeys = if (partKeys.any { it.contains("dublaj") || it.contains("altyazi") }) {
                    partKeys.filter { it.contains("dublaj", true) || it.contains("altyaz", true) || it.isBlank() }
                } else {
                    partKeys.take(2)
                }

                coroutineScope {
                    val deferreds = selectedPlayers.flatMap { playerName ->
                        targetPartKeys.map { partKey ->
                            async(Dispatchers.IO) {
                                try {
                                    val formBody = FormBody.Builder()
                                        .add("action", "get_video_url")
                                        .add("nonce", ajaxNonce)
                                        .add("post_id", postId)
                                        .add("player_name", playerName)
                                        .add("part_key", partKey)
                                        .build()

                                    val request = Request.Builder()
                                        .url("$baseUrl/wp-admin/admin-ajax.php")
                                        .header("User-Agent", userAgent)
                                        .header("Referer", movieUrl)
                                        .header("X-Requested-With", "XMLHttpRequest")
                                        .post(formBody)
                                        .build()

                                    client.newCall(request).execute().use { resp ->
                                        if (resp.isSuccessful) {
                                            val body = resp.body?.string().orEmpty()
                                            val json = try { JSONObject(body) } catch (_: Exception) { null }
                                            val pUrl = json?.optJSONObject("data")?.optString("url")
                                                ?: json?.optString("url")
                                                ?: Regex("""https?://[^\s"'<>]+""").find(body)?.value
                                            if (!pUrl.isNullOrBlank()) {
                                                Triple(pUrl, playerName, partKey)
                                            } else null
                                        } else null
                                    }
                                } catch (_: Exception) {
                                    null
                                }
                            }
                        }
                    }

                    val results = deferreds.awaitAll().filterNotNull()
                    for ((pUrl, _, partKey) in results) {
                        if (!iframes.contains(pUrl)) {
                            iframes.add(pUrl)
                            val isDub = partKey.contains("dublaj", ignoreCase = true)
                            val isSub = partKey.contains("altyaz", ignoreCase = true)
                            if (isDub) {
                                dubbingIframes.add(pUrl)
                                if (!languages.contains("Türkçe Dublaj")) languages.add("Türkçe Dublaj")
                            }
                            if (isSub) {
                                subtitleIframes.add(pUrl)
                                if (!languages.contains("Türkçe Altyazılı")) languages.add("Türkçe Altyazılı")
                            }
                            if (!isDub && !isSub) {
                                dubbingIframes.add(pUrl)
                                subtitleIframes.add(pUrl)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve ajax video url", e)
        }

        // 2. DOM iframe'lerini de kontrol et (fallback)
        doc.select("iframe").forEach { iframe ->
            val src = iframe.attr("src").ifEmpty { iframe.attr("data-src") }
            if (src.isNotEmpty() && !src.contains("youtube") && !src.contains("google")) {
                val formattedSrc = if (src.startsWith("//")) "https:$src" else src
                if (!iframes.contains(formattedSrc)) {
                    iframes.add(formattedSrc)
                    dubbingIframes.add(formattedSrc)
                    subtitleIframes.add(formattedSrc)
                }
            }
        }

        // 3. Dizi bölümlerini tespit et (dizi ise)
        val epRegex = Regex("""/([a-zA-Z0-9_\-]+)-(\d+)-sezon-(\d+)-bolum""")
        doc.select("a[href*='-sezon-'][href*='-bolum']").forEach { aTag ->
            val href = aTag.attr("href")
            val match = epRegex.find(href)
            if (match != null) {
                isSeries = true
                val sNum = match.groupValues[2].toIntOrNull() ?: 1
                val epNum = match.groupValues[3].toIntOrNull() ?: 1
                val epTitle = aTag.text().ifEmpty { "$epNum. Bölüm" }
                val fullEpUrl = if (href.startsWith("http")) href else if (href.startsWith("/")) "$baseUrl$href" else "$baseUrl/$href"

                val episodeItems = seasonsMap.getOrPut(sNum) { mutableListOf() }
                if (episodeItems.none { it.url == fullEpUrl }) {
                    episodeItems.add(Episode(epNum, epTitle, fullEpUrl))
                }
            }
        }

        val parsedSeasons = seasonsMap.map { (sNum, epList) ->
            Season(seasonNumber = sNum, name = "$sNum. Sezon", episodes = epList)
        }

        if (dubbingIframes.isNotEmpty() && !languages.contains("Türkçe Dublaj")) languages.add("Türkçe Dublaj")
        if (subtitleIframes.isNotEmpty() && !languages.contains("Türkçe Altyazı")) languages.add("Türkçe Altyazı")
        if (languages.isEmpty() && iframes.isNotEmpty()) {
            languages.add("Türkçe Dublaj")
            languages.add("Türkçe Altyazı")
        }

        val cleanPoster = PosterUrlUtils.normalize(poster, name, true)

        MovieDetail(
            title = title,
            description = description,
            posterUrl = cleanPoster,
            iframes = iframes,
            isSeries = isSeries,
            seasons = parsedSeasons,
            provider = name,
            languages = languages,
            dubbingIframes = dubbingIframes,
            subtitleIframes = subtitleIframes,
            hasDub = dubbingIframes.isNotEmpty() || hasDubHtml || languages.any { it.contains("dublaj", ignoreCase = true) },
            hasSubtitle = subtitleIframes.isNotEmpty() || hasSubHtml || languages.any { it.contains("altyaz", ignoreCase = true) }
        )
    }

    override suspend fun getEpisodeSources(episodeUrl: String): EpisodeSources = withContext(Dispatchers.IO) {
        val detail = getMovieDetail(episodeUrl)
        EpisodeSources(
            iframes = detail.iframes,
            dubbingIframes = detail.dubbingIframes,
            subtitleIframes = detail.subtitleIframes,
            hasDub = detail.hasDub,
            hasSubtitle = detail.hasSubtitle
        )
    }

    override suspend fun getEpisodeIframes(episodeUrl: String): List<String> = withContext(Dispatchers.IO) {
        getEpisodeSources(episodeUrl).iframes
    }

    private companion object {
        const val TAG = "Hdfilmcehennemi"
    }
}
