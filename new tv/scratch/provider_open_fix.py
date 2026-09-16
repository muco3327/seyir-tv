from pathlib import Path
p=Path('app/src/main/java/tv/newtv/ui/viewmodel/TvViewModels.kt')
s=p.read_text(encoding='utf-8')
s=s.replace('val selectedLanguage: String = ""\n)', 'val selectedLanguage: String = "",\n    val resolvedSource: VideoSource? = null\n)',1)
s=s.replace('resumePositionMs: Long = 0L\n    )', 'resumePositionMs: Long = 0L,\n        resolvedSource: VideoSource? = null\n    )',1)
s=s.replace('            resolveNextVodSource()\n        }\n    }\n\n    fun switchVodLanguage', '''            if (resolvedSource != null) {
                _videoSources.value = listOf(resolvedSource)
                _currentSources.value = listOf(resolvedSource.url)
                _currentStream.value = resolvedSource.copy(timestamp = System.currentTimeMillis())
                pendingVodIframes = emptyList()
                _isLoading.value = false
            } else resolveNextVodSource()
        }
    }

    fun switchVodLanguage''')
a=s.index('        if (_mediaType.value != PlayerMediaType.LIVE_CHANNEL) {',s.index('    fun switchToNextSource(): Boolean'))
b=s.index('\n\n        val sources',a)
s=s[:a]+'        if (_mediaType.value != PlayerMediaType.LIVE_CHANNEL) return false'+s[b:]
at=s.index('    private var movieScanJob')
s=s[:at]+'''    private val openingClient = client.newBuilder().callTimeout(15, java.util.concurrent.TimeUnit.SECONDS).build()
    private val movieResolver = tv.newtv.repository.MovieSourceResolver(
        VodProviderManager(openingClient).movieScrapers,
        { catalogEngine.movieCandidates() },
        tv.newtv.extractor.PlayableMovieSource(openingClient)::resolve
    )
    private var originalMovie: MovieItem? = null
    private var contentJob: Job? = null
''' + s[at:]
a=s.index('    fun openMovie(item: MovieItem)')
b=s.index('    private fun List<String>.filterValidEmbed',a)
s=s[:a]+'''    fun openMovie(item: MovieItem) = load {
        originalMovie = item.copy(provider = item.provider.ifBlank {
            providerManager.getMovieScraperForUrl(item.url).name
        })
        val result = movieResolver.find(originalMovie!!)
            ?: throw Exception("Bu film için tüm sağlayıcılar sırayla arandı ancak açılabilir video bulunamadı.")
        _seriesDetail.value = null
        _movieDetail.value = result.detail
    }

'''+s[b:]
a=s.index('    fun playMovie(movieDetail: MovieDetail')
b=s.index('    fun openEpisode',a)
s=s[:a]+'''    fun playMovie(movieDetail: MovieDetail, preferDubbing: Boolean? = null) = load {
        val original = originalMovie ?: MovieItem(movieDetail.title, movieDetail.sourceUrl,
            movieDetail.posterUrl, provider = movieDetail.provider)
        val result = movieResolver.find(original, preferDubbing)
            ?: throw Exception("Bu film için seçilen dilde açılabilir video bulunamadı. Tüm sağlayıcılar sırayla denendi.")
        val detail = result.detail
        val iframes = when (preferDubbing) {
            true -> detail.dubbingIframes.ifEmpty { detail.iframes }
            false -> detail.subtitleIframes.ifEmpty { detail.iframes }
            null -> detail.iframes.ifEmpty { detail.dubbingIframes + detail.subtitleIframes }
        }
        _playableRequest.value = PlayableRequest(
            iframes = iframes,
            title = original.title,
            isMovie = true,
            posterUrl = original.posterUrl,
            dubbingIframes = detail.dubbingIframes,
            subtitleIframes = detail.subtitleIframes,
            selectedLanguage = when (preferDubbing) { true -> "Türkçe Dublaj"; false -> "Türkçe Altyazı"; null -> "" },
            resolvedSource = result.source
        )
    }

'''+s[b:]
a=s.index('    private fun load(block: suspend () -> Unit)')
s=s[:a]+s[a:].replace('        viewModelScope.launch {', '        contentJob?.cancel()\n        contentJob = viewModelScope.launch {',1).replace('            runCatching { block() }.onFailure { error ->','            runCatching { block() }.onFailure { error ->\n                if (error is CancellationException) throw error',1)
p.write_text(s,encoding='utf-8')
p=Path('app/src/main/java/tv/newtv/ui/navigation/TvAppNavigation.kt')
s=p.read_text(encoding='utf-8').replace('selectedLanguage = req.selectedLanguage','selectedLanguage = req.selectedLanguage,\n                    resolvedSource = req.resolvedSource',1)
p.write_text(s,encoding='utf-8')
