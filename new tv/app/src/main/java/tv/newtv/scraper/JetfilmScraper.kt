package tv.newtv.scraper

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tv.newtv.data.models.MovieDetail
import tv.newtv.data.models.MovieItem
import tv.newtv.data.models.VodCategory
import java.net.URLEncoder

class JetfilmScraper(private val client: OkHttpClient) : MovieScraper {
    override val name: String = "JetFilmizle"
    private val baseUrl = "https://jetfilmizle.top"
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
        val linkElem = if (element.tagName() == "a") element else element.selectFirst("a[href*='-izle']") ?: return null
        val href = linkElem.attr("href")
        if (href.isEmpty() || href == "#") return null

        val fullUrl = if (href.startsWith("http")) href else "$baseUrl$href"
        if (!seen.add(fullUrl)) return null

        val title = element.selectFirst("h3, h2, .title")?.text()?.trim()
            ?: linkElem.attr("title").replace(" izle", "").trim()
        if (title.length < 2) return null

        var poster = tv.newtv.utils.PosterUrlUtils.extractImgUrl(element, baseUrl)
        poster = tv.newtv.utils.PosterUrlUtils.normalize(poster, name, true)

        val year = element.selectFirst(".year, span:contains(202), span:contains(201)")?.text()?.trim() ?: ""

        val text = element.text().lowercase()
        val langList = mutableListOf<String>()
        val labels = element.select(".labels, .badge, span").text().lowercase()
        val combinedText = "$text $labels"
        if (combinedText.contains("dublaj") && combinedText.contains("altyaz")) {
            langList.add("TR Dublaj")
            langList.add("TR Altyazı")
        } else if (combinedText.contains("dublaj") || combinedText.contains("türkçe dublaj")) {
            langList.add("TR Dublaj")
        } else if (combinedText.contains("altyaz") || combinedText.contains("türkçe altyazı")) {
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

        val cards = doc.select("div.group.relative, article, a[href*='-izle']")
        for (card in cards) {
            parseMovieItem(card, seen)?.let { items.add(it) }
        }
        items
    }

    override suspend fun getCategories(page: Int): List<VodCategory<MovieItem>> = withContext(Dispatchers.IO) {
        val catDefs = listOf(
            "Günün Öne Çıkanları" to "$baseUrl/",
            "Türkçe Dublaj Filmler" to "$baseUrl/turkce-dublaj/",
            "Türkçe Altyazılı Filmler" to "$baseUrl/turkce-altyazi/",
            "2025 Filmleri" to "$baseUrl/yil/2025/",
            "2024 Filmleri" to "$baseUrl/yil/2024/",
            "2023 Filmleri" to "$baseUrl/yil/2023/",
            "Aksiyon Filmleri" to "$baseUrl/tur/aksiyon-filmleri/",
            "Korku & Gerilim" to "$baseUrl/tur/korku-filmleri/",
            "Komedi Filmleri" to "$baseUrl/tur/komedi-filmleri/",
            "Bilim Kurgu & Fantastik" to "$baseUrl/tur/bilim-kurgu-filmleri/",
            "Animasyon Dünyası" to "$baseUrl/tur/animasyon-filmleri/",
            "Savaş & Tarih" to "$baseUrl/tur/savas-filmleri/",
            "Kore & Asya Sineması" to "$baseUrl/kategori/kore-filmleri-izle/"
        )

        catDefs.map { (catTitle, catUrl) ->
            async {
                try {
                    val doc = fetchDocument(catUrl) ?: return@async null
                    val seen = mutableSetOf<String>()
                    val items = mutableListOf<MovieItem>()
                    val cards = doc.select("div.group.relative, article, a[href*='-izle'], .grid a")
                    for (card in cards) {
                        parseMovieItem(card, seen)?.let { items.add(it) }
                        if (items.size >= 80) break
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
            val encoded = URLEncoder.encode(query, "UTF-8")
            val doc = fetchDocument("$baseUrl/film-ara?q=$encoded")
            if (doc != null) {
                val cards = doc.select("div.group.relative, article, a[href*='-izle']")
                for (card in cards) {
                    parseMovieItem(card, seen)?.let { items.add(it) }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        items
    }

    override suspend fun getMovieDetail(movieUrl: String): MovieDetail = withContext(Dispatchers.IO) {
        val doc = fetchDocument(movieUrl) ?: throw Exception("Film yüklenemedi: $movieUrl")

        val title = doc.selectFirst("h1, meta[property='og:title']")?.let {
            if (it.hasAttr("content")) it.attr("content") else it.text()
        }?.replace(" izle", "")?.trim() ?: "Film"

        val description = doc.selectFirst("meta[name='description'], meta[property='og:description']")?.attr("content")?.trim()
            ?: doc.selectFirst(".description, p.text-gray-300")?.text()?.trim()
            ?: ""

        var poster = doc.selectFirst("meta[property='og:image']")?.attr("content")?.trim() ?: ""
        if (poster.isEmpty() || poster.startsWith("data:image")) {
            val img = doc.selectFirst("img[alt*='$title'], .aspect-video img, .poster img, .image img")
            if (img != null) {
                poster = tv.newtv.utils.PosterUrlUtils.extractImgUrl(img, baseUrl)
            }
        }
        poster = tv.newtv.utils.PosterUrlUtils.normalize(poster, name, true)

        val iframes = mutableListOf<String>()
        val dubbingIframes = mutableListOf<String>()
        val subtitleIframes = mutableListOf<String>()
        val languages = mutableListOf<String>()

        val html = doc.html()

        // Extract videoPlayerData JSON: videoPlayerData(JSON.parse('...'), ...)
        val regex = Regex("""videoPlayerData\s*\(\s*JSON\.parse\s*\(\s*['"]([^'"]+)['"]\s*\)""")
        regex.find(html)?.let { match ->
            val rawJson = match.groupValues[1]
                .replace("\\u0022", "\"")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\/", "/")
            try {
                val json = JSONObject(rawJson)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val langKey = keys.next().lowercase() // "dual", "tr", "al", "en"
                    val arr = json.optJSONArray(langKey) ?: continue
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val link = obj.optString("link")
                        val slug = obj.optString("service_slug").ifEmpty { obj.optString("slug") }
                        val templateB64 = obj.optString("template")

                        var iframeUrl = ""
                        if (templateB64.isNotEmpty()) {
                            try {
                                val decodedTemplate = String(Base64.decode(templateB64, Base64.DEFAULT), Charsets.UTF_8)
                                val srcMatch = Regex("""(?:data-src|src)=["']([^"']+)["']""").find(decodedTemplate)
                                val templateSrc = srcMatch?.groupValues?.get(1) ?: ""
                                if (templateSrc.isNotEmpty()) {
                                    iframeUrl = templateSrc
                                        .replace("{url}", link)
                                        .replace("{link}", link)
                                        .replace("{slug}", slug)
                                }
                            } catch (e: Exception) { android.util.Log.d("JetfilmScraper", "iframe extract failed", e) }
                        }

                        if (iframeUrl.isEmpty() && link.startsWith("http")) {
                            iframeUrl = link
                        }

                        if (iframeUrl.isNotEmpty()) {
                            val fullSrc = if (iframeUrl.startsWith("//")) "https:$iframeUrl" else iframeUrl
                            if (!iframes.contains(fullSrc)) iframes.add(fullSrc)

                            val isDub = langKey.contains("tr") || langKey.contains("dub")
                            val isSub = langKey.contains("al") || langKey.contains("sub")
                            val isDual = langKey.contains("dual") || langKey == "en"

                            if (isDual) {
                                if (!dubbingIframes.contains(fullSrc)) dubbingIframes.add(fullSrc)
                                if (!subtitleIframes.contains(fullSrc)) subtitleIframes.add(fullSrc)
                            } else if (isDub) {
                                if (!dubbingIframes.contains(fullSrc)) dubbingIframes.add(fullSrc)
                            } else if (isSub) {
                                if (!subtitleIframes.contains(fullSrc)) subtitleIframes.add(fullSrc)
                            } else {
                                if (!dubbingIframes.contains(fullSrc)) dubbingIframes.add(fullSrc)
                                if (!subtitleIframes.contains(fullSrc)) subtitleIframes.add(fullSrc)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // DOM içindeki iframeleri de tara (özellikle videoPlayerData dışı alternatifler)
        doc.select("iframe[src], iframe[data-src]").forEach { iframe ->
            val src = iframe.attr("src").ifEmpty { iframe.attr("data-src") }
            if (src.isNotEmpty() && !src.contains("youtube.com") && !src.contains("google")) {
                val fullSrc = if (src.startsWith("//")) "https:$src" else src
                if (!iframes.contains(fullSrc)) {
                    iframes.add(fullSrc)
                    if (!dubbingIframes.contains(fullSrc)) dubbingIframes.add(fullSrc)
                    if (!subtitleIframes.contains(fullSrc)) subtitleIframes.add(fullSrc)
                }
            }
        }

        // Language tag populating
        languages.clear()
        if (dubbingIframes.isNotEmpty() && subtitleIframes.isNotEmpty()) {
            languages.add("Türkçe Dublaj")
            languages.add("Türkçe Altyazı")
        } else if (dubbingIframes.isNotEmpty()) {
            languages.add("Türkçe Dublaj")
        } else if (subtitleIframes.isNotEmpty()) {
            languages.add("Türkçe Altyazı")
        } else {
            languages.add("Türkçe Dublaj")
            languages.add("Türkçe Altyazı")
        }

        if (dubbingIframes.isEmpty() && iframes.isNotEmpty()) dubbingIframes.addAll(iframes)
        if (subtitleIframes.isEmpty() && iframes.isNotEmpty()) subtitleIframes.addAll(iframes)

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
            hasSubtitle = subtitleIframes.isNotEmpty()
        )
    }
}
