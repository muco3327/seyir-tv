package tv.newtv.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tv.newtv.data.models.Episode
import tv.newtv.data.models.EpisodeSources
import tv.newtv.data.models.Season
import tv.newtv.data.models.SeriesDetail
import tv.newtv.data.models.SeriesItem
import tv.newtv.data.models.VodCategory
import java.net.URLEncoder

class SezonlukDiziScraper(private val client: OkHttpClient) : SeriesScraper {
    override val name: String = "SezonlukDizi"
    private val baseUrl = "https://sezonlukdizi.cc"
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

    private fun parseSeriesItem(element: Element, seen: MutableSet<String>): SeriesItem? {
        val linkElem = if (element.tagName().equals("a", ignoreCase = true)) element else (element.selectFirst("a[href*='/diziler/'], a[href*='/dizi/'], a.image, a[href*='.html']") ?: return null)
        val href = linkElem.attr("href")
        if (href.isEmpty() || href == "#") return null

        val fullUrl = if (href.startsWith("http")) href else "$baseUrl$href"
        if (!seen.add(fullUrl)) return null

        val title = element.selectFirst(".header, .box-title .title, .description, h2, h3")?.text()?.trim()
            ?: linkElem.attr("title").replace(" izle", "").trim()
        if (title.length < 2) return null

        var poster = tv.newtv.utils.PosterUrlUtils.extractImgUrl(element, baseUrl)
        poster = tv.newtv.utils.PosterUrlUtils.normalize(poster, name, false)

        return SeriesItem(
            title = title,
            url = fullUrl,
            posterUrl = poster,
            provider = name,
            languages = listOf("TR Dublaj", "TR Altyazı")
        )
    }

    override suspend fun getRecent(page: Int): List<SeriesItem> = withContext(Dispatchers.IO) {
        val doc = fetchDocument(baseUrl) ?: return@withContext emptyList()
        val seen = mutableSetOf<String>()
        val items = mutableListOf<SeriesItem>()

        val cards = doc.select("#soncikan .column, .ui.card")
        for (card in cards) {
            val linkElem = card.selectFirst("a[href*='.html']") ?: continue
            val href = linkElem.attr("href")
            val title = card.selectFirst(".box-title .title, .header")?.text()?.trim()
                ?: linkElem.attr("title").replace(" izle", "").trim()
            var poster = tv.newtv.utils.PosterUrlUtils.extractImgUrl(card, baseUrl)
            poster = tv.newtv.utils.PosterUrlUtils.normalize(poster, name, false)

            if (title.isNotEmpty() && href.isNotEmpty()) {
                val slug = href.substringBefore("/").ifEmpty { href.substringAfter("/").substringBefore("/") }
                val seriesUrl = "$baseUrl/diziler/$slug.html"
                if (seen.add(seriesUrl)) {
                    items.add(
                        SeriesItem(
                            title = title,
                            url = seriesUrl,
                            posterUrl = poster,
                            provider = name,
                            languages = listOf("TR Dublaj", "TR Altyazı")
                        )
                    )
                }
            }
        }
        items
    }

    override suspend fun getCategories(page: Int): List<VodCategory<SeriesItem>> = withContext(Dispatchers.IO) {
        val catDefs = listOf(
            "En Popüler Diziler" to listOf(
                "$baseUrl/diziler.asp?siralama_tipi=imdb&siralama_turu=desc&sayfa=1",
                "$baseUrl/diziler.asp?siralama_tipi=imdb&siralama_turu=desc&sayfa=2",
                "$baseUrl/diziler.asp?siralama_tipi=imdb&siralama_turu=desc&sayfa=3"
            ),
            "Son Eklenen Diziler" to listOf(
                "$baseUrl/diziler.asp?siralama_tipi=id&siralama_turu=desc&sayfa=1",
                "$baseUrl/diziler.asp?siralama_tipi=id&siralama_turu=desc&sayfa=2",
                "$baseUrl/diziler.asp?siralama_tipi=id&siralama_turu=desc&sayfa=3"
            ),
            "Aksiyon & Macera" to listOf(
                "$baseUrl/diziler.asp?tur=aksiyon&sayfa=1",
                "$baseUrl/diziler.asp?tur=aksiyon&sayfa=2",
                "$baseUrl/diziler.asp?tur=aksiyon&sayfa=3"
            ),
            "Bilim Kurgu & Fantastik" to listOf(
                "$baseUrl/diziler.asp?tur=bilimkurgu&sayfa=1",
                "$baseUrl/diziler.asp?tur=bilimkurgu&sayfa=2",
                "$baseUrl/diziler.asp?tur=bilimkurgu&sayfa=3"
            ),
            "Suç & Polisiye" to listOf(
                "$baseUrl/diziler.asp?tur=suc&sayfa=1",
                "$baseUrl/diziler.asp?tur=suc&sayfa=2",
                "$baseUrl/diziler.asp?tur=suc&sayfa=3"
            ),
            "Komedi Dizileri" to listOf(
                "$baseUrl/diziler.asp?tur=komedi&sayfa=1",
                "$baseUrl/diziler.asp?tur=komedi&sayfa=2",
                "$baseUrl/diziler.asp?tur=komedi&sayfa=3"
            ),
            "Dram & Romantik" to listOf(
                "$baseUrl/diziler.asp?tur=dram&sayfa=1",
                "$baseUrl/diziler.asp?tur=dram&sayfa=2",
                "$baseUrl/diziler.asp?tur=dram&sayfa=3"
            ),
            "Korku & Gerilim" to listOf(
                "$baseUrl/diziler.asp?tur=korku&sayfa=1",
                "$baseUrl/diziler.asp?tur=korku&sayfa=2"
            ),
            "Animasyon & Anime" to listOf(
                "$baseUrl/diziler.asp?kat=4&sayfa=1",
                "$baseUrl/diziler.asp?kat=4&sayfa=2"
            ),
            "Mini Diziler" to listOf(
                "$baseUrl/diziler.asp?tur=mini&sayfa=1",
                "$baseUrl/diziler.asp?tur=mini&sayfa=2"
            )
        )

        catDefs.map { (catTitle, pageUrls) ->
            async {
                try {
                    val seen = mutableSetOf<String>()
                    val items = mutableListOf<SeriesItem>()
                    for (catUrl in pageUrls) {
                        val doc = fetchDocument(catUrl) ?: continue
                        val cards = doc.select(".afis .column, #filtreSonuclari .column, a.column")
                        for (card in cards) {
                            parseSeriesItem(card, seen)?.let { items.add(it) }
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

    override suspend fun search(query: String, page: Int): List<SeriesItem> = withContext(Dispatchers.IO) {
        val seen = mutableSetOf<String>()
        val items = mutableListOf<SeriesItem>()
        try {
            val encoded = URLEncoder.encode(query, "windows-1254")
            val doc = fetchDocument("$baseUrl/diziler.asp?adi=$encoded")
            if (doc != null) {
                val cards = doc.select(".afis .column, #filtreSonuclari .column, a.column")
                for (card in cards) {
                    parseSeriesItem(card, seen)?.let { items.add(it) }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        items
    }

    override suspend fun getSeriesDetail(seriesUrl: String): SeriesDetail = withContext(Dispatchers.IO) {
        val doc = fetchDocument(seriesUrl) ?: throw Exception("Dizi detayları yüklenemedi: $seriesUrl")

        val title = doc.selectFirst("h1, meta[property='og:title']")?.let {
            if (it.hasAttr("content")) it.attr("content") else it.text()
        }?.replace(" izle", "")?.replace(" - Sezonluk Dizi", "")?.trim() ?: "Dizi"

        val description = doc.selectFirst("meta[name='description'], meta[property='og:description']")?.attr("content")?.trim()
            ?: doc.selectFirst(".description, .ozet")?.text()?.trim()
            ?: ""

        var poster = doc.selectFirst("meta[property='og:image']")?.attr("content")?.trim() ?: ""
        if (poster.isEmpty() || poster.startsWith("data:image")) {
            val img = doc.selectFirst(".ui.image img, img.golgever, .image img")
            if (img != null) {
                poster = tv.newtv.utils.PosterUrlUtils.extractImgUrl(img, baseUrl)
            }
        }
        poster = tv.newtv.utils.PosterUrlUtils.normalize(poster, name, false)

        // Find slug for episodes page: /bolumler/{slug}.html
        val slug = seriesUrl.substringAfterLast("/").substringBefore(".html")
        val bolumlerUrl = "$baseUrl/bolumler/$slug.html"
        val episodesDoc = fetchDocument(bolumlerUrl) ?: doc

        // Parse all episode links
        val episodesBySeason = mutableMapOf<Int, MutableList<Episode>>()
        val epLinks = episodesDoc.select("a[href*='-sezon-'][href*='-bolum.html']")

        for (epLink in epLinks) {
            val href = epLink.attr("href")
            val fullEpUrl = if (href.startsWith("http")) href else "$baseUrl$href"
            val text = epLink.text().trim().ifEmpty { epLink.attr("title").replace(" izle", "") }

            // Extract season and episode number: /slug/{s}-sezon-{e}-bolum.html
            val regex = Regex("""(\d+)-sezon-(\d+)-bolum""")
            val match = regex.find(href)
            val seasonNum = match?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val epNum = match?.groupValues?.get(2)?.toIntOrNull() ?: 1

            val epName = if (text.isNotEmpty()) text else "$seasonNum. Sezon $epNum. Bölüm"

            val list = episodesBySeason.getOrPut(seasonNum) { mutableListOf() }
            if (list.none { it.episodeNumber == epNum }) {
                list.add(
                    Episode(
                        episodeNumber = epNum,
                        name = epName,
                        url = fullEpUrl,
                        languages = listOf("TR Dublaj", "TR Altyazı")
                    )
                )
            }
        }

        // Sort seasons and episodes
        val seasons = episodesBySeason.keys.sorted().map { sNum ->
            val eps = episodesBySeason[sNum]?.sortedBy { it.episodeNumber } ?: emptyList()
            Season(
                seasonNumber = sNum,
                name = "$sNum. Sezon",
                episodes = eps
            )
        }

        SeriesDetail(
            title = title,
            description = description,
            posterUrl = poster,
            seasons = seasons,
            provider = name,
            languages = listOf("Türkçe Dublaj", "Türkçe Altyazı")
        )
    }

    override suspend fun getEpisodeSources(episodeUrl: String): EpisodeSources = withContext(Dispatchers.IO) {
        val dubbingIframes = mutableListOf<String>()
        val subtitleIframes = mutableListOf<String>()
        try {
            val doc = fetchDocument(episodeUrl) ?: return@withContext EpisodeSources()

            // Find episode id (bid)
            val bid = doc.selectFirst("#dilsec")?.attr("data-id")
                ?: doc.selectFirst("[bid]")?.attr("bid")
                ?: doc.selectFirst("[data-id]")?.attr("data-id")
                ?: ""

            if (bid.isNotEmpty()) {
                // 0 = Türkçe Dublaj, 1 = Türkçe Altyazı
                for (dil in listOf(0, 1)) {
                    val targetList = if (dil == 0) dubbingIframes else subtitleIframes
                    val formBody = FormBody.Builder()
                        .add("bid", bid)
                        .add("dil", dil.toString())
                        .build()
                    val req = Request.Builder()
                        .url("$baseUrl/ajax/dataAlternatif22.asp")
                        .post(formBody)
                        .header("User-Agent", userAgent)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Referer", episodeUrl)
                        .build()
                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val respText = resp.body?.string() ?: ""
                        resp.close()
                        try {
                            val json = JSONObject(respText)
                            if (json.optString("status") == "success") {
                                val dataArr = json.optJSONArray("data")
                                if (dataArr != null) {
                                    for (i in 0 until dataArr.length()) {
                                        val altObj = dataArr.getJSONObject(i)
                                        val altId = altObj.optString("id")
                                        if (altId.isNotEmpty()) {
                                            val embedBody = FormBody.Builder()
                                                .add("id", altId)
                                                .build()
                                            val embedReq = Request.Builder()
                                                .url("$baseUrl/ajax/dataEmbed22.asp")
                                                .post(embedBody)
                                                .header("User-Agent", userAgent)
                                                .header("X-Requested-With", "XMLHttpRequest")
                                                .header("Referer", episodeUrl)
                                                .build()
                                            val embedResp = client.newCall(embedReq).execute()
                                            if (embedResp.isSuccessful) {
                                                val embedHtml = embedResp.body?.string() ?: ""
                                                embedResp.close()
                                                val embedDoc = Jsoup.parse(embedHtml, baseUrl)
                                                val ifr = embedDoc.selectFirst("iframe")?.attr("src") ?: ""
                                                if (ifr.isNotEmpty() && !ifr.contains("reCAPTCHA")) {
                                                    val fullIfr = if (ifr.startsWith("//")) "https:$ifr" else if (ifr.startsWith("/")) "$baseUrl$ifr" else ifr
                                                    if (!targetList.contains(fullIfr)) targetList.add(fullIfr)
                                                }
                                            } else {
                                                embedResp.close()
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) { android.util.Log.d("SezonlukDiziScraper", "iframe extract failed", e) }
                    } else {
                        resp.close()
                    }
                }
            }

            // Sayfadaki statik iframe'ler
            for (ifr in doc.select("iframe[src]")) {
                val src = ifr.attr("src")
                if (src.isNotEmpty() && !src.contains("google") && !src.contains("youtube")) {
                    val fullSrc = if (src.startsWith("//")) "https:$src" else src
                    if (!subtitleIframes.contains(fullSrc) && !dubbingIframes.contains(fullSrc)) {
                        subtitleIframes.add(fullSrc)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val all = (dubbingIframes + subtitleIframes).distinct()
        EpisodeSources(iframes = all, dubbingIframes = dubbingIframes, subtitleIframes = subtitleIframes)
    }

    override suspend fun getEpisodeIframes(episodeUrl: String): List<String> = withContext(Dispatchers.IO) {
        val sources = getEpisodeSources(episodeUrl)
        sources.iframes
    }
}
