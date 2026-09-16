from pathlib import Path

p = Path(r"c:\tv\new tv\app\src\main\java\tv\newtv\ui\viewmodel\TvViewModels.kt")
text = p.read_text(encoding="utf-8")

# Find the location:
# After "    fun setSeriesCatalogPage(page: Int) {\n        _seriesCatalogPage.value = page.coerceIn(1, _seriesCatalogPageCount.value)\n        publishSeriesPage()\n    }\n"
# and before "            providerManager.hdfilmcehennemi.getEpisodeSources(url)"

old_snippet = """    fun setSeriesCatalogPage(page: Int) {
        _seriesCatalogPage.value = page.coerceIn(1, _seriesCatalogPageCount.value)
        publishSeriesPage()
    }

    private fun publishMoviePage(reset: Boolean = false) {
            providerManager.hdfilmcehennemi.getEpisodeSources(url)"""

new_snippet = """    fun setSeriesCatalogPage(page: Int) {
        _seriesCatalogPage.value = page.coerceIn(1, _seriesCatalogPageCount.value)
        publishSeriesPage()
    }

    fun clearError() {
        _error.value = null
    }

    private fun publishMoviePage(reset: Boolean = false) {
        val pageCount = maxOf(1, (allMovieCatalog.size + VodCatalogEngine.PAGE_SIZE - 1) / VodCatalogEngine.PAGE_SIZE)
        _movieCatalogPageCount.value = pageCount
        if (reset) _movieCatalogPage.value = 1
        val page = _movieCatalogPage.value.coerceIn(1, pageCount)
        val from = (page - 1) * VodCatalogEngine.PAGE_SIZE
        val items = allMovieCatalog.drop(from).take(VodCatalogEngine.PAGE_SIZE)
        _movies.value = if (items.isEmpty()) emptyList() else listOf(tv.newtv.data.models.VodCategory("Tüm Filmler", items))
    }

    private fun publishSeriesPage(reset: Boolean = false) {
        val pageCount = maxOf(1, (allSeriesCatalog.size + VodCatalogEngine.PAGE_SIZE - 1) / VodCatalogEngine.PAGE_SIZE)
        _seriesCatalogPageCount.value = pageCount
        if (reset) _seriesCatalogPage.value = 1
        val page = _seriesCatalogPage.value.coerceIn(1, pageCount)
        val from = (page - 1) * VodCatalogEngine.PAGE_SIZE
        val items = allSeriesCatalog.drop(from).take(VodCatalogEngine.PAGE_SIZE)
        _series.value = if (items.isEmpty()) emptyList() else listOf(tv.newtv.data.models.VodCategory("Tüm Diziler", items))
    }

    fun openMovie(item: MovieItem) = load {
        _seriesDetail.value = null
        val targetMovie = item.copy(provider = item.provider.ifBlank {
            providerManager.getMovieScraperForUrl(item.url).name
        })
        originalMovie = targetMovie

        // 1. Filmin kendi sağlayıcısından detayını çekmeyi dene
        val primaryScraper = providerManager.getMovieScraperForUrl(targetMovie.url)
        var detail = runCatching { primaryScraper.getMovieDetail(targetMovie.url) }.getOrNull()

        // 2. Kendi sağlayıcısında sorun varsa veya oynatıcı iframe'i boşsa diğer sağlayıcılardan dene
        if (detail == null || (detail.iframes.isEmpty() && detail.dubbingIframes.isEmpty() && detail.subtitleIframes.isEmpty())) {
            val candidates = catalogEngine.movieSources(targetMovie.title).filter { it.url != targetMovie.url }
            for (candidate in candidates) {
                val candidateScraper = providerManager.getMovieScraperForUrl(candidate.url)
                val candidateDetail = runCatching { candidateScraper.getMovieDetail(candidate.url) }.getOrNull()
                if (candidateDetail != null && (candidateDetail.iframes.isNotEmpty() || candidateDetail.dubbingIframes.isNotEmpty() || candidateDetail.subtitleIframes.isNotEmpty())) {
                    detail = candidateDetail.copy(
                        provider = candidateDetail.provider.ifBlank { candidate.provider.ifBlank { candidateScraper.name } },
                        sourceUrl = candidate.url
                    )
                    break
                }
            }
        }

        val finalDetail = detail ?: MovieDetail(
            title = targetMovie.title,
            description = "Film detayları yüklenemedi. Oynatmayı deneyebilir veya diğer kaynaklara göz atabilirsiniz.",
            posterUrl = targetMovie.posterUrl,
            iframes = emptyList(),
            provider = targetMovie.provider,
            sourceUrl = targetMovie.url
        )

        _movieDetail.value = finalDetail
    }

    private fun List<String>.filterValidEmbed(): List<String> = tv.newtv.utils.VodSourceUrls.playable(this)

    fun openSeries(item: SeriesItem) = load {
        _movieDetail.value = null // Film detayını temizle (State karışmasını engelle)
        val candidates = (listOf(item) + catalogEngine.seriesSources(item.title)).distinctBy { it.url }
        val resolved = mutableListOf<Pair<SeriesItem, SeriesDetail>>()
        for (candidate in candidates) {
            val scraper = providerManager.getSeriesScraperForUrl(candidate.url)
            val detail = runCatching { scraper.getSeriesDetail(candidate.url) }.getOrNull() ?: continue
            if (detail.seasons.any { it.episodes.isNotEmpty() }) {
                resolved += candidate to detail
            }
        }
        val (sourceItem, firstDetail) = resolved.firstOrNull()
            ?: throw Exception("Bu dizi için çalışan bölüm kaynağı bulunamadı.")
        // İlk çalışan kaynağın sezon yapısını kullan, dil bilgilerini tüm kaynaklardan topla
        val detail = firstDetail.copy(
            provider = firstDetail.provider.ifEmpty { sourceItem.provider },
            languages = resolved.flatMap { it.second.languages }.distinct().ifEmpty { sourceItem.languages },
            sourceUrl = sourceItem.url,
            hasDub = resolved.any { it.second.hasDub },
            hasSubtitle = resolved.any { it.second.hasSubtitle }
        )
        _seriesDetail.value = detail
    }

    fun playMovie(movieDetail: MovieDetail, preferDubbing: Boolean? = null) = load {
        val original = originalMovie ?: MovieItem(movieDetail.title, movieDetail.sourceUrl,
            movieDetail.posterUrl, provider = movieDetail.provider)
        
        // Önce movieResolver ile çalışan bir kaynak doğrulamayı dene
        val result = runCatching { movieResolver.find(original, preferDubbing) }.getOrNull()
        val detail = result?.detail ?: movieDetail
        val iframes = when (preferDubbing) {
            true -> detail.dubbingIframes.ifEmpty { detail.iframes }
            false -> detail.subtitleIframes.ifEmpty { detail.iframes }
            null -> detail.iframes.ifEmpty { detail.dubbingIframes + detail.subtitleIframes }
        }

        if (iframes.isEmpty() && result == null) {
            throw Exception("Bu film için seçilen dilde oynatılabilir kaynak bulunamadı.")
        }

        _playableRequest.value = PlayableRequest(
            iframes = iframes,
            title = original.title,
            isMovie = true,
            posterUrl = original.posterUrl.ifBlank { movieDetail.posterUrl },
            dubbingIframes = detail.dubbingIframes,
            subtitleIframes = detail.subtitleIframes,
            selectedLanguage = when (preferDubbing) { true -> "Türkçe Dublaj"; false -> "Türkçe Altyazı"; null -> "" },
            resolvedSource = result?.source
        )
    }

    fun openEpisode(url: String, episodeName: String = "", preferDubbing: Boolean? = null) = load {
        val sources = if (url.contains("selcukflix") || url.contains("hdfilmcehennemi")) {
            providerManager.hdfilmcehennemi.getEpisodeSources(url)"""

assert old_snippet in text, "old_snippet not found!"
text = text.replace(old_snippet, new_snippet, 1)
p.write_text(text, encoding="utf-8")
print("TvViewModels patched successfully!")
