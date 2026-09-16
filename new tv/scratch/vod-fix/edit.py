from pathlib import Path
root = Path('app/src/main/java/tv/newtv')
p = root / 'extractor/ExtractorChain.kt'
s = p.read_text(encoding='utf-8')
s = s.replace('import kotlinx.coroutines.async\nimport kotlinx.coroutines.awaitAll\nimport kotlinx.coroutines.supervisorScope', 'import kotlinx.coroutines.CancellationException\nimport kotlinx.coroutines.ensureActive\nimport kotlinx.coroutines.withTimeoutOrNull\nimport kotlin.coroutines.coroutineContext')
s = s.replace('import kotlinx.coroutines.withTimeout\n', '')
start = s.index('    /**')
end = s.index('    /** Tek bir iframe', start)
s = s[:start] + '''    /** Kaynakları sırayla dene; ilk sonuç bulunduğunda ağ işini durdur. */
    suspend fun resolveSource(iframeUrls: List<String>): VideoSource? =
        resolveAllSources(iframeUrls, maxSources = 1).firstOrNull()

    suspend fun resolveAllSources(
        iframeUrls: List<String>,
        maxSources: Int = MAX_SOURCES
    ): List<VideoSource> = withContext(Dispatchers.IO) {
        val resolved = mutableListOf<VideoSource>()
        val urls = iframeUrls.map { it.trim() }.filter { it.isNotEmpty() }
            .map { if (it.startsWith("//")) "https:$it" else it }.distinct()
        for ((index, url) in urls.withIndex()) {
            coroutineContext.ensureActive()
            val source = try {
                withTimeoutOrNull(45_000L) { resolveIframeSource(url, index) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("ExtractorChain", "Source resolution failed", error)
                null
            }
            coroutineContext.ensureActive()
            if (source != null && resolved.none { it.url == source.url }) {
                resolved.add(source)
                if (resolved.size >= maxSources.coerceAtLeast(1)) break
            }
        }
        resolved
    }

''' + s[end:]
s = s.replace('    /** Tek bir iframe URL\'sini çözümler (paralel çağrı için) */', '    /** Tek bir iframe URL\'sini çözümler. */')
p.write_text(s, encoding='utf-8')
p = root / 'ui/viewmodel/TvViewModels.kt'
s = p.read_text(encoding='utf-8')
s = s.replace('import kotlinx.coroutines.Job', 'import kotlinx.coroutines.CancellationException\nimport kotlinx.coroutines.ensureActive\nimport kotlinx.coroutines.Job', 1)
s = s.replace('    private val extractorChain = ExtractorChain(OkHttpClientProvider.getUnsafeOkHttpClient())', '''    private val extractorChain = ExtractorChain(
        OkHttpClientProvider.getUnsafeOkHttpClient().newBuilder()
            .callTimeout(20, java.util.concurrent.TimeUnit.SECONDS).build()
    )
    private var extractionJob: Job? = null
    private var pendingVodIframes = emptyList<String>()

    fun cancelVodExtraction() {
        extractionJob?.cancel()
        extractionJob = null
        _isLoading.value = false
    }

    private suspend fun resolveNextVodSource() {
        _isLoading.value = true
        _vodExtractionError.value = null
        try {
            while (pendingVodIframes.isNotEmpty()) {
                val iframe = pendingVodIframes.first()
                pendingVodIframes = pendingVodIframes.drop(1)
                val source = extractorChain.resolveSource(listOf(iframe))
                kotlin.coroutines.coroutineContext.ensureActive()
                if (source == null || _videoSources.value.any { it.url == source.url }) continue
                _videoSources.value = _videoSources.value + source
                _currentSources.value = _videoSources.value.map { it.url }
                _currentSourceIndex.value = _videoSources.value.lastIndex
                _currentStream.value = source.copy(timestamp = System.currentTimeMillis())
                return
            }
            _vodExtractionError.value = "Video kaynakları denendi ancak oynatılabilir bir kaynak bulunamadı. Yeniden deneyebilirsiniz."
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _vodExtractionError.value = "Video kaynağı çözümlenemedi. Lütfen yeniden deneyin."
        } finally {
            if (kotlin.coroutines.coroutineContext[Job]?.isActive == true) _isLoading.value = false
        }
    }

    fun retryVodStream(positionMs: Long) {
        loadStreamFromIframe(historyIframeUrls, _streamTitle.value,
            _mediaType.value == PlayerMediaType.VOD_MOVIE,
            _dubbingIframes.value, _subtitleIframes.value, _selectedLanguage.value,
            historyPosterUrl, positionMs)
    }''')
s = s.replace('    fun playChannelWithSource(channel:', '    fun playChannelWithSource(channel:', 1)
marker = '        _mediaType.value = PlayerMediaType.LIVE_CHANNEL'
s = s.replace(marker, '        cancelVodExtraction()\n' + marker)
s = s.replace('''        // Otomatik kaynak geçişi YALNIZCA Canlı TV için çalışır (VOD için devre dışı)
        if (_mediaType.value != PlayerMediaType.LIVE_CHANNEL) return false''', '''        if (_mediaType.value != PlayerMediaType.LIVE_CHANNEL) {
            if (extractionJob?.isActive == true) return true
            if (pendingVodIframes.isEmpty()) return false
            _failoverMessage.value = "Sıradaki video kaynağı deneniyor..."
            extractionJob = viewModelScope.launch { resolveNextVodSource() }
            return true
        }''')
start = s.index('    fun loadStreamFromIframe(')
end = s.index('    fun switchVodLanguage', start)
part = s[start:end].replace('        viewModelScope.launch {', '''        cancelVodExtraction()
        pendingVodIframes = iframeUrls.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        extractionJob = viewModelScope.launch {''', 1)
a = part.index('            _isLoading.value = true')
part = part[:a] + '            resolveNextVodSource()\n        }\n    }\n\n'
s = s[:start] + part + s[end:]
p.write_text(s, encoding='utf-8')
p = root / 'ui/screens/TvPlayerScreen.kt'
s = p.read_text(encoding='utf-8')
s = s.replace('''                    savedVodResumePosition = pendingSeekPosition.takeIf { it >= 0L } ?: exoPlayer.currentPosition
                }''', '''                    savedVodResumePosition = pendingSeekPosition.takeIf { it >= 0L } ?: exoPlayer.currentPosition
                    if (viewModel.switchToNextSource()) {
                        isBuffering = true
                        playerError = null
                        return
                    }
                }''', 1)
s = s.replace('if (!isVod || !isBuffering || currentStream == null || playerError != null)', 'if (!isVod || !isBuffering || currentStream == null || playerError != null || viewModel.isLoading.value)')
s = s.replace('< 15_000L', '< 45_000L')
s = s.replace('''            savedVodResumePosition = resumeAt
            isBuffering = false''', '''            savedVodResumePosition = resumeAt
            if (viewModel.switchToNextSource()) {
                vodBufferingStartedAt = 0L
                continue
            }
            isBuffering = false''')
s = s.replace('''                                        currentStream?.let { source ->
                                            playerManager.playStream(source.url, source.headers)
                                        }''', '''                                        viewModel.retryVodStream(savedVodResumePosition)''')
s = s.replace('            lifecycleOwner.lifecycle.removeObserver(observer)', '            viewModel.cancelVodExtraction()\n            lifecycleOwner.lifecycle.removeObserver(observer)')
s = s.replace('// 4. VOD (Film/Dizi) için otomatik failover YAPMA – kullanıcıya bildir', '// 4. VOD için konumu koruyarak sıradaki kaynağı çöz.')
s = s.replace('// VOD\'da otomatik failover yok – kullanıcıya doğrudan bildir', '// Uzun süre yanıt vermeyen VOD kaynağından sıradakine geç.')
p.write_text(s, encoding='utf-8')
