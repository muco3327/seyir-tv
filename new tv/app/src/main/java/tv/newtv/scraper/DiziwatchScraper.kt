package tv.newtv.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.FormBody
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import tv.newtv.data.models.Episode
import tv.newtv.data.models.Season
import tv.newtv.data.models.SeriesDetail
import tv.newtv.data.models.SeriesItem
import tv.newtv.data.models.VodCategory

class DiziwatchScraper(private val client: OkHttpClient) : SeriesScraper {
    override val name = "Diziwatch"
    private val baseUrl = "https://diziwatch.ac"
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

    private fun parseSeriesFromDoc(doc: Document): List<SeriesItem> {
        val items = mutableListOf<SeriesItem>()
        val seen = mutableSetOf<String>()
        
        doc.select("a[href*='/dizi/']:has(img), .dizi-item a[href], .item a[href], article a[href], .post-item a[href]").forEach { aTag ->
            val element = aTag.parent() ?: aTag
            val img = aTag.selectFirst("img") ?: element.selectFirst("img")
            val title = aTag.attr("title")
                .ifEmpty { img?.attr("alt") ?: "" }
                .ifEmpty { aTag.selectFirst("h2, h3")?.text() ?: "" }
                .trim()
            val rawUrl = aTag.attr("href")
            val url = rawUrl.replace(Regex("/sezon-\\d+/bolum-\\d+/?$"), "")
            
            var posterUrl = tv.newtv.utils.PosterUrlUtils.extractImgUrl(aTag, baseUrl)
            if (posterUrl.isEmpty()) {
                posterUrl = tv.newtv.utils.PosterUrlUtils.extractImgUrl(element, baseUrl)
            }
            posterUrl = tv.newtv.utils.PosterUrlUtils.normalize(posterUrl, name, false)
            
            val fullUrl = if (url.startsWith("http")) url else "$baseUrl$url"
            
            if (title.isNotEmpty() && url.isNotEmpty() && seen.add(fullUrl)) {
                items.add(SeriesItem(title = title, url = fullUrl, posterUrl = posterUrl, provider = name))
            }
        }
        return items
    }

    override suspend fun getCategories(page: Int): List<VodCategory<SeriesItem>> = withContext(Dispatchers.IO) {
        val categories = mutableListOf<VodCategory<SeriesItem>>()
        try {
            // Diziwatch'ın HTML page= bağlantısı CDN'de hep ilk sayfayı döndürüyor.
            // Sitenin kendi arama indeksini Türkçe harf ve ikili harf bölümleriyle
            // sırayla gezerek arşivin erişilebilir tamamını topluyoruz.
            val key = ARCHIVE_QUERY_KEYS.getOrNull(page - 1) ?: return@withContext emptyList()
            val body = FormBody.Builder().add("searchterm", key).build()
            val request = Request.Builder()
                .url("$baseUrl/bg/searchcontent")
                .header("User-Agent", userAgent)
                .header("Referer", "$baseUrl/dizi-arsivi")
                .post(body)
                .build()
            val result = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                JSONObject(response.body?.string().orEmpty())
                    .optJSONObject("data")?.optJSONArray("result")
            }
            val items = buildList {
                if (result != null) for (i in 0 until result.length()) {
                    val obj = result.optJSONObject(i) ?: continue
                    if (!obj.optString("used_type").equals("Series", ignoreCase = true)) continue
                    val slug = obj.optString("used_slug").trimStart('/')
                    val title = obj.optString("object_name").trim()
                    if (slug.isBlank() || title.isBlank()) continue
                    val poster = obj.optString("object_poster_url")
                        .replace("https://images-macellan-online.cdn.ampproject.org/i/s/", "https://")
                    add(SeriesItem(title, "$baseUrl/$slug", poster, provider = name))
                }
            }.distinctBy { it.url }
            if (items.isNotEmpty()) {
                categories.add(VodCategory("Dizi Arşivi ($key)", items))
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
        categories
    }

    private companion object {
        val ARCHIVE_QUERY_KEYS: List<String> = run {
            val letters = "abcçdefgğhıijklmnoöprsştuüvyz".map(Char::toString)
            letters + letters.flatMap { first -> letters.map { second -> first + second } }
        }
    }

    override suspend fun search(query: String, page: Int): List<SeriesItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<SeriesItem>()
        try {
            val searchUrl = "$baseUrl/page/$page/?s=${java.net.URLEncoder.encode(query, "UTF-8")}"
            val doc = fetchDocument(searchUrl)
            items.addAll(parseSeriesFromDoc(doc))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        items
    }

    override suspend fun getSeriesDetail(seriesUrl: String): SeriesDetail = withContext(Dispatchers.IO) {
        val doc = fetchDocument(seriesUrl)
        
        val title = doc.selectFirst("h1")?.text() ?: ""
        val description = doc.selectFirst(".summary, .ozet, p")?.text() ?: ""
        
        var posterUrl = doc.selectFirst("meta[property='og:image']")?.attr("content")?.trim() ?: ""
        if (posterUrl.isEmpty() || posterUrl.startsWith("data:image")) {
            val img = doc.selectFirst(".poster img, .series-poster img, .image img")
            if (img != null) {
                posterUrl = tv.newtv.utils.PosterUrlUtils.extractImgUrl(img, baseUrl)
            }
        }
        posterUrl = tv.newtv.utils.PosterUrlUtils.normalize(posterUrl, name, false)
        
        val seasonsMap = sortedMapOf<Int, MutableList<Episode>>()
        
        doc.select(".episodes-list li, .sezon-listesi li").forEach { epLi ->
            val aTag = epLi.selectFirst("a") ?: return@forEach
            val epTitle = aTag.text()
            val epUrl = aTag.attr("href")
            
            // "1. Sezon 5. Bölüm" veya benzeri textten parse et
            val sMatch = Regex("""(?:Sezon|S)\s*(\d+)""").find(epTitle)
            val eMatch = Regex("""(?:Bölüm|B|E)\s*(\d+)""").find(epTitle)
            
            val sNum = sMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val epNum = eMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
            
            val formattedUrl = if (epUrl.startsWith("http")) epUrl else "$baseUrl$epUrl"
            
            val episodeItems = seasonsMap.getOrPut(sNum) { mutableListOf() }
            if (episodeItems.none { it.url == formattedUrl }) {
                episodeItems.add(Episode(epNum, epTitle.ifEmpty { "$epNum. Bölüm" }, formattedUrl))
            }
        }
        
        val parsedSeasons = seasonsMap.map { (sNum, epList) ->
            Season(seasonNumber = sNum, name = "$sNum. Sezon", episodes = epList.sortedBy { it.episodeNumber })
        }

        SeriesDetail(
            title = title,
            description = description,
            posterUrl = posterUrl,
            seasons = parsedSeasons,
            provider = name,
            hasDub = true,
            hasSubtitle = true
        )
    }

    override suspend fun getEpisodeSources(episodeUrl: String): tv.newtv.data.models.EpisodeSources = withContext(Dispatchers.IO) {
        val doc = fetchDocument(episodeUrl)
        
        val iframes = mutableListOf<String>()
        val dubbingIframes = mutableListOf<String>()
        val subtitleIframes = mutableListOf<String>()
        
        doc.select("iframe").forEach { iframe ->
            val src = iframe.attr("data-src").ifEmpty { iframe.attr("src") }
            if (src.isNotEmpty() && !src.contains("youtube") && !src.contains("google")) {
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
        
        val all = (dubbingIframes + subtitleIframes).distinct()
        tv.newtv.data.models.EpisodeSources(
            iframes = if (all.isNotEmpty()) all else iframes,
            dubbingIframes = dubbingIframes.ifEmpty { iframes },
            subtitleIframes = subtitleIframes.ifEmpty { iframes },
            hasDub = dubbingIframes.isNotEmpty(),
            hasSubtitle = subtitleIframes.isNotEmpty()
        )
    }

    override suspend fun getEpisodeIframes(episodeUrl: String): List<String> = withContext(Dispatchers.IO) {
        getEpisodeSources(episodeUrl).iframes
    }
}
