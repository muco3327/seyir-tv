package tv.newtv.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import tv.newtv.data.models.*
import tv.newtv.utils.NextJsAesDecryptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class DizillaScraper(private val client: OkHttpClient) : SeriesScraper {
    override val name = "Dizilla"
    private val baseUrl = "https://dizilla.now"
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
        val html = response.body?.string() ?: ""
        return Jsoup.parse(html, formattedUrl)
    }

    private suspend fun fetchAndDecrypt(url: String): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val formattedUrl = if (url.startsWith("http")) url else if (url.startsWith("/")) "$baseUrl$url" else "$baseUrl/$url"
            val request = Request.Builder()
                .url(formattedUrl)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "tr-TR,tr;q=0.9,en-US;q=0.8,en;q=0.7")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                response.close()
                NextJsAesDecryptor.extractAndDecryptFromHtml(html)
            } else {
                response.close()
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseSeriesItem(obj: JSONObject, seen: MutableSet<String>): SeriesItem? {
        val slug = obj.optString("used_slug").ifEmpty { obj.optString("slug") }
        val title = obj.optString("original_title")
            .ifEmpty { obj.optString("culture_title") }
            .ifEmpty { obj.optString("title") }
            .ifEmpty { obj.optString("name") }
            .trim()

        var poster = obj.optString("poster_url")
            .ifEmpty { obj.optString("face_url") }
            .ifEmpty { obj.optString("object_poster_url") }
            .trim()

        if (poster.length < 5 || title.length < 2 || slug.isEmpty()) return null
        if (slug.contains("/dizi-listesi/") || slug.contains("/ulke/") || slug.contains("/kategori/") || slug.contains("/dizi-kategori/")) return null

        if (poster.startsWith("//")) {
            poster = "https:$poster"
        } else if (poster.startsWith("/")) {
            poster = "$baseUrl$poster"
        }

        // Google AMP Proxy'sini kaldır ve doğrudan doğrudan Macellan CDN'e bağla
        poster = poster.replace("https://images-macellan-online.cdn.ampproject.org/i/s/", "https://")
            .replace("http://images-macellan-online.cdn.ampproject.org/i/s/", "https://")

        // Kırık Dizilla / placeholder afişlerini filtrele
        if (poster.contains("dizilla-og.jpg", ignoreCase = true) ||
            poster.contains("placeholder", ignoreCase = true) ||
            poster.contains("no-poster", ignoreCase = true)) {
            return null
        }

        val fullUrl = if (slug.startsWith("http")) slug else if (slug.startsWith("/")) "$baseUrl$slug" else "$baseUrl/$slug"
        if (seen.add(fullUrl)) {
            return SeriesItem(title = title, url = fullUrl, posterUrl = poster, provider = name)
        }
        return null
    }

    private fun parseSeriesFromJsonArray(arr: JSONArray?, seen: MutableSet<String> = mutableSetOf()): List<SeriesItem> {
        val list = mutableListOf<SeriesItem>()
        if (arr == null) return list

        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            parseSeriesItem(obj, seen)?.let { list.add(it) }
        }
        return list
    }

    private fun extractSeriesFromJsonObject(json: JSONObject?, seen: MutableSet<String> = mutableSetOf()): List<SeriesItem> {
        val list = mutableListOf<SeriesItem>()
        if (json == null) return list

        val ignoredKeys = setOf(
            "yearList", "countryList", "yearListSeries", "countryListSeries",
            "getChannels", "getCategoriesByGroup", "contentItem", "content",
            "siteSettings", "adsWithKeys"
        )

        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (ignoredKeys.contains(key)) continue

            val arr = json.optJSONArray(key)
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    parseSeriesItem(obj, seen)?.let { list.add(it) }

                    val seriesObj = obj.optJSONObject("Series") ?: obj.optJSONObject("series")
                    if (seriesObj != null) {
                        val resultArr = seriesObj.optJSONArray("result")
                        if (resultArr != null) {
                            for (j in 0 until resultArr.length()) {
                                val s = resultArr.optJSONObject(j) ?: continue
                                parseSeriesItem(s, seen)?.let { list.add(it) }
                            }
                        }
                    }
                }
            } else {
                val subObj = json.optJSONObject(key)
                if (subObj != null) {
                    val subArr = subObj.optJSONArray("result")
                    if (subArr != null) {
                        for (i in 0 until subArr.length()) {
                            val obj = subArr.optJSONObject(i) ?: continue
                            parseSeriesItem(obj, seen)?.let { list.add(it) }
                        }
                    } else if (key == "RelatedResults") {
                        val rKeys = subObj.keys()
                        while (rKeys.hasNext()) {
                            val rk = rKeys.next()
                            val nested = subObj.optJSONObject(rk)
                            val nestedArr = nested?.optJSONArray("result")
                            if (nestedArr != null) {
                                for (j in 0 until nestedArr.length()) {
                                    val obj = nestedArr.optJSONObject(j) ?: continue
                                    parseSeriesItem(obj, seen)?.let { list.add(it) }
                                }
                            }
                        }
                    }
                }
            }
        }
        return list
    }

    private fun filterJsonByGenres(arrays: List<JSONArray?>, genres: List<String>): List<SeriesItem> {
        val list = mutableListOf<SeriesItem>()
        val seen = mutableSetOf<String>()

        for (arr in arrays) {
            if (arr == null) continue
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val categories = obj.optString("categories")
                val matches = genres.any { g -> categories.contains(g, ignoreCase = true) }
                if (matches) {
                    parseSeriesItem(obj, seen)?.let { list.add(it) }
                }
            }
        }
        return list
    }

    private fun parseSeriesFromDoc(doc: Document): List<SeriesItem> {
        val list = mutableListOf<SeriesItem>()
        val seen = mutableSetOf<String>()

        doc.select("a[href*='dizi/']").forEach { aTag ->
            val href = aTag.attr("href")
            val imgTag = aTag.selectFirst("img") ?: return@forEach
            val rawTitle = aTag.selectFirst("h3")?.text()
                ?: imgTag.attr("alt").ifEmpty { aTag.attr("title") }.ifEmpty { aTag.text() }
            val title = rawTitle.replace(" izle", "", ignoreCase = true).trim()
            var poster = imgTag.attr("src").ifEmpty { imgTag.attr("data-src") }.trim()
            if (poster.startsWith("//")) poster = "https:$poster"
            else if (poster.startsWith("/")) poster = "$baseUrl$poster"

            val fullUrl = if (href.startsWith("http")) href else if (href.startsWith("/")) "$baseUrl$href" else "$baseUrl/$href"

            if (title.isNotEmpty() && !title.equals("dizi", ignoreCase = true) && poster.length > 5 && seen.add(fullUrl)) {
                list.add(SeriesItem(title, fullUrl, poster))
            }
        }
        return list
    }

    override suspend fun getRecent(page: Int): List<SeriesItem> = withContext(Dispatchers.IO) {
        val categories = getCategories()
        categories.flatMap { it.items }.distinctBy { it.url }
    }

    override suspend fun getCategories(page: Int): List<VodCategory<SeriesItem>> = withContext(Dispatchers.IO) {
        val categories = mutableListOf<VodCategory<SeriesItem>>()

        try {
            // Paralel ağ istekleri
            val homeAsync = async { fetchAndDecrypt("$baseUrl/") }
            val diziIzleAsync = async { fetchAndDecrypt("$baseUrl/dizi-izle") }
            val imdbTopAsync = async { fetchAndDecrypt("$baseUrl/imdb-top-100") }
            val diziOnerileriAsync = async { fetchAndDecrypt("$baseUrl/dizi-onerileri") }
            val trendAsync = async { fetchAndDecrypt("$baseUrl/trend") }
            val arsivAsync = async { fetchAndDecrypt("$baseUrl/arsiv") }
            val selcukDiziAsync = async { fetchAndDecrypt("https://selcukflix.com/dizi-izle") }
            val selcukKesfetAsync = async { fetchAndDecrypt("https://selcukflix.com/kesfet") }

            // Platformlar
            val disneyAsync = async { fetchAndDecrypt("$baseUrl/dizi-listesi/disney-plus") }
            val primeAsync = async { fetchAndDecrypt("$baseUrl/dizi-listesi/prime-video") }
            val appleAsync = async { fetchAndDecrypt("$baseUrl/dizi-listesi/apple-tv") }

            // Anime ve K-Drama Özel Koleksiyonları (Doğrudan AES JSON şifre çözümü)
            val animeAsync = async { fetchAndDecrypt("$baseUrl/anime-izle") }
            val kdramaAsync = async { fetchAndDecrypt("$baseUrl/kdrama-izle") }

            val homeJson = homeAsync.await()
            val diziIzleJson = diziIzleAsync.await()
            val imdbJson = imdbTopAsync.await()
            val onerilerJson = diziOnerileriAsync.await()
            val trendJson = trendAsync.await()
            val arsivJson = arsivAsync.await()
            val selcukDiziJson = selcukDiziAsync.await()
            val selcukKesfetJson = selcukKesfetAsync.await()
            val disneyJson = disneyAsync.await()
            val primeJson = primeAsync.await()
            val appleJson = appleAsync.await()
            val animeJson = animeAsync.await()
            val kdramaJson = kdramaAsync.await()

            // 1. Trend Diziler (50+ Dizi)
            val trendList = mutableListOf<SeriesItem>()
            trendList.addAll(parseSeriesFromJsonArray(homeJson?.optJSONArray("getTrendSeries")))
            if (trendJson != null) trendList.addAll(extractSeriesFromJsonObject(trendJson))
            if (diziIzleJson != null) {
                trendList.addAll(parseSeriesFromJsonArray(diziIzleJson.optJSONArray("trendSeries")))
            }
            val distinctTrend = trendList.distinctBy { it.url }
            if (distinctTrend.isNotEmpty()) {
                categories.add(VodCategory("Trend Diziler", distinctTrend))
            }

            // 2. Disney+ Dizileri
            if (disneyJson != null) {
                val disneyItems = extractSeriesFromJsonObject(disneyJson).distinctBy { it.url }
                if (disneyItems.isNotEmpty()) {
                    categories.add(VodCategory("Disney+ Dizileri", disneyItems))
                }
            }

            // 3. Amazon Prime Dizileri
            if (primeJson != null) {
                val primeItems = extractSeriesFromJsonObject(primeJson).distinctBy { it.url }
                if (primeItems.isNotEmpty()) {
                    categories.add(VodCategory("Amazon Prime Dizileri", primeItems))
                }
            }

            // 4. Apple TV+ Dizileri
            if (appleJson != null) {
                val appleItems = extractSeriesFromJsonObject(appleJson).distinctBy { it.url }
                if (appleItems.isNotEmpty()) {
                    categories.add(VodCategory("Apple TV+ Dizileri", appleItems))
                }
            }

            // 5. Anime Dünyası
            if (animeJson != null) {
                val animeItems = extractSeriesFromJsonObject(animeJson).distinctBy { it.url }
                if (animeItems.isNotEmpty()) {
                    categories.add(VodCategory("Anime Dünyası", animeItems))
                }
            }

            // 6. K-Drama (Kore Dizileri)
            if (kdramaJson != null) {
                val kdramaItems = extractSeriesFromJsonObject(kdramaJson).distinctBy { it.url }
                if (kdramaItems.isNotEmpty()) {
                    categories.add(VodCategory("K-Drama (Kore Dizileri)", kdramaItems))
                }
            }

            // 7. IMDb Top 100 Dizi Listesi (100 Dizi)
            val imdbList = mutableListOf<SeriesItem>()
            if (imdbJson != null) {
                imdbList.addAll(extractSeriesFromJsonObject(imdbJson))
            }
            val distinctImdb = imdbList.distinctBy { it.url }
            if (distinctImdb.isNotEmpty()) {
                categories.add(VodCategory("IMDb Top 100 Diziler", distinctImdb))
            }

            // 8. Son Eklenen Diziler & Yeni Bölümler (60+ Dizi)
            val lastList = mutableListOf<SeriesItem>()
            lastList.addAll(parseSeriesFromJsonArray(homeJson?.optJSONArray("getLastSeriesAll")))
            lastList.addAll(parseSeriesFromJsonArray(homeJson?.optJSONArray("getEpisodesOnNewSeries")))
            if (diziIzleJson != null) {
                lastList.addAll(parseSeriesFromJsonArray(diziIzleJson.optJSONArray("getEpisodesOnNewSeries")))
                lastList.addAll(parseSeriesFromJsonArray(diziIzleJson.optJSONArray("listItems")))
            }
            if (selcukKesfetJson != null) {
                lastList.addAll(extractSeriesFromJsonObject(selcukKesfetJson))
            }
            val distinctLast = lastList.distinctBy { it.url }
            if (distinctLast.isNotEmpty()) {
                categories.add(VodCategory("Son Eklenen Diziler", distinctLast))
            }

            // 9. Editörün Dizi Önerileri & Seçkiler (100+ Dizi)
            val recoList = mutableListOf<SeriesItem>()
            if (onerilerJson != null) {
                recoList.addAll(extractSeriesFromJsonObject(onerilerJson))
            }
            val distinctReco = recoList.distinctBy { it.url }
            if (distinctReco.isNotEmpty()) {
                categories.add(VodCategory("Dizi Önerileri & Seçkiler", distinctReco))
            }

            // 10. Popüler Yabancı Dizi Arşivi
            val archiveList = mutableListOf<SeriesItem>()
            if (arsivJson != null) archiveList.addAll(extractSeriesFromJsonObject(arsivJson))
            if (selcukDiziJson != null) archiveList.addAll(extractSeriesFromJsonObject(selcukDiziJson))
            val distinctArchive = archiveList.distinctBy { it.url }
            if (distinctArchive.isNotEmpty()) {
                categories.add(VodCategory("Popüler Yabancı Diziler", distinctArchive))
            }

            // 11. Tür Bazlı Kategoriler (Tüm toplanan dizilerden filtrelenir)
            val allSources = listOfNotNull(homeJson, diziIzleJson, imdbJson, onerilerJson, trendJson, arsivJson, selcukDiziJson, disneyJson, primeJson, appleJson)
            val allArrays = mutableListOf<JSONArray>()
            for (j in allSources) {
                val keys = j.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val a = j.optJSONArray(k)
                    if (a != null) allArrays.add(a)
                }
            }

            // Aksiyon & Suç
            val actionSeries = filterJsonByGenres(allArrays, listOf("Aksiyon", "Suç", "Macera", "Action", "Crime"))
            if (actionSeries.isNotEmpty()) {
                categories.add(VodCategory("Aksiyon & Suç Dizileri", actionSeries))
            }

            // Bilim Kurgu & Fantastik
            val sciFiSeries = filterJsonByGenres(allArrays, listOf("Bilim-Kurgu", "Fantastik", "Sci-Fi", "Fantasy"))
            if (sciFiSeries.isNotEmpty()) {
                categories.add(VodCategory("Bilim Kurgu & Fantastik", sciFiSeries))
            }

            // Dram & Gizem
            val dramaSeries = filterJsonByGenres(allArrays, listOf("Dram", "Gizem", "Drama", "Mystery"))
            if (dramaSeries.isNotEmpty()) {
                categories.add(VodCategory("Dram & Gizem", dramaSeries))
            }

            // Komedi & Animasyon
            val comedySeries = filterJsonByGenres(allArrays, listOf("Komedi", "Animasyon", "Comedy", "Animation"))
            if (comedySeries.isNotEmpty()) {
                categories.add(VodCategory("Komedi & Animasyon", comedySeries))
            }

            // Yedek: Eğer kategoriler boş kaldıysa HTML'den dene
            if (categories.isEmpty()) {
                val homeDoc = fetchDocument(baseUrl)
                val homeItems = parseSeriesFromDoc(homeDoc)
                if (homeItems.isNotEmpty()) {
                    categories.add(VodCategory("Trend Diziler", homeItems))
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        categories
    }

    override suspend fun search(query: String, page: Int): List<SeriesItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<SeriesItem>()
        val seen = mutableSetOf<String>()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext items

        try {
            val encodedQuery = java.net.URLEncoder.encode(trimmed, "UTF-8")
            val searchUrl = "$baseUrl/api/bg/searchContent?searchterm=$encodedQuery"
            val jsonPayload = org.json.JSONObject().put("searchterm", trimmed).toString()
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val requestBody = jsonPayload.toRequestBody(mediaType)

            val request = Request.Builder()
                .url(searchUrl)
                .post(requestBody)
                .header("User-Agent", userAgent)
                .header("Referer", "$baseUrl/")
                .header("Accept", "application/json, text/plain, */*")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: ""
                val jsonResponse = org.json.JSONObject(bodyStr)
                val encryptedResponse = jsonResponse.optString("response")
                if (encryptedResponse.isNotEmpty()) {
                    val decrypted = NextJsAesDecryptor.decrypt(encryptedResponse)
                    val resultArr = decrypted?.optJSONArray("result")
                    if (resultArr != null) {
                        for (i in 0 until resultArr.length()) {
                            val obj = resultArr.optJSONObject(i) ?: continue
                            val slug = obj.optString("used_slug").ifEmpty { obj.optString("slug") }
                            val title = obj.optString("object_name").ifEmpty { obj.optString("title") }.trim()
                            var poster = obj.optString("object_poster_url").ifEmpty { obj.optString("poster_url") }
                            poster = tv.newtv.utils.PosterUrlUtils.normalize(poster, name, false)
                            val fullUrl = if (slug.startsWith("http")) slug else if (slug.startsWith("/")) "$baseUrl$slug" else "$baseUrl/$slug"

                            if (title.isNotEmpty() && slug.isNotEmpty() && seen.add(fullUrl)) {
                                items.add(SeriesItem(title = title, url = fullUrl, posterUrl = poster, provider = name))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback: Yerel listedeki dizilerden arama terimini içerenleri de ekle
        if (items.isEmpty()) {
            val localSeries = getRecent()
            val matched = localSeries.filter { it.title.contains(trimmed, ignoreCase = true) }
            items.addAll(matched.filter { seen.add(it.url) })
        }

        items
    }

    override suspend fun getSeriesDetail(seriesUrl: String): SeriesDetail = withContext(Dispatchers.IO) {
        val doc = fetchDocument(seriesUrl)
        val html = doc.html()

        var title = doc.selectFirst("h1")?.text()?.replace(" izle", "", ignoreCase = true)?.trim() ?: ""
        var description = doc.selectFirst("meta[name=description]")?.attr("content") ?: ""
        var poster = doc.selectFirst("meta[property='og:image']")?.attr("content")?.trim()
            ?: doc.selectFirst(".dizi-afis img, img[src*='poster']")?.let { it.attr("data-src").ifEmpty { it.attr("src") } }
            ?: ""
        val seasonsMap = sortedMapOf<Int, MutableList<Episode>>()

        // 1. Next.js şifreli verisini çözerek sezon ve bölümleri eksiksiz al
        val decrypted = NextJsAesDecryptor.extractAndDecryptFromHtml(html)
        if (decrypted != null) {
            val contentItem = decrypted.optJSONObject("contentItem")
            if (contentItem != null) {
                val itemTitle = contentItem.optString("original_title").ifEmpty { contentItem.optString("culture_title") }
                if (itemTitle.isNotEmpty()) title = itemTitle
                val itemDesc = contentItem.optString("description")
                if (itemDesc.isNotEmpty()) description = itemDesc
                val itemPoster = contentItem.optString("poster_url").ifEmpty { contentItem.optString("face_url") }
                if (itemPoster.isNotEmpty()) poster = itemPoster
            }

            val related = decrypted.optJSONObject("RelatedResults")
            val seasonData = related?.optJSONObject("getSerieSeasonAndEpisodes")?.optJSONArray("result")

            seasonData?.let { sList ->
                for (i in 0 until sList.length()) {
                    val sObj = sList.optJSONObject(i) ?: continue
                    val sNum = sObj.optInt("season_no", i + 1)
                    val epList = sObj.optJSONArray("episodes") ?: continue

                    val episodeItems = seasonsMap.getOrPut(sNum) { mutableListOf() }
                    for (j in 0 until epList.length()) {
                        val epObj = epList.optJSONObject(j) ?: continue
                        val epNum = epObj.optInt("episode_no", j + 1)
                        val epTitle = epObj.optString("episode_text").ifEmpty { "$epNum. Bölüm" }
                        val epSlug = epObj.optString("used_slug")
                        val epUrl = if (epSlug.startsWith("http")) epSlug else if (epSlug.startsWith("/")) "$baseUrl$epSlug" else "$baseUrl/$epSlug"
                        episodeItems.add(Episode(epNum, epTitle, epUrl))
                    }
                }
            }
        }

        // 2. Eğer şifreli veriden sezon gelmediyse HTML DOM linklerinden derle
        if (seasonsMap.isEmpty()) {
            val epRegex = Regex("""/([a-zA-Z0-9_\-]+)-(\d+)-sezon-(\d+)-bolum""")
            doc.select("a[href*='-sezon-'][href*='-bolum']").forEach { aTag ->
                val href = aTag.attr("href")
                val match = epRegex.find(href)
                if (match != null) {
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
        }

        val seasons = seasonsMap.map { (sNum, epList) ->
            Season(seasonNumber = sNum, name = "$sNum. Sezon", episodes = epList)
        }

        val cleanPoster = tv.newtv.utils.PosterUrlUtils.normalize(poster, name, false)
        SeriesDetail(title, description, cleanPoster, seasons, provider = name, languages = listOf("Türkçe Dublaj", "Türkçe Altyazı"))
    }

    override suspend fun getEpisodeSources(episodeUrl: String): EpisodeSources = withContext(Dispatchers.IO) {
        val doc = fetchDocument(episodeUrl)
        val html = doc.html()
        val dubbingIframes = mutableListOf<String>()
        val subtitleIframes = mutableListOf<String>()

        // 1. Şifreli Next.js verisinden direkt sunucu iframelerini çek
        val decrypted = NextJsAesDecryptor.extractAndDecryptFromHtml(html)
        if (decrypted != null) {
            val related = decrypted.optJSONObject("RelatedResults")
            val sources = related?.optJSONObject("getEpisodeSources")?.optJSONArray("result")

            sources?.let { sList ->
                val iframeSrcRegex = Regex("""src\s*=\s*["']([^"']+)["']""")
                for (i in 0 until sList.length()) {
                    val sObj = sList.optJSONObject(i) ?: continue
                    val langName = sObj.optString("language_name").lowercase()
                    val srcName = sObj.optString("source_name").lowercase()
                    val title = sObj.optString("title").lowercase()
                    val type = sObj.optString("type").lowercase()
                    val langType = sObj.optString("language_type").lowercase()

                    val isDub = langName.contains("dublaj") || title.contains("dublaj") || srcName.contains("dublaj") || type.contains("dublaj") || langType.contains("dublaj")
                    val isSub = langName.contains("altyaz") || title.contains("altyaz") || srcName.contains("altyaz") || type.contains("altyaz") || langType.contains("altyaz") || title.contains("sub")

                    val extractedUrls = mutableListOf<String>()
                    val directUrl = sObj.optString("source_url").ifEmpty { sObj.optString("url") }
                    if (directUrl.isNotEmpty() && !directUrl.contains("youtube") && !directUrl.contains("google")) {
                        val formatted = if (directUrl.startsWith("//")) "https:$directUrl" else directUrl
                        extractedUrls.add(formatted)
                    }

                    val content = sObj.optString("source_content")
                    val match = iframeSrcRegex.find(content)
                    if (match != null) {
                        var src = match.groupValues[1]
                        if (src.startsWith("//")) src = "https:$src"
                        if (!src.contains("youtube") && !src.contains("google") && !extractedUrls.contains(src)) {
                            extractedUrls.add(src)
                        }
                    }

                    for (src in extractedUrls) {
                        if (isDub) {
                            if (!dubbingIframes.contains(src)) dubbingIframes.add(src)
                        }
                        if (isSub) {
                            if (!subtitleIframes.contains(src)) subtitleIframes.add(src)
                        }
                        if (!isDub && !isSub) {
                            if (!dubbingIframes.contains(src)) dubbingIframes.add(src)
                            if (!subtitleIframes.contains(src)) subtitleIframes.add(src)
                        }
                    }
                }
            }
        }

        // 2. DOM iframe'lerini de kontrol et
        doc.select("iframe").forEach { iframe ->
            val src = iframe.attr("src")
            if (src.isNotEmpty() && !src.contains("youtube") && !src.contains("google")) {
                val formattedSrc = if (src.startsWith("//")) "https:$src" else src
                if (!subtitleIframes.contains(formattedSrc) && !dubbingIframes.contains(formattedSrc)) {
                    subtitleIframes.add(formattedSrc)
                }
            }
        }

        val limitedDub = limitHostIframes(dubbingIframes)
        val limitedSub = limitHostIframes(subtitleIframes)
        val all = (limitedDub + limitedSub).distinct()
        EpisodeSources(iframes = all, dubbingIframes = limitedDub, subtitleIframes = limitedSub)
    }

    override suspend fun getEpisodeIframes(episodeUrl: String): List<String> = withContext(Dispatchers.IO) {
        getEpisodeSources(episodeUrl).iframes
    }

    /**
     * Aynı host'tan gelen iframe sayısını sınırlar (maks 2 per host).
     */
    private fun limitHostIframes(urls: List<String>, maxPerHost: Int = 2): List<String> {
        val hostCount = mutableMapOf<String, Int>()
        return urls.filter { url ->
            val host = runCatching { java.net.URI(url).host?.lowercase() ?: "" }.getOrDefault("")
            val count = hostCount.getOrDefault(host, 0)
            if (count < maxPerHost) {
                hostCount[host] = count + 1
                true
            } else false
        }
    }
}
