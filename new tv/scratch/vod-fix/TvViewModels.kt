package tv.newtv.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import tv.newtv.data.local.AppDatabase
import tv.newtv.data.local.ChannelEntity
import tv.newtv.data.local.ChannelStatus
import tv.newtv.data.local.WatchHistoryEntity
import org.json.JSONObject
import tv.newtv.data.models.MovieDetail
import tv.newtv.data.models.MovieItem
import tv.newtv.data.models.SeriesDetail
import tv.newtv.data.models.SeriesItem
import tv.newtv.data.models.VideoSource
import tv.newtv.extractor.ExtractorChain
import tv.newtv.network.OkHttpClientProvider
import tv.newtv.repository.IptvRepository
import tv.newtv.repository.VodCatalogEngine
import tv.newtv.scraper.VodProviderManager

enum class ChannelFilterType {
    ALL,      // Tüm Kanallar
    ACTIVE,   // Aktif
    OFFLINE   // Çalışmıyor
}

@OptIn(ExperimentalCoroutinesApi::class)
class TvMainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val VIRTUAL_SPORTS_SOURCE = "VIRTUAL_SPORTS_SOURCE"
        const val VIRTUAL_NATIONAL_SOURCE = "VIRTUAL_NATIONAL_SOURCE"
    }

    // Ekranlar arası kaybolmayan IPTV Grid Scroll & Odak durumu
    val channelGridState = LazyGridState()
    var lastFocusedChannelId by mutableStateOf<Int?>(null)
    
    private val dao = AppDatabase.getDatabase(application).channelDao()
    private val client = OkHttpClientProvider.getUnsafeOkHttpClient()
    val repository = IptvRepository(dao, client, application)

    val sources: StateFlow<List<String>> = dao.getSources()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Varsayılan olarak en üstteki özel "SPOR KANALLARI" seçili gelir
    private val _selectedSource = MutableStateFlow<String?>(VIRTUAL_SPORTS_SOURCE)
    val selectedSource: StateFlow<String?> = _selectedSource.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>("Tüm Spor Kanalları")
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _channels = MutableStateFlow<List<ChannelEntity>>(emptyList())
    val channels: StateFlow<List<ChannelEntity>> = _channels.asStateFlow()

    // Arama ve Filtre Durumları
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // 3'LÜ FİLTRE VE DOĞRULAMA DURUMLARI
    private val _channelFilter = MutableStateFlow(ChannelFilterType.ALL)
    val channelFilter: StateFlow<ChannelFilterType> = _channelFilter.asStateFlow()

    private val _channelStatuses = MutableStateFlow<Map<Int, ChannelStatus>>(emptyMap())
    val channelStatuses: StateFlow<Map<Int, ChannelStatus>> = _channelStatuses.asStateFlow()

    private val _isVerifying = MutableStateFlow(false)
    val isVerifying: StateFlow<Boolean> = _isVerifying.asStateFlow()

    private val _verificationProgress = MutableStateFlow(0 to 0)
    val verificationProgress: StateFlow<Pair<Int, Int>> = _verificationProgress.asStateFlow()

    private var verificationJob: kotlinx.coroutines.Job? = null

    // Filtrelenmiş ve doğrulanmış kanalları sunan StateFlow (Arama destekli)
    val displayChannels: StateFlow<List<ChannelEntity>> = combine(
        _channels,
        _channelStatuses,
        _channelFilter,
        _searchQuery
    ) { baseChannels, statuses, filter, query ->
        val mapped = baseChannels.map { ch ->
            val st = statuses[ch.id] ?: ch.status
            if (st != ch.status) ch.copy(status = st) else ch
        }
        val byFilter = when (filter) {
            ChannelFilterType.ALL -> mapped
            ChannelFilterType.ACTIVE -> mapped.filter { it.status == ChannelStatus.ACTIVE }
            ChannelFilterType.OFFLINE -> mapped.filter { it.status == ChannelStatus.OFFLINE }
        }
        if (query.isBlank()) {
            byFilter
        } else {
            byFilter.filter { it.name.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val activeCount: StateFlow<Int> = combine(_channels, _channelStatuses) { baseChannels, statuses ->
        baseChannels.count { ch -> (statuses[ch.id] ?: ch.status) == ChannelStatus.ACTIVE }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val offlineCount: StateFlow<Int> = combine(_channels, _channelStatuses) { baseChannels, statuses ->
        baseChannels.count { ch -> (statuses[ch.id] ?: ch.status) == ChannelStatus.OFFLINE }
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val totalCount: StateFlow<Int> = _channels.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    init {
        viewModelScope.launch {
            repository.syncInitialPlaylists()
        }

        // Seçilen ana bölüme göre (Spor vs Ulusal) alt kategorileri ayarla
        viewModelScope.launch {
            _selectedSource.collect { source ->
                val list = when (source) {
                    VIRTUAL_NATIONAL_SOURCE -> listOf("Tüm Ulusal Kanallar", "Haber Kanalları", "Genel TV")
                    else -> listOf("Tüm Spor Kanalları", "beIN Sports", "Exxen & Tivibu & S Sport", "Diğer Sporlar")
                }
                _categories.value = list
                if (_selectedCategory.value == null || !list.contains(_selectedCategory.value)) {
                    _selectedCategory.value = list.first()
                }
            }
        }
        
        // Kaynak ve kategoriye göre kanalları anında güncelle
        viewModelScope.launch {
            combine(_selectedSource, _selectedCategory) { source, cat ->
                source to cat
            }.flatMapLatest { (source, _) ->
                when (source) {
                    VIRTUAL_NATIONAL_SOURCE -> dao.getAllNationalChannels()
                    else -> dao.getAllSportsChannels()
                }
            }.collect { channels ->
                val targetList = when (_selectedSource.value) {
                    VIRTUAL_NATIONAL_SOURCE -> {
                        val newsKeywords = listOf("haber", "ntv", "cnn", "halk", "tele1", "sözcü", "sozcu", "bloomberg", "ekol", "24 tv", "tvnet", "tgrt", "ulke")
                        val newsChannels = channels.filter { ch -> newsKeywords.any { ch.name.contains(it, ignoreCase = true) } }
                            .sortedBy { it.name }
                        val generalChannels = channels.filter { ch -> !newsKeywords.any { ch.name.contains(it, ignoreCase = true) } }
                            .sortedBy { it.name }

                        when (_selectedCategory.value) {
                            "Haber Kanalları" -> newsChannels
                            "Genel TV" -> generalChannels
                            else -> generalChannels + newsChannels
                        }
                    }
                    else -> {
                        val beinChannels = channels.filter { it.name.contains("bein", ignoreCase = true) }
                            .sortedWith(compareBy<ChannelEntity> { 
                                val num = "\\d+".toRegex().find(it.name)?.value?.toIntOrNull() ?: 99
                                if (it.name.contains("haber", ignoreCase = true)) 0 else num
                            }.thenBy { it.name })

                        val exxenTivibuSsport = channels.filter { ch ->
                            val lower = ch.name.lowercase()
                            (lower.contains("exxen") || lower.contains("tivibu") || lower.contains("s sport") ||
                             lower.contains("ssport") || lower.contains("smart spor") || lower.contains("dsmart")) &&
                            !lower.contains("bein")
                        }.sortedBy { it.name }

                        val otherSports = channels.filter { ch ->
                            val lower = ch.name.lowercase()
                            !lower.contains("bein") &&
                            !lower.contains("exxen") &&
                            !lower.contains("tivibu") &&
                            !lower.contains("s sport") &&
                            !lower.contains("ssport") &&
                            !lower.contains("smart spor") &&
                            !lower.contains("dsmart")
                        }.sortedBy { it.name }

                        when (_selectedCategory.value) {
                            "beIN Sports" -> beinChannels
                            "Exxen & Tivibu & S Sport" -> exxenTivibuSsport
                            "Diğer Sporlar" -> otherSports
                            else -> beinChannels + exxenTivibuSsport + otherSports
                        }
                    }
                }
                _channels.value = targetList
            }
        }
    }

    fun selectSource(sourceUrl: String) {
        _selectedSource.value = sourceUrl
        _selectedCategory.value = when (sourceUrl) {
            VIRTUAL_NATIONAL_SOURCE -> "Tüm Ulusal Kanallar"
            else -> "Tüm Spor Kanalları"
        }
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setChannelFilter(filter: ChannelFilterType) {
        _channelFilter.value = filter
        if (filter != ChannelFilterType.ALL) {
            // Filtre seçildiğinde doğrulanmamış kanallar varsa nazikçe doğrula
            startVerification(force = false)
        }
    }

    fun startVerification(channelsToVerify: List<ChannelEntity> = _channels.value, force: Boolean = false) {
        if (channelsToVerify.isEmpty()) return
        verificationJob?.cancel()

        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            android.util.Log.e("TvIptvViewModel", "Verification error", throwable)
            _isVerifying.value = false
        }

        verificationJob = viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            _isVerifying.value = true
            val toCheck = if (force) channelsToVerify else channelsToVerify.filter {
                (_channelStatuses.value[it.id] ?: it.status) == ChannelStatus.UNKNOWN
            }

            if (toCheck.isEmpty()) {
                _isVerifying.value = false
                return@launch
            }

            _verificationProgress.value = 0 to toCheck.size

            val pendingUpdates = mutableMapOf<Int, ChannelStatus>()
            var lastUpdateMs = System.currentTimeMillis()

            repository.checkChannelsBatch(toCheck) { verified, total, channelId, status ->
                synchronized(pendingUpdates) {
                    pendingUpdates[channelId] = status
                }
                val now = System.currentTimeMillis()
                // Mi Box Gen 2 gibi TV cihazlarında arayüzün kasmaması için durumu 600ms'de bir veya bittiğinde topluca güncelle
                if (now - lastUpdateMs >= 600 || verified == total) {
                    lastUpdateMs = now
                    _verificationProgress.value = verified to total
                    val snapshot = synchronized(pendingUpdates) {
                        val copy = pendingUpdates.toMap()
                        pendingUpdates.clear()
                        copy
                    }
                    if (snapshot.isNotEmpty()) {
                        val current = _channelStatuses.value.toMutableMap()
                        current.putAll(snapshot)
                        _channelStatuses.value = current
                    }
                }
            }

            _isVerifying.value = false
        }
    }

    fun refreshPlaylists(extraSources: List<String> = emptyList()) {
        viewModelScope.launch {
            _isVerifying.value = true
            repository.syncInitialPlaylists(extraSources)
            _isVerifying.value = false
        }
    }
}

enum class TvResizeMode(val title: String, val mode: Int) {
    FIT("Orantılı (Fit)", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT),
    ZOOM("Tam Ekran (Zoom)", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    FILL("Esnet (16:9)", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL)
}

enum class PlayerMediaType {
    LIVE_CHANNEL,
    VOD_MOVIE,
    VOD_SERIES
}

data class PlayableRequest(
    val iframes: List<String>,
    val title: String,
    val isMovie: Boolean,
    val posterUrl: String = "",
    val dubbingIframes: List<String> = emptyList(),
    val subtitleIframes: List<String> = emptyList(),
    val selectedLanguage: String = ""
)

class TvPlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val extractorChain = ExtractorChain(OkHttpClientProvider.getUnsafeOkHttpClient())
    private val watchHistoryDao = AppDatabase.getDatabase(application).watchHistoryDao()
    private var historyIframeUrls: List<String> = emptyList()
    private var historyPosterUrl: String = ""
    private var lastHistorySaveAt = 0L
    private val _currentStream = MutableStateFlow<VideoSource?>(null)
    val currentStream: StateFlow<VideoSource?> = _currentStream.asStateFlow()

    private val _mediaType = MutableStateFlow(PlayerMediaType.LIVE_CHANNEL)
    val mediaType: StateFlow<PlayerMediaType> = _mediaType.asStateFlow()

    private val _vodExtractionError = MutableStateFlow<String?>(null)
    val vodExtractionError: StateFlow<String?> = _vodExtractionError.asStateFlow()

    private val _streamTitle = MutableStateFlow("Canlı Yayın")
    val streamTitle: StateFlow<String> = _streamTitle.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isControlsVisible = MutableStateFlow(false)
    val isControlsVisible: StateFlow<Boolean> = _isControlsVisible.asStateFlow()

    private val _showAudioSubMenu = MutableStateFlow(false)
    val showAudioSubMenu: StateFlow<Boolean> = _showAudioSubMenu.asStateFlow()

    private val _showSourceMenu = MutableStateFlow(false)
    val showSourceMenu: StateFlow<Boolean> = _showSourceMenu.asStateFlow()

    private val _showInfoMenu = MutableStateFlow(false)
    val showInfoMenu: StateFlow<Boolean> = _showInfoMenu.asStateFlow()

    private val _showChannelListMenu = MutableStateFlow(false)
    val showChannelListMenu: StateFlow<Boolean> = _showChannelListMenu.asStateFlow()

    private val _resizeMode = MutableStateFlow(TvResizeMode.FIT)
    val resizeMode: StateFlow<TvResizeMode> = _resizeMode.asStateFlow()

    private val _videoSources = MutableStateFlow<List<VideoSource>>(emptyList())
    val videoSources: StateFlow<List<VideoSource>> = _videoSources.asStateFlow()

    private val _currentSources = MutableStateFlow<List<String>>(emptyList())
    val currentSources: StateFlow<List<String>> = _currentSources.asStateFlow()

    private val _currentSourceIndex = MutableStateFlow(0)
    val currentSourceIndex: StateFlow<Int> = _currentSourceIndex.asStateFlow()

    private val _failoverMessage = MutableStateFlow<String?>(null)
    val failoverMessage: StateFlow<String?> = _failoverMessage.asStateFlow()

    private val _channelList = MutableStateFlow<List<ChannelEntity>>(emptyList())
    val channelList: StateFlow<List<ChannelEntity>> = _channelList.asStateFlow()

    private val _currentChannel = MutableStateFlow<ChannelEntity?>(null)
    val currentChannel: StateFlow<ChannelEntity?> = _currentChannel.asStateFlow()

    // VOD Dublaj & Altyazı Kaynakları
    private val _dubbingIframes = MutableStateFlow<List<String>>(emptyList())
    val dubbingIframes: StateFlow<List<String>> = _dubbingIframes.asStateFlow()

    private val _subtitleIframes = MutableStateFlow<List<String>>(emptyList())
    val subtitleIframes: StateFlow<List<String>> = _subtitleIframes.asStateFlow()

    private val _selectedLanguage = MutableStateFlow<String>("")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    private val _vodSavedPosition = MutableStateFlow<Long>(0L)
    val vodSavedPosition: StateFlow<Long> = _vodSavedPosition.asStateFlow()
    val recentHistory = watchHistoryDao.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun playChannel(channel: ChannelEntity, allChannels: List<ChannelEntity> = emptyList()) {
        _mediaType.value = PlayerMediaType.LIVE_CHANNEL
        _vodExtractionError.value = null
        _currentChannel.value = channel
        if (allChannels.isNotEmpty()) {
            _channelList.value = allChannels
        }
        playChannelStream(channel.getStreamUrls(), channel.name)
    }

    fun playChannelWithSource(channel: ChannelEntity, sourceIndex: Int, allChannels: List<ChannelEntity> = emptyList()) {
        _mediaType.value = PlayerMediaType.LIVE_CHANNEL
        _vodExtractionError.value = null
        _currentChannel.value = channel
        if (allChannels.isNotEmpty()) {
            _channelList.value = allChannels
        }
        val urls = channel.getStreamUrls().filter { it.isNotBlank() }
        _currentSources.value = urls
        _videoSources.value = emptyList()
        val idx = sourceIndex.coerceIn(0, (urls.size - 1).coerceAtLeast(0))
        _currentSourceIndex.value = idx
        _streamTitle.value = channel.name
        _failoverMessage.value = if (urls.size > 1) "Kaynak ${idx + 1}/${urls.size} Açıldı" else null
        val targetUrl = urls.getOrNull(idx) ?: ""
        _currentStream.value = VideoSource(
            url = targetUrl,
            quality = "Direct",
            mimeType = androidx.media3.common.MimeTypes.APPLICATION_M3U8,
            timestamp = System.currentTimeMillis()
        )
    }

    fun playNextChannel(): Boolean {
        if (_mediaType.value != PlayerMediaType.LIVE_CHANNEL) return false
        val list = _channelList.value
        val cur = _currentChannel.value ?: return false
        val idx = list.indexOfFirst { it.id == cur.id }
        if (idx != -1 && list.isNotEmpty()) {
            val nextIdx = (idx + 1) % list.size
            playChannel(list[nextIdx])
            return true
        }
        return false
    }

    fun playPreviousChannel(): Boolean {
        if (_mediaType.value != PlayerMediaType.LIVE_CHANNEL) return false
        val list = _channelList.value
        val cur = _currentChannel.value ?: return false
        val idx = list.indexOfFirst { it.id == cur.id }
        if (idx != -1 && list.isNotEmpty()) {
            val prevIdx = if (idx - 1 < 0) list.size - 1 else idx - 1
            playChannel(list[prevIdx])
            return true
        }
        return false
    }

    fun playChannelStream(urls: List<String>, title: String = "Canlı Yayın") {
        _mediaType.value = PlayerMediaType.LIVE_CHANNEL
        _vodExtractionError.value = null
        _streamTitle.value = title
        val filtered = urls.filter { it.isNotBlank() }
        _currentSources.value = filtered
        _videoSources.value = emptyList()
        _currentSourceIndex.value = 0
        _failoverMessage.value = null
        val firstUrl = filtered.firstOrNull() ?: ""
        _currentStream.value = VideoSource(
            url = firstUrl,
            quality = "Direct",
            mimeType = androidx.media3.common.MimeTypes.APPLICATION_M3U8,
            timestamp = System.currentTimeMillis()
        )
    }

    fun playDirectStream(url: String, title: String = "Canlı Yayın") {
        playChannelStream(listOf(url), title)
    }

    fun selectSourceIndex(index: Int) {
        val vSources = _videoSources.value
        val sources = _currentSources.value
        if (vSources.isNotEmpty() && index in vSources.indices) {
            _currentSourceIndex.value = index
            val nextVideoSource = vSources[index]
            val label = nextVideoSource.serverName.ifEmpty { "Sunucu ${index + 1}" }
            _failoverMessage.value = "$label Seçildi"
            _currentStream.value = nextVideoSource.copy(timestamp = System.currentTimeMillis())
            _showSourceMenu.value = false
        } else if (sources.isNotEmpty() && index in sources.indices) {
            _currentSourceIndex.value = index
            val nextUrl = sources[index]
            _failoverMessage.value = "Yedek Kaynak (${index + 1}/${sources.size}) Seçildi"
            _currentStream.value = VideoSource(
                url = nextUrl,
                quality = "Direct",
                mimeType = androidx.media3.common.MimeTypes.APPLICATION_M3U8,
                timestamp = System.currentTimeMillis()
            )
            _showSourceMenu.value = false
        }
    }

    fun cycleResizeMode(): TvResizeMode {
        val modes = TvResizeMode.values()
        val nextMode = modes[(_resizeMode.value.ordinal + 1) % modes.size]
        _resizeMode.value = nextMode
        _failoverMessage.value = "Ekran Boyutu: ${nextMode.title}"
        return nextMode
    }

    fun switchToNextSource(): Boolean {
        // Otomatik kaynak geçişi YALNIZCA Canlı TV için çalışır (VOD için devre dışı)
        if (_mediaType.value != PlayerMediaType.LIVE_CHANNEL) return false

        val sources = _currentSources.value
        val nextIdx = _currentSourceIndex.value + 1
        if (nextIdx < sources.size) {
            _currentSourceIndex.value = nextIdx
            val nextUrl = sources[nextIdx]
            _failoverMessage.value = "Yedek Kaynak (${nextIdx + 1}/${sources.size}) Açılıyor..."
            _currentStream.value = VideoSource(
                url = nextUrl,
                quality = "Direct",
                mimeType = androidx.media3.common.MimeTypes.APPLICATION_M3U8,
                timestamp = System.currentTimeMillis()
            )
            return true
        }
        return false
    }

    fun clearFailoverMessage() {
        _failoverMessage.value = null
    }

    fun loadStreamFromIframe(
        iframeUrls: List<String>,
        title: String = "Film / Dizi Yayını",
        isMovie: Boolean = true,
        dubbingIframes: List<String> = emptyList(),
        subtitleIframes: List<String> = emptyList(),
        selectedLanguage: String = "",
        posterUrl: String = "",
        resumePositionMs: Long = 0L
    ) {
        viewModelScope.launch {
            _mediaType.value = if (isMovie) PlayerMediaType.VOD_MOVIE else PlayerMediaType.VOD_SERIES
            _streamTitle.value = title
            _vodExtractionError.value = null
            _dubbingIframes.value = dubbingIframes
            _subtitleIframes.value = subtitleIframes
            _selectedLanguage.value = selectedLanguage.ifEmpty {
                if (dubbingIframes.isNotEmpty()) "Türkçe Dublaj" else if (subtitleIframes.isNotEmpty()) "Türkçe Altyazı" else ""
            }
            _vodSavedPosition.value = resumePositionMs
            historyIframeUrls = iframeUrls.filter { it.isNotBlank() }.distinct()
            historyPosterUrl = posterUrl

            // Canlı TV durumunu kesinlikle temizle (State sızıntısını engelle)
            _currentChannel.value = null
            _channelList.value = emptyList()
            _videoSources.value = emptyList()
            _currentSources.value = emptyList()
            _currentSourceIndex.value = 0
            _failoverMessage.value = null
            _currentStream.value = null

            _isLoading.value = true
            val resolvedList = extractorChain.resolveAllSources(iframeUrls)
            if (resolvedList.isNotEmpty()) {
                _videoSources.value = resolvedList
                _currentSources.value = resolvedList.map { it.url }
                _currentSourceIndex.value = 0
                _currentStream.value = resolvedList.first()
                if (resolvedList.size > 1) {
                    _failoverMessage.value = "${resolvedList.size} Alternatif Sunucu Bulundu"
                }
            } else {
                _vodExtractionError.value = "Video kaynağı çözümlenemedi veya sunucu geçici olarak yanıt vermiyor."
            }
            _isLoading.value = false
        }
    }

    fun switchVodLanguage(targetLanguage: String, currentPositionMs: Long) {
        val targetLower = targetLanguage.lowercase()
        val targetIframes = when {
            targetLower.contains("dublaj") -> _dubbingIframes.value
            targetLower.contains("altyaz") -> _subtitleIframes.value
            targetLower.contains("subtitle") || targetLower.contains("sub") -> _subtitleIframes.value
            targetLower.contains("dub") -> _dubbingIframes.value
            else -> _dubbingIframes.value.ifEmpty { _subtitleIframes.value }
        }
        if (targetIframes.isNotEmpty()) {
            loadStreamFromIframe(
                iframeUrls = targetIframes,
                title = _streamTitle.value,
                isMovie = _mediaType.value == PlayerMediaType.VOD_MOVIE,
                dubbingIframes = _dubbingIframes.value,
                subtitleIframes = _subtitleIframes.value,
                selectedLanguage = targetLanguage,
                posterUrl = historyPosterUrl,
                resumePositionMs = currentPositionMs
            )
        }
    }

    fun savePlaybackProgress(positionMs: Long, durationMs: Long, force: Boolean = false) {
        val mediaType = _mediaType.value
        if (mediaType == PlayerMediaType.LIVE_CHANNEL || historyIframeUrls.isEmpty() || durationMs <= 0L || positionMs < 10_000L) {
            return
        }
        val now = System.currentTimeMillis()
        if (!force && now - lastHistorySaveAt < 30_000L) return
        lastHistorySaveAt = now
        val isMovie = mediaType == PlayerMediaType.VOD_MOVIE
        val title = _streamTitle.value
        viewModelScope.launch(Dispatchers.IO) {
            watchHistoryDao.upsert(
                WatchHistoryEntity(
                    contentKey = WatchHistoryEntity.contentKey(title, mediaType),
                    title = title,
                    posterUrl = historyPosterUrl,
                    iframeUrlsJson = org.json.JSONArray(historyIframeUrls).toString(),
                    isMovie = isMovie,
                    selectedLanguage = _selectedLanguage.value,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    updatedAt = now
                )
            )
        }
    }

    fun resume(history: WatchHistoryEntity) {
        loadStreamFromIframe(
            iframeUrls = history.iframeUrls(),
            title = history.title,
            isMovie = history.isMovie,
            selectedLanguage = history.selectedLanguage,
            posterUrl = history.posterUrl,
            resumePositionMs = history.positionMs
        )
    }

    fun showControls() {
        _isControlsVisible.value = true
        _showAudioSubMenu.value = false
        _showSourceMenu.value = false
        _showInfoMenu.value = false
        _showChannelListMenu.value = false
    }

    fun toggleControls() {
        _isControlsVisible.value = !_isControlsVisible.value
        if (_isControlsVisible.value) {
            _showAudioSubMenu.value = false
            _showSourceMenu.value = false
            _showInfoMenu.value = false
            _showChannelListMenu.value = false
        }
    }

    fun toggleAudioSubMenu() {
        _showAudioSubMenu.value = !_showAudioSubMenu.value
        if (_showAudioSubMenu.value) {
            _isControlsVisible.value = false
            _showSourceMenu.value = false
            _showInfoMenu.value = false
            _showChannelListMenu.value = false
        }
    }

    fun toggleSourceMenu() {
        _showSourceMenu.value = !_showSourceMenu.value
        if (_showSourceMenu.value) {
            _isControlsVisible.value = false
            _showAudioSubMenu.value = false
            _showInfoMenu.value = false
            _showChannelListMenu.value = false
        }
    }

    fun toggleInfoMenu() {
        _showInfoMenu.value = !_showInfoMenu.value
        if (_showInfoMenu.value) {
            _isControlsVisible.value = false
            _showAudioSubMenu.value = false
            _showSourceMenu.value = false
            _showChannelListMenu.value = false
        }
    }

    fun toggleChannelListMenu() {
        _showChannelListMenu.value = !_showChannelListMenu.value
        if (_showChannelListMenu.value) {
            _isControlsVisible.value = false
            _showAudioSubMenu.value = false
            _showSourceMenu.value = false
            _showInfoMenu.value = false
        }
    }

    fun hideAllMenus() {
        _isControlsVisible.value = false
        _showAudioSubMenu.value = false
        _showSourceMenu.value = false
        _showInfoMenu.value = false
        _showChannelListMenu.value = false
    }
}

/** VOD kaynaklarını ana iş parçacığını engellemeden UI'ya sunar. */
class TvVodViewModel(application: Application) : AndroidViewModel(application) {
    private val favoritesPreferences = application.getSharedPreferences("vod_favorites", Application.MODE_PRIVATE)
    private val client = OkHttpClientProvider.getScraperOkHttpClient()
    val providerManager = VodProviderManager(client)
    private val catalogEngine = VodCatalogEngine(
        AppDatabase.getDatabase(application).vodCatalogDao(),
        providerManager
    )
    private var movieScanJob: Job? = null
    private var seriesScanJob: Job? = null
    private var allMovieCatalog = emptyList<MovieItem>()
    private var allSeriesCatalog = emptyList<SeriesItem>()

    // Ekranlar arası kaybolmayan Scroll, Kategori ve Odak hafızası
    val movieScrollState = LazyListState()
    val seriesScrollState = LazyListState()
    val categoryDetailGridState = LazyGridState()

    var selectedCategory by mutableStateOf<tv.newtv.data.models.VodCategory<*>?>(null)
    var lastFocusedMovieUrl by mutableStateOf<String?>(null)
    var lastFocusedSeriesUrl by mutableStateOf<String?>(null)

    private val _movies = MutableStateFlow<List<tv.newtv.data.models.VodCategory<MovieItem>>>(emptyList())
    val movies: StateFlow<List<tv.newtv.data.models.VodCategory<MovieItem>>> = _movies.asStateFlow()
    private val _series = MutableStateFlow<List<tv.newtv.data.models.VodCategory<SeriesItem>>>(emptyList())
    val series: StateFlow<List<tv.newtv.data.models.VodCategory<SeriesItem>>> = _series.asStateFlow()
    
    private val _seriesDetail = MutableStateFlow<SeriesDetail?>(null)
    val seriesDetail: StateFlow<SeriesDetail?> = _seriesDetail.asStateFlow()
    
    private val _movieDetail = MutableStateFlow<MovieDetail?>(null)
    val movieDetail: StateFlow<MovieDetail?> = _movieDetail.asStateFlow()
    
    private val _playableRequest = MutableStateFlow<PlayableRequest?>(null)
    val playableRequest: StateFlow<PlayableRequest?> = _playableRequest.asStateFlow()

    private val _playableIframes = MutableStateFlow<List<String>?>(null)
    val playableIframes: StateFlow<List<String>?> = _playableIframes.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Arama Durumları
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _movieSearchResults = MutableStateFlow<List<MovieItem>>(emptyList())
    val movieSearchResults: StateFlow<List<MovieItem>> = _movieSearchResults.asStateFlow()

    private val _seriesSearchResults = MutableStateFlow<List<SeriesItem>>(emptyList())
    val seriesSearchResults: StateFlow<List<SeriesItem>> = _seriesSearchResults.asStateFlow()

    private val _favoriteUrls = MutableStateFlow(favoritesPreferences.getStringSet("urls", emptySet()) ?: emptySet())
    val favoriteUrls: StateFlow<Set<String>> = _favoriteUrls.asStateFlow()

    data class FavoriteVod(val url: String, val title: String, val posterUrl: String, val isMovie: Boolean)
    private val _favorites = MutableStateFlow(loadFavorites())
    val favorites: StateFlow<List<FavoriteVod>> = _favorites.asStateFlow()

    fun toggleFavorite(url: String, title: String = "", posterUrl: String = "", isMovie: Boolean = true) {
        if (url.isBlank()) return
        val updated = _favoriteUrls.value.toMutableSet()
        if (!updated.add(url)) updated.remove(url)
        _favoriteUrls.value = updated
        favoritesPreferences.edit().putStringSet("urls", updated).apply()
        val entries = _favorites.value.toMutableList()
        val index = entries.indexOfFirst { it.url == url }
        if (index >= 0) entries.removeAt(index) else entries.add(FavoriteVod(url, title, posterUrl, isMovie))
        _favorites.value = entries
        favoritesPreferences.edit().putStringSet("items", entries.map {
            JSONObject().put("url", it.url).put("title", it.title).put("poster", it.posterUrl).put("movie", it.isMovie).toString()
        }.toSet()).apply()
    }

    private fun loadFavorites(): List<FavoriteVod> = favoritesPreferences.getStringSet("items", emptySet())
        ?.mapNotNull { raw -> runCatching { JSONObject(raw) }.getOrNull() }
        ?.mapNotNull { json -> json.optString("url").takeIf { it.isNotBlank() }?.let { FavoriteVod(it, json.optString("title"), json.optString("poster"), json.optBoolean("movie", true)) } }
        ?: emptyList()

    private val _searchProviderNotice = MutableStateFlow<String?>(null)
    val searchProviderNotice: StateFlow<String?> = _searchProviderNotice.asStateFlow()

    private val _movieCatalogPage = MutableStateFlow(1)
    val movieCatalogPage: StateFlow<Int> = _movieCatalogPage.asStateFlow()
    private val _seriesCatalogPage = MutableStateFlow(1)
    val seriesCatalogPage: StateFlow<Int> = _seriesCatalogPage.asStateFlow()
    private val _movieCatalogPageCount = MutableStateFlow(1)
    val movieCatalogPageCount: StateFlow<Int> = _movieCatalogPageCount.asStateFlow()
    private val _seriesCatalogPageCount = MutableStateFlow(1)
    val seriesCatalogPageCount: StateFlow<Int> = _seriesCatalogPageCount.asStateFlow()
    private val _movieCatalogScanStatus = MutableStateFlow<String?>(null)
    val movieCatalogScanStatus: StateFlow<String?> = _movieCatalogScanStatus.asStateFlow()
    private val _seriesCatalogScanStatus = MutableStateFlow<String?>(null)
    val seriesCatalogScanStatus: StateFlow<String?> = _seriesCatalogScanStatus.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _movieSearchResults.value = emptyList()
            _seriesSearchResults.value = emptyList()
            _searchProviderNotice.value = null
        }
    }

    fun search(query: String, isMovie: Boolean) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _movieSearchResults.value = emptyList()
            _seriesSearchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                if (isMovie) {
                    val result = providerManager.searchAllMovies(query.trim())
                    _movieSearchResults.value = result.items
                    _searchProviderNotice.value = result.toUserNotice()
                } else {
                    val result = providerManager.searchAllSeries(query.trim())
                    _seriesSearchResults.value = result.items
                    _searchProviderNotice.value = result.toUserNotice()
                }
            } catch (e: Exception) {
                _error.value = "Arama sırasında bir sorun oluştu."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _movieSearchResults.value = emptyList()
        _seriesSearchResults.value = emptyList()
        _searchProviderNotice.value = null
    }

    fun loadMovies(force: Boolean = false, nextPage: Boolean = false) {
        if (nextPage) {
            setMovieCatalogPage(_movieCatalogPage.value + 1)
            return
        }
        if (movieScanJob?.isActive == true || (!force && allMovieCatalog.isNotEmpty())) return
        movieScanJob = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            allMovieCatalog = catalogEngine.movies()
            publishMoviePage(reset = true)
            _movieCatalogScanStatus.value = "Tüm film kaynakları taranıyor…"
            runCatching {
                catalogEngine.scanMovies { provider, count ->
                    _movieCatalogScanStatus.value = "$provider tarandı: $count içerik"
                }
            }
            allMovieCatalog = catalogEngine.movies()
            publishMoviePage(reset = true)
            _movieCatalogScanStatus.value = "${allMovieCatalog.size} film, ${providerManager.movieScrapers.size} kaynak"
            if (allMovieCatalog.isEmpty()) _error.value = "Film kaynaklarına şu anda ulaşılamıyor. Yeniden deneyin."
            _isLoading.value = false
        }
    }

    fun loadSeries(force: Boolean = false, nextPage: Boolean = false) {
        if (nextPage) {
            setSeriesCatalogPage(_seriesCatalogPage.value + 1)
            return
        }
        if (seriesScanJob?.isActive == true || (!force && allSeriesCatalog.isNotEmpty())) return
        seriesScanJob = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            allSeriesCatalog = catalogEngine.series()
            publishSeriesPage(reset = true)
            _seriesCatalogScanStatus.value = "Tüm dizi kaynakları taranıyor…"
            runCatching {
                catalogEngine.scanSeries { provider, count ->
                    _seriesCatalogScanStatus.value = "$provider tarandı: $count içerik"
                }
            }
            allSeriesCatalog = catalogEngine.series()
            publishSeriesPage(reset = true)
            _seriesCatalogScanStatus.value = "${allSeriesCatalog.size} dizi, ${providerManager.seriesScrapers.size} kaynak"
            if (allSeriesCatalog.isEmpty()) _error.value = "Dizi kaynaklarına şu anda ulaşılamıyor. Yeniden deneyin."
            _isLoading.value = false
        }
    }

    fun setMovieCatalogPage(page: Int) {
        _movieCatalogPage.value = page.coerceIn(1, _movieCatalogPageCount.value)
        publishMoviePage()
    }

    fun setSeriesCatalogPage(page: Int) {
        _seriesCatalogPage.value = page.coerceIn(1, _seriesCatalogPageCount.value)
        publishSeriesPage()
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
        val candidates = (listOf(item) + catalogEngine.movieSources(item.title)).distinctBy { it.url }
        val resolved = mutableListOf<Pair<MovieItem, MovieDetail>>()
        for (candidate in candidates) {
            val scraper = providerManager.getMovieScraperForUrl(candidate.url)
            val detail = runCatching { scraper.getMovieDetail(candidate.url) }.getOrNull() ?: continue
            val playable = detail.iframes.isNotEmpty() || detail.dubbingIframes.isNotEmpty() ||
                detail.subtitleIframes.isNotEmpty() || (detail.isSeries && detail.seasons.isNotEmpty())
            if (playable) {
                resolved += candidate to detail
            }
        }
        val (selectedItem, firstDetail) = resolved.firstOrNull()
            ?: throw Exception("Bu film için çalışan video kaynağı bulunamadı.")
        val detail = firstDetail.copy(
            iframes = resolved.flatMap { it.second.iframes }.filterValidEmbed().distinct(),
            dubbingIframes = resolved.flatMap { it.second.dubbingIframes }.filterValidEmbed().distinct(),
            subtitleIframes = resolved.flatMap { it.second.subtitleIframes }.filterValidEmbed().distinct(),
            hasDub = resolved.any { it.second.hasDub || it.second.dubbingIframes.isNotEmpty() },
            hasSubtitle = resolved.any { it.second.hasSubtitle || it.second.subtitleIframes.isNotEmpty() },
            languages = resolved.flatMap { it.second.languages }.distinct()
        )
        if (detail.isSeries && detail.seasons.isNotEmpty()) {
            // Film kategorisinden bir DİZİ açıldı! Otomatik olarak Dizi Detayına çevir:
            _movieDetail.value = null
            _seriesDetail.value = SeriesDetail(
                title = detail.title,
                description = detail.description,
                posterUrl = detail.posterUrl,
                seasons = detail.seasons,
                provider = detail.provider.ifEmpty { selectedItem.provider },
                languages = detail.languages.ifEmpty { selectedItem.languages },
                sourceUrl = selectedItem.url
            )
        } else {
            _seriesDetail.value = null
            _movieDetail.value = detail.copy(
                provider = detail.provider.ifEmpty { selectedItem.provider },
                languages = detail.languages.ifEmpty { selectedItem.languages },
                sourceUrl = selectedItem.url
            )
        }
    }

    private fun List<String>.filterValidEmbed(): List<String> = filter { url ->
        url.isNotBlank() && !url.contains("vr_set=1", ignoreCase = true) &&
            (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true))
    }
    
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
    
    fun playMovie(movieDetail: MovieDetail, preferDubbing: Boolean? = null) {
        val selectedIframes = when {
            preferDubbing == true && movieDetail.dubbingIframes.isNotEmpty() -> movieDetail.dubbingIframes
            preferDubbing == false && movieDetail.subtitleIframes.isNotEmpty() -> movieDetail.subtitleIframes
            preferDubbing == true && movieDetail.subtitleIframes.isNotEmpty() -> movieDetail.subtitleIframes
            preferDubbing == false && movieDetail.dubbingIframes.isNotEmpty() -> movieDetail.dubbingIframes
            else -> movieDetail.iframes.ifEmpty { movieDetail.dubbingIframes.ifEmpty { movieDetail.subtitleIframes } }
        }

        if (selectedIframes.isEmpty()) {
            _error.value = "Bu film için aktif oynatıcı kaynağı bulunamadı."
            return
        }

        val langTag = when {
            preferDubbing == true && movieDetail.dubbingIframes.isNotEmpty() -> "Türkçe Dublaj"
            preferDubbing == false && movieDetail.subtitleIframes.isNotEmpty() -> "Türkçe Altyazı"
            movieDetail.dubbingIframes.isNotEmpty() -> "Türkçe Dublaj"
            movieDetail.subtitleIframes.isNotEmpty() -> "Türkçe Altyazı"
            else -> ""
        }

        _playableIframes.value = selectedIframes
        _playableRequest.value = PlayableRequest(
        iframes = selectedIframes,
            title = movieDetail.title,
            isMovie = true,
            posterUrl = movieDetail.posterUrl,
            dubbingIframes = movieDetail.dubbingIframes.ifEmpty { selectedIframes },
            subtitleIframes = movieDetail.subtitleIframes.ifEmpty { selectedIframes },
            selectedLanguage = langTag
        )
    }
    
    fun openEpisode(url: String, episodeName: String = "", preferDubbing: Boolean? = null) = load {
        val sources = if (url.contains("selcukflix") || url.contains("hdfilmcehennemi")) {
            providerManager.hdfilmcehennemi.getEpisodeSources(url)
        } else {
            providerManager.getSeriesScraperForUrl(url).getEpisodeSources(url)
        }

        val dubbing = sources.dubbingIframes
        val subtitle = sources.subtitleIframes

        val selectedIframes = if (preferDubbing == true) {
            if (dubbing.isNotEmpty()) dubbing else (if (subtitle.isNotEmpty()) subtitle else sources.iframes)
        } else if (preferDubbing == false) {
            if (subtitle.isNotEmpty()) subtitle else (if (dubbing.isNotEmpty()) dubbing else sources.iframes)
        } else {
            if (dubbing.isNotEmpty()) dubbing else (if (subtitle.isNotEmpty()) subtitle else sources.iframes)
        }

        if (selectedIframes.isEmpty()) {
            _error.value = "Bu bölüm için aktif oynatıcı kaynağı bulunamadı."
            return@load
        }
        val seriesTitle = _seriesDetail.value?.title ?: "Dizi"
        val fullTitle = if (episodeName.isNotBlank()) "$seriesTitle - $episodeName" else seriesTitle
        val langTag = if (preferDubbing == true && dubbing.isNotEmpty()) {
            "Türkçe Dublaj"
        } else if (preferDubbing == false && subtitle.isNotEmpty()) {
            "Türkçe Altyazı"
        } else if (dubbing.isNotEmpty() && selectedIframes == dubbing) {
            "Türkçe Dublaj"
        } else {
            "Türkçe Altyazı"
        }

        _playableIframes.value = selectedIframes
        _playableRequest.value = PlayableRequest(
            iframes = selectedIframes,
            title = fullTitle,
            isMovie = false,
            posterUrl = _seriesDetail.value?.posterUrl ?: "",
            dubbingIframes = dubbing.ifEmpty { selectedIframes },
            subtitleIframes = subtitle.ifEmpty { selectedIframes },
            selectedLanguage = langTag
        )
    }
    
    fun consumePlayableRequest() { 
        _playableRequest.value = null 
        _playableIframes.value = null
    }

    fun consumePlayableIframes() { 
        _playableIframes.value = null 
        _playableRequest.value = null
    }

    fun clearSeriesDetail() { _seriesDetail.value = null }
    fun clearMovieDetail() { _movieDetail.value = null }

    private fun load(block: suspend () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            runCatching { block() }.onFailure { error ->
                val safeMessage = error.message?.takeIf {
                    it.startsWith("Bu film için") || it.startsWith("Bu dizi için") ||
                        it.startsWith("Film kaynaklarına") || it.startsWith("Dizi kaynaklarına")
                }
                _error.value = safeMessage ?: "İçerik çözümlenemedi. Diğer kaynaklar da denendi."
            }
            _isLoading.value = false
        }
    }

    private fun VodProviderManager.ProviderSearchResult<*>.toUserNotice(): String? {
        if (unavailableProviders.isEmpty()) return null
        return "Bazı kaynaklar şu an yanıt vermiyor: ${unavailableProviders.joinToString()}."
    }
}
