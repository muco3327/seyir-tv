package tv.newtv.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import android.util.Base64
import tv.newtv.data.models.MovieDetail
import tv.newtv.data.models.MovieItem
import tv.newtv.data.models.VodCategory

class FullhdfilmizleseneScraper(private val client: OkHttpClient) : MovieScraper {
    override val name = "FullHDFilmİzlesene"
    private val baseUrl = "https://fullhdfilmizlesene.co"
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private fun fetchDocument(url: String): Document {
        val formattedUrl = if (url.startsWith("http")) url else "$baseUrl$url"
        val request = Request.Builder()
            .url(formattedUrl)
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            .build()
        
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
        val html = response.body?.string() ?: ""
        return Jsoup.parse(html, formattedUrl)
    }

    private fun parseMoviesFromDoc(doc: Document): List<MovieItem> {
        val items = mutableListOf<MovieItem>()
        val seen = mutableSetOf<String>()
        
        doc.select("a.image.sldurl, ul.mov-list > li a[href], div.poster-item a[href], div.film-item a[href], article.item a[href]").forEach { aTag ->
            val element = aTag.parent() ?: aTag
            val title = aTag.attr("title").ifEmpty { element.selectFirst(".title, h2, h3")?.text() ?: "" }.trim()
            val url = aTag.attr("href")
            
            var posterUrl = tv.newtv.utils.PosterUrlUtils.extractImgUrl(aTag, baseUrl)
            if (posterUrl.isEmpty()) {
                posterUrl = tv.newtv.utils.PosterUrlUtils.extractImgUrl(element, baseUrl)
            }
            posterUrl = tv.newtv.utils.PosterUrlUtils.normalize(posterUrl, name, true)
            
            val fullUrl = if (url.startsWith("http")) url else "$baseUrl$url"
            
            if (title.isNotEmpty() && url.isNotEmpty() && seen.add(fullUrl)) {
                items.add(MovieItem(title = title, url = fullUrl, posterUrl = posterUrl, provider = name))
            }
        }
        return items
    }

    override suspend fun getCategories(page: Int): List<VodCategory<MovieItem>> = withContext(Dispatchers.IO) {
        val categories = mutableListOf<VodCategory<MovieItem>>()
        try {
            val doc = fetchDocument(baseUrl)
            
            val homeMovies = parseMoviesFromDoc(doc)
            if (homeMovies.isNotEmpty()) {
                categories.add(VodCategory("Yeni Eklenen Filmler", homeMovies.take(20)))
            }
            
            val popularUrl = "$baseUrl/en-cok-izlenen-filmler"
            try {
                val popDoc = fetchDocument(popularUrl)
                val popMovies = parseMoviesFromDoc(popDoc)
                if (popMovies.isNotEmpty()) {
                    categories.add(VodCategory("Popüler Filmler", popMovies.take(20)))
                }
            } catch (e: Exception) { android.util.Log.d("FullHDScraper", "Populer failed", e) }

        } catch (e: Exception) {
            e.printStackTrace()
        }
        categories
    }

    override suspend fun search(query: String, page: Int): List<MovieItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MovieItem>()
        try {
            val searchUrl = "$baseUrl/arama/${java.net.URLEncoder.encode(query, "UTF-8")}"
            val doc = fetchDocument(searchUrl)
            items.addAll(parseMoviesFromDoc(doc))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        items
    }

    override suspend fun getMovieDetail(movieUrl: String): MovieDetail = withContext(Dispatchers.IO) {
        val doc = fetchDocument(movieUrl)
        val pageHtml = doc.html()
        
        val title = doc.selectFirst("h1")?.text() ?: ""
        val description = doc.selectFirst(".ozet, .summary, p[itemprop=description]")?.text() ?: ""
        
        var posterUrl = doc.selectFirst("meta[property='og:image']")?.attr("content")?.trim() ?: ""
        if (posterUrl.isEmpty() || posterUrl.startsWith("data:image")) {
            val img = doc.selectFirst(".poster img, .film-poster img, .image img")
            if (img != null) {
                posterUrl = tv.newtv.utils.PosterUrlUtils.extractImgUrl(img, baseUrl)
            }
        }
        posterUrl = tv.newtv.utils.PosterUrlUtils.normalize(posterUrl, name, true)
        
        val iframes = mutableListOf<String>()
        val dubbingIframes = mutableListOf<String>()
        val subtitleIframes = mutableListOf<String>()

        // Site oynatıcı adreslerini JavaScript içindeki Base64 pdata alanlarında saklıyor.
        // Görünürdeki ?vr_set=1 iframe'i gerçek video değildir; bütün partları çöz.
        val encodedPartRegex = Regex("""(?:pdata\['([^']+)'\]|ilkpartkod)\s*=\s*'([^']+)'""")
        val sourceRegex = Regex("""src\s*=\s*["']?([^"'\s>]+)""", RegexOption.IGNORE_CASE)
        encodedPartRegex.findAll(pageHtml).forEach { match ->
            val partName = match.groupValues.getOrNull(1).orEmpty().lowercase()
            var encoded = match.groupValues[2]
            if (!encoded.startsWith("PGlm") && !encoded.startsWith("PGlt")) {
                encoded = "PGlmcmFtZSB" + encoded
            }
            val decoded = runCatching {
                String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
            }.getOrNull().orEmpty()
            sourceRegex.find(decoded)?.groupValues?.getOrNull(1)?.let { raw ->
                val sourceUrl = when {
                    raw.startsWith("//") -> "https:$raw"
                    raw.startsWith("/") -> "$baseUrl$raw"
                    else -> raw
                }
                if (sourceUrl.startsWith("http") && !sourceUrl.contains("youtube", true)) {
                    if (!iframes.contains(sourceUrl)) iframes.add(sourceUrl)
                    when {
                        partName.contains("dublaj") -> if (!dubbingIframes.contains(sourceUrl)) dubbingIframes.add(sourceUrl)
                        partName.contains("altyaz") -> if (!subtitleIframes.contains(sourceUrl)) subtitleIframes.add(sourceUrl)
                        else -> {
                            if (!dubbingIframes.contains(sourceUrl)) dubbingIframes.add(sourceUrl)
                            if (!subtitleIframes.contains(sourceUrl)) subtitleIframes.add(sourceUrl)
                        }
                    }
                }
            }
        }
        
        doc.select("iframe").forEach { iframe ->
            val src = iframe.attr("data-src").ifEmpty { iframe.attr("src") }
            if (src.isNotEmpty() && !src.contains("youtube") && !src.contains("google") && !src.contains("vr_set=1")) {
                val formattedSrc = if (src.startsWith("//")) "https:$src" else src
                iframes.add(formattedSrc)
            }
        }
        
        doc.select(".lang-options a, .source-list li").forEach { tab ->
            val langText = tab.text().lowercase()
            val dataSrc = tab.attr("data-src").ifEmpty { tab.attr("data-url") }
            if (dataSrc.isNotEmpty()) {
                val formatted = if (dataSrc.startsWith("//")) "https:$dataSrc" else dataSrc
                if (!iframes.contains(formatted)) iframes.add(formatted)
                
                if (langText.contains("dublaj") || langText.contains("tr")) {
                    if (!dubbingIframes.contains(formatted)) dubbingIframes.add(formatted)
                }
                if (langText.contains("altyaz") || langText.contains("en")) {
                    if (!subtitleIframes.contains(formatted)) subtitleIframes.add(formatted)
                }
            }
        }
        
        if (dubbingIframes.isEmpty() && iframes.isNotEmpty()) dubbingIframes.addAll(iframes)
        if (subtitleIframes.isEmpty() && iframes.isNotEmpty()) subtitleIframes.addAll(iframes)
        
        val languages = mutableListOf<String>()
        if (dubbingIframes.isNotEmpty()) languages.add("Türkçe Dublaj")
        if (subtitleIframes.isNotEmpty()) languages.add("Türkçe Altyazı")

        MovieDetail(
            title = title,
            description = description,
            posterUrl = posterUrl,
            iframes = iframes.distinct(),
            isSeries = false,
            provider = name,
            languages = languages,
            dubbingIframes = dubbingIframes.distinct(),
            subtitleIframes = subtitleIframes.distinct(),
            hasDub = dubbingIframes.isNotEmpty(),
            hasSubtitle = subtitleIframes.isNotEmpty()
        )
    }
}
