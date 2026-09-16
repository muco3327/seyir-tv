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
import kotlinx.coroutines.async
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

    private val _isStarting = MutableStateFlow(false)
    val isStarting: StateFlow<Boolean> = _isStarting.asStateFlow()

    private val _startupMessage = MutableStateFlow("Yükleniyor...")
    val startupMessage: StateFlow<String> = _startupMessage.asStateFlow()

    private val _startupProgress = MutableStateFlow(0 to 0)
    val startupProgress: StateFlow<Pair<Int, Int>> = _startupProgress.asStateFlow()

    private val _isRefreshingPlaylists = MutableStateFlow(false)
    val isRefreshingPlaylists: StateFlow<Boolean> = _isRefreshingPlaylists.asStateFlow()

    private val _playlistError = MutableStateFlow<String?>(null)
    val playlistError: StateFlow<String?> = _playlistError.asStateFlow()

    var resolvedSource: VideoSource? = null

    fun cancelVerification() {
    }

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
    val selectedLanguage: String = "",
    val resolvedSource: VideoSource? = null
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

    private fun expandStreamUrls(urls: List<String>): List<String> {
        val result = mutableListOf<String>()
        for (rawUrl in urls.filter { it.isNotBlank() }) {
            val url = rawUrl.trim()
            if (!result.contains(url)) {
                result.add(url)
            }
        }
        return result
    }

    fun playChannelWithSource(channel: ChannelEntity, sourceIndex: Int, allChannels: List<ChannelEntity> = emptyList()) {
        _mediaType.value = PlayerMediaType.LIVE_CHANNEL
        _vodExtractionError.value = null
        _currentChannel.value = channel
        if (allChannels.isNotEmpty()) {
            _channelList.value = allChannels
        }
        val urls = expandStreamUrls(channel.getStreamUrls().filter { it.isNotBlank() })
        _currentSources.value = urls
        _videoSources.value = emptyList()
        val idx = sourceIndex.coerceIn(0, (urls.size - 1).coerceAtLeast(0))
        _currentSourceIndex.value = idx
        _streamTitle.value = channel.name
        _failoverMessage.value = if (urls.size > 1) "Kaynak ${idx + 1}/${urls.size} Açıldı" else null
        val targetUrl = urls.getOrNull(idx) ?: ""
        val isM3u = targetUrl.contains(".m3u8", ignoreCase = true) || targetUrl.contains(".txt", ignoreCase = true) || targetUrl.contains("hls", ignoreCase = true)
        _currentStream.value = VideoSource(
            url = targetUrl,
            quality = "Direct",
            mimeType = if (isM3u) androidx.media3.common.MimeTypes.APPLICATION_M3U8 else null,
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
        val expanded = expandStreamUrls(urls.filter { it.isNotBlank() })
        _currentSources.value = expanded
        _videoSources.value = emptyList()
        _currentSourceIndex.value = 0
        _failoverMessage.value = null
        val firstUrl = expanded.firstOrNull() ?: ""
        val isM3u = firstUrl.contains(".m3u8", ignoreCase = true) || firstUrl.contains(".txt", ignoreCase = true) || firstUrl.contains("hls", ignoreCase = true)
        _currentStream.value = VideoSource(
            url = firstUrl,
            quality = "Direct",
            mimeType = if (isM3u) androidx.media3.common.MimeTypes.APPLICATION_M3U8 else null,
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
            _currentStream.value = nextVideoSource.copy(timestamp = System.currentTimeMillis())
            _showSourceMenu.value = false
            _failoverMessage.value = "Sunucu ${index + 1}/${vSources.size} Seçildi"
        } else if (sources.isNotEmpty() && index in sources.indices) {
            _currentSourceIndex.value = index
            val nextUrl = sources[index]
            val isM3u = nextUrl.contains(".m3u8", ignoreCase = true) || nextUrl.contains(".txt", ignoreCase = true) || nextUrl.contains("hls", ignoreCase = true)
            _currentStream.value = VideoSource(
                url = nextUrl,
                quality = "Direct",
                mimeType = if (isM3u) androidx.media3.common.MimeTypes.APPLICATION_M3U8 else null,
                timestamp = System.currentTimeMillis()
            )
            _showSourceMenu.value = false
            _failoverMessage.value = if (sources.size > 1) "Kaynak ${index + 1}/${sources.size} Seçildi" else "Yayın Bağlanıyor..."
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
        val vSources = _videoSources.value
        val curIdx = _currentSourceIndex.value
        if (vSources.isNotEmpty()) {
            val nextIdx = curIdx + 1
            if (nextIdx in vSources.indices) {
                selectSourceIndex(nextIdx)
                _failoverMessage.value = "Alternatif Video Sunucusu ${nextIdx + 1}/${vSources.size} Açıldı"
                return true
            }
        }
        val sources = _currentSources.value
        val nextIdx = curIdx + 1
        if (nextIdx in sources.indices) {
            selectSourceIndex(nextIdx)
            _failoverMessage.value = "Yedek Kaynak ${nextIdx + 1}/${sources.size} Açıldı"
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
        resumePositionMs: Long = 0L,
        resolvedSource: VideoSource? = null
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

    fun hideAutoClosingMenus() {
        _isControlsVisible.value = false
        _showAudioSubMenu.value = false
        _showSourceMenu.value = false
        _showChannelListMenu.value = false
    }
}

/** VOD kaynaklarını ana iş parçacığını engellemeden UI'ya sunar. */

data class FilterOption(val label: String, val value: String)

class TvVodViewModel(application: Application) : AndroidViewModel(application) {

    val movieGenres = androidx.compose.runtime.mutableStateListOf<FilterOption>()
    val movieYears = androidx.compose.runtime.mutableStateListOf<FilterOption>()
    val movieSortOptions = androidx.compose.runtime.mutableStateListOf<FilterOption>()
    val selectedMovieGenre = kotlinx.coroutines.flow.MutableStateFlow<FilterOption?>(null)
    val selectedMovieYear = kotlinx.coroutines.flow.MutableStateFlow<FilterOption?>(null)
    val selectedMovieSort = kotlinx.coroutines.flow.MutableStateFlow<FilterOption?>(null)

    val seriesGenres = androidx.compose.runtime.mutableStateListOf<FilterOption>()
    val seriesYears = androidx.compose.runtime.mutableStateListOf<FilterOption>()
    val seriesSortOptions = androidx.compose.runtime.mutableStateListOf<FilterOption>()
    val selectedSeriesGenre = kotlinx.coroutines.flow.MutableStateFlow<FilterOption?>(null)
    val selectedSeriesYear = kotlinx.coroutines.flow.MutableStateFlow<FilterOption?>(null)
    val selectedSeriesSort = kotlinx.coroutines.flow.MutableStateFlow<FilterOption?>(null)

    private var filterJob: Job? = null

    init {
        movieGenres.addAll(
            listOf(
                FilterOption("Tümü", "tum"),
                FilterOption("Aksiyon", "aksiyon-izle-1"),
                FilterOption("Macera", "macera-izle"),
                FilterOption("Animasyon", "animasyon-izle"),
                FilterOption("Komedi", "komedi-izle-2"),
                FilterOption("Korku", "korku-izle-2"),
                FilterOption("Bilim Kurgu", "bilim-kurgu-izle"),
                FilterOption("Gerilim", "gerilim-izle-1"),
                FilterOption("Dram", "dram-izle-2"),
                FilterOption("Suç", "suc-izle"),
                FilterOption("Gizem", "gizem-izle"),
                FilterOption("Fantastik", "fantastik-izle"),
                FilterOption("Romantik", "romantik-izle-1"),
                FilterOption("Aile", "aile-izle-1"),
                FilterOption("Savaş", "savas-izle"),
                FilterOption("Tarih", "tarih-izle-1"),
                FilterOption("Belgesel", "belgesel-izle"),
                FilterOption("Western", "western-izle"),
                FilterOption("Biyografi", "biyografi-izle-1"),
                FilterOption("Yerli Film", "yerli-film-izle-3"),
                FilterOption("Türkçe Dublaj", "turkce-dublaj-filmler-izle-1"),
                FilterOption("IMDb En İyiler", "imdb-en-iyiler-izle")
            )
        )
        movieYears.addAll(
            listOf(
                FilterOption("Tümü", "tum"),
                FilterOption("2026", "2026"),
                FilterOption("2025", "2025"),
                FilterOption("2024", "2024"),
                FilterOption("2023", "2023"),
                FilterOption("2022", "2022"),
                FilterOption("2021", "2021"),
                FilterOption("2020", "2020"),
                FilterOption("2019", "2019"),
                FilterOption("2018", "2018"),
                FilterOption("2017", "2017"),
                FilterOption("2016", "2016"),
                FilterOption("2015", "2015"),
                FilterOption("2014", "2014"),
                FilterOption("2010", "2010"),
                FilterOption("2000", "2000")
            )
        )
        movieSortOptions.addAll(
            listOf(
                FilterOption("Yeni Eklenenler", "yeni-eklenenler"),
                FilterOption("Tavsiye Filmler", "tavsiye-filmler"),
                FilterOption("Imdb 7+ Filmler", "imdb-7"),
                FilterOption("En Çok Yorumlananlar", "en-cok-yorumlananlar"),
                FilterOption("En Çok Beğenilenler", "en-cok-begenilenler"),
                FilterOption("Yıl (Yeniden Eskiye)", "year_desc"),
                FilterOption("Yıl (Eskiden Yeniye)", "year_asc"),
                FilterOption("A'dan Z'ye", "title_asc")
            )
        )

        seriesGenres.addAll(
            listOf(
                FilterOption("Tümü", "tum"),
                FilterOption("Aksiyon", "aksiyon"),
                FilterOption("Animasyon", "animasyon"),
                FilterOption("Bilim Kurgu", "bilim-kurgu"),
                FilterOption("Komedi", "komedi"),
                FilterOption("Suç", "suc"),
                FilterOption("Belgesel", "belgesel"),
                FilterOption("Dram", "dram"),
                FilterOption("Aile", "aile"),
                FilterOption("Fantastik", "fantastik"),
                FilterOption("Gizem", "gizem"),
                FilterOption("Romantik", "romantik"),
                FilterOption("Gerilim", "gerilim"),
                FilterOption("Savaş", "savas"),
                FilterOption("Western", "western"),
                FilterOption("Yerli Dizi", "yerli-dizi"),
                FilterOption("Kore Dizileri", "kore-dizileri"),
                FilterOption("Anime", "anime")
            )
        )
        seriesYears.addAll(
            listOf(
                FilterOption("Tümü", "tum"),
                FilterOption("2026", "2026"),
                FilterOption("2025", "2025"),
                FilterOption("2024", "2024"),
                FilterOption("2023", "2023"),
                FilterOption("2022", "2022"),
                FilterOption("2021", "2021"),
                FilterOption("2020", "2020"),
                FilterOption("2019", "2019"),
                FilterOption("2018", "2018"),
                FilterOption("2017", "2017"),
                FilterOption("2016", "2016"),
                FilterOption("2015", "2015"),
                FilterOption("2014", "2014"),
                FilterOption("2010", "2010"),
                FilterOption("2000", "2000")
            )
        )
        seriesSortOptions.addAll(
            listOf(
                FilterOption("Varsayılan (En Yeni)", "default"),
                FilterOption("En Popüler", "popular"),
                FilterOption("En Yüksek IMDb", "imdb"),
                FilterOption("Yıl (Yeniden Eskiye)", "year_desc"),
                FilterOption("Yıl (Eskiden Yeniye)", "year_asc"),
                FilterOption("İsim (A-Z)", "title_asc"),
                FilterOption("İsim (Z-A)", "title_desc")
            )
        )
    }

    fun selectMovieGenre(genre: FilterOption?) {
        if (selectedMovieGenre.value == genre || genre?.value == "tum") {
            selectedMovieGenre.value = null
        } else {
            selectedMovieGenre.value = genre
        }
        loadFilteredMovies(1)
    }

    fun selectMovieYear(year: FilterOption?) {
        if (selectedMovieYear.value == year || year?.value == "tum") {
            selectedMovieYear.value = null
        } else {
            selectedMovieYear.value = year
        }
        loadFilteredMovies(1)
    }

    private var tabJob: Job? = null
    private val tabMovieCache = mutableMapOf<Pair<String, Int>, List<MovieItem>>()

    fun selectMovieSort(sort: FilterOption?) {
        if (selectedMovieSort.value == sort || sort?.value == "default" || sort?.value == "yeni-eklenenler") {
            selectedMovieSort.value = null
        } else {
            selectedMovieSort.value = sort
        }
        val genre = selectedMovieGenre.value
        val year = selectedMovieYear.value
        if (genre == null && year == null) {
            val sortKey = selectedMovieSort.value?.value
            if (sortKey in listOf("tavsiye-filmler", "imdb-7", "en-cok-yorumlananlar", "en-cok-begenilenler")) {
                loadTabMovies(sortKey!!, page = 1)
            } else {
                publishMoviePage(reset = true)
            }
        } else {
            loadFilteredMovies(1)
        }
    }

    private fun loadTabMovies(tabKey: String, page: Int) {
        val label = movieSortOptions.firstOrNull { it.value == tabKey }?.label ?: tabKey
        val cached = tabMovieCache[tabKey to page]
        if (cached != null && cached.isNotEmpty()) {
            _movieCatalogPage.value = page
            _movieCatalogPageCount.value = 10
            _movies.value = listOf(tv.newtv.data.models.VodCategory(label, cached))
            _movieCatalogScanStatus.value = "${cached.size} film listelendi"
            return
        }

        tabJob?.cancel()
        tabJob = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _movieCatalogScanStatus.value = "$label getiriliyor…"
            try {
                val items = when (tabKey) {
                    "tavsiye-filmler" -> providerManager.hdfilmcehennemi.getRecommendedMovies(page)
                    "imdb-7" -> providerManager.hdfilmcehennemi.getImdb7Movies(page)
                    "en-cok-yorumlananlar" -> providerManager.hdfilmcehennemi.getMostCommentedMovies(page)
                    "en-cok-begenilenler" -> providerManager.hdfilmcehennemi.getMostLikedMovies(page)
                    else -> emptyList()
                }
                if (items.isNotEmpty()) {
                    tabMovieCache[tabKey to page] = items
                    catalogEngine.saveMovies(items)
                    _movieCatalogPage.value = page
                    _movieCatalogPageCount.value = 10
                    _movies.value = listOf(tv.newtv.data.models.VodCategory(label, items))
                    _movieCatalogScanStatus.value = "${items.size} film listelendi"
                } else {
                    val fallback = when (tabKey) {
                        "imdb-7" -> allMovieCatalog.filter { parseRating(it.rating) >= 7.0 }.sortedByDescending { parseRating(it.rating) }
                        "tavsiye-filmler", "en-cok-begenilenler" -> allMovieCatalog.sortedByDescending { parseRating(it.rating) }
                        "en-cok-yorumlananlar" -> allMovieCatalog.sortedWith(
                            compareByDescending<MovieItem> { parseRating(it.rating) * 1000 + (it.year.toIntOrNull() ?: 0) }
                        )
                        else -> allMovieCatalog
                    }
                    val from = (page - 1) * VodCatalogEngine.PAGE_SIZE
                    val slice = fallback.drop(from).take(VodCatalogEngine.PAGE_SIZE)
                    _movieCatalogPage.value = page
                    _movieCatalogPageCount.value = maxOf(1, (fallback.size + VodCatalogEngine.PAGE_SIZE - 1) / VodCatalogEngine.PAGE_SIZE)
                    _movies.value = if (slice.isEmpty()) emptyList() else listOf(tv.newtv.data.models.VodCategory(label, slice))
                    _movieCatalogScanStatus.value = "${slice.size} film listelendi"
                }
            } catch (e: Exception) {
                _error.value = "Filmler yüklenirken hata oluştu."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectSeriesGenre(genre: FilterOption?) {
        if (selectedSeriesGenre.value == genre || genre?.value == "tum") {
            selectedSeriesGenre.value = null
        } else {
            selectedSeriesGenre.value = genre
        }
        publishSeriesPage(reset = true)
    }

    fun selectSeriesYear(year: FilterOption?) {
        if (selectedSeriesYear.value == year || year?.value == "tum") {
            selectedSeriesYear.value = null
        } else {
            selectedSeriesYear.value = year
        }
        publishSeriesPage(reset = true)
    }

    fun selectSeriesSort(sort: FilterOption?) {
        if (selectedSeriesSort.value == sort || sort?.value == "default") {
            selectedSeriesSort.value = null
        } else {
            selectedSeriesSort.value = sort
        }
        publishSeriesPage(reset = true)
    }

    private fun parseRating(rating: String): Double {
        val clean = Regex("""\b(\d+[\.,]\d+|\d+)\b""").find(rating)?.value?.replace(',', '.') ?: return 0.0
        return clean.toDoubleOrNull() ?: 0.0
    }

    private fun applySorting(list: List<MovieItem>, sortKey: String?): List<MovieItem> {
        return when (sortKey) {
            "popular", "en-cok-yorumlananlar" -> list.sortedWith(
                compareByDescending<MovieItem> {
                    val r = parseRating(it.rating)
                    val y = it.year.toIntOrNull() ?: 0
                    if (r > 0.0) r * 1000 + y else y.toDouble()
                }.thenByDescending { it.year.toIntOrNull() ?: 0 }
            )
            "imdb", "imdb-7", "tavsiye-filmler", "en-cok-begenilenler" -> list.sortedWith(
                compareByDescending<MovieItem> { parseRating(it.rating) }
                    .thenByDescending { it.year.toIntOrNull() ?: 0 }
            )
            "year_desc" -> list.sortedByDescending { it.year.toIntOrNull() ?: 0 }
            "year_asc" -> list.sortedBy {
                val y = it.year.toIntOrNull()
                if (y != null && y > 1900) y else 9999
            }
            "title_asc" -> list.sortedBy { it.title.lowercase(java.util.Locale("tr")) }
            "title_desc" -> list.sortedByDescending { it.title.lowercase(java.util.Locale("tr")) }
            else -> list
        }
    }

    private fun applySeriesSorting(list: List<SeriesItem>, sortKey: String?): List<SeriesItem> {
        return when (sortKey) {
            "popular" -> list.sortedWith(
                compareByDescending<SeriesItem> {
                    val r = parseRating(it.rating)
                    val y = it.year.toIntOrNull() ?: 0
                    if (r > 0.0) r * 1000 + y else y.toDouble()
                }.thenByDescending { it.year.toIntOrNull() ?: 0 }
            )
            "imdb" -> list.sortedWith(
                compareByDescending<SeriesItem> { parseRating(it.rating) }
                    .thenByDescending { it.year.toIntOrNull() ?: 0 }
            )
            "year_desc" -> list.sortedByDescending { it.year.toIntOrNull() ?: 0 }
            "year_asc" -> list.sortedBy {
                val y = it.year.toIntOrNull()
                if (y != null && y > 1900) y else 9999
            }
            "title_asc" -> list.sortedBy { it.title.lowercase(java.util.Locale("tr")) }
            "title_desc" -> list.sortedByDescending { it.title.lowercase(java.util.Locale("tr")) }
            else -> list
        }
    }

    fun clearFilters() {
        selectedMovieGenre.value = null
        selectedMovieYear.value = null
        selectedMovieSort.value = null
        selectedSeriesGenre.value = null
        selectedSeriesYear.value = null
        selectedSeriesSort.value = null
        _movieCatalogPage.value = 1
        _seriesCatalogPage.value = 1
        publishMoviePage(reset = true)
        publishSeriesPage(reset = true)
    }

    fun loadFilteredMovies(page: Int = 1) {
        val genre = selectedMovieGenre.value
        val year = selectedMovieYear.value
        val sort = selectedMovieSort.value

        if (genre == null && year == null) {
            _movieCatalogPage.value = page
            publishMoviePage(reset = false)
            return
        }

        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _movieCatalogScanStatus.value = "Filtrelenmiş filmler getiriliyor…"
            try {
                var items: List<MovieItem> = when {
                    genre != null && year != null -> {
                        providerManager.hdfilmcehennemi.getMoviesByGenreAndYear(genre.value, year.value)
                    }
                    genre != null -> {
                        val p1 = (page - 1) * 2 + 1
                        val p2 = (page - 1) * 2 + 2
                        val p3 = (page - 1) * 2 + 3
                        val f1 = async { runCatching { providerManager.hdfilmcehennemi.getMoviesByGenre(genre.value, p1) }.getOrDefault(emptyList()) }
                        val f2 = async { runCatching { providerManager.hdfilmcehennemi.getMoviesByGenre(genre.value, p2) }.getOrDefault(emptyList()) }
                        val f3 = async { runCatching { providerManager.hdfilmcehennemi.getMoviesByGenre(genre.value, p3) }.getOrDefault(emptyList()) }
                        (f1.await() + f2.await() + f3.await()).distinctBy { it.url }.take(VodCatalogEngine.PAGE_SIZE)
                    }
                    year != null -> {
                        val p1 = (page - 1) * 2 + 1
                        val p2 = (page - 1) * 2 + 2
                        val p3 = (page - 1) * 2 + 3
                        val f1 = async { runCatching { providerManager.hdfilmcehennemi.getMoviesByYear(year.value, p1) }.getOrDefault(emptyList()) }
                        val f2 = async { runCatching { providerManager.hdfilmcehennemi.getMoviesByYear(year.value, p2) }.getOrDefault(emptyList()) }
                        val f3 = async { runCatching { providerManager.hdfilmcehennemi.getMoviesByYear(year.value, p3) }.getOrDefault(emptyList()) }
                        (f1.await() + f2.await() + f3.await()).distinctBy { it.url }.take(VodCatalogEngine.PAGE_SIZE)
                    }
                    else -> emptyList()
                }

                if (sort != null && sort.value != "default") {
                    items = applySorting(items, sort.value)
                }

                if (items.isNotEmpty()) {
                    catalogEngine.saveMovies(items)
                    val filterTitle = listOfNotNull(genre?.label, year?.label, sort?.label).joinToString(" • ")
                    _movies.value = listOf(tv.newtv.data.models.VodCategory(filterTitle, items))
                    _movieCatalogPage.value = page
                    _movieCatalogPageCount.value = 10
                    _movieCatalogScanStatus.value = "${items.size} film listelendi"
                } else {
                    var localFiltered = allMovieCatalog.filter { movie ->
                        val yearMatch = year == null || movie.year == year.value || movie.year.contains(year.value) || movie.url.contains(year.value)
                        yearMatch
                    }
                    if (sort != null && sort.value != "default") {
                        localFiltered = applySorting(localFiltered, sort.value)
                    }
                    if (localFiltered.isNotEmpty()) {
                        val filterTitle = listOfNotNull(genre?.label, year?.label, sort?.label).joinToString(" • ")
                        _movies.value = listOf(tv.newtv.data.models.VodCategory(filterTitle, localFiltered))
                        _movieCatalogPage.value = 1
                        _movieCatalogPageCount.value = 1
                        _movieCatalogScanStatus.value = "${localFiltered.size} film listelendi (Katalog)"
                    } else {
                        _movies.value = emptyList()
                        _movieCatalogScanStatus.value = "Kriterlere uygun film bulunamadı"
                    }
                }
            } catch (e: Exception) {
                _error.value = "Filtreleme sırasında hata oluştu."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }





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

    enum class MovieTab { NEW, RECOMMENDED }
    private val _selectedMovieTab = MutableStateFlow(MovieTab.NEW)
    val selectedMovieTab: StateFlow<MovieTab> = _selectedMovieTab.asStateFlow()

    private val _featuredMovies = MutableStateFlow<List<MovieItem>>(emptyList())
    val featuredMovies: StateFlow<List<MovieItem>> = _featuredMovies.asStateFlow()

    private val _recommendedMovies = MutableStateFlow<List<MovieItem>>(emptyList())
    val recommendedMovies: StateFlow<List<MovieItem>> = _recommendedMovies.asStateFlow()

    fun selectMovieTab(tab: MovieTab) {
        if (_selectedMovieTab.value == tab) return
        _selectedMovieTab.value = tab
        if (tab == MovieTab.RECOMMENDED && _recommendedMovies.value.isEmpty()) {
            viewModelScope.launch {
                val rec = runCatching { providerManager.hdfilmcehennemi.getRecommendedMovies() }.getOrDefault(emptyList())
                if (rec.isNotEmpty()) {
                    _recommendedMovies.value = rec
                    catalogEngine.saveMovies(rec)
                }
                publishMoviePage(reset = true)
            }
        }
        publishMoviePage(reset = true)
    }

    fun loadFeaturedMovies() {
        viewModelScope.launch {
            if (_featuredMovies.value.isEmpty()) {
                val featured = runCatching { providerManager.hdfilmcehennemi.getFeaturedMovies() }.getOrDefault(emptyList())
                if (featured.isNotEmpty()) {
                    _featuredMovies.value = featured
                    catalogEngine.saveMovies(featured)
                }
            }
        }
    }

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
        if (!force && allMovieCatalog.isNotEmpty()) {
            publishMoviePage()
            return
        }
        if (movieScanJob?.isActive == true) {
            if (force) movieScanJob?.cancel() else return
        }
        movieScanJob = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            loadFeaturedMovies()
            allMovieCatalog = catalogEngine.movies()
            val hasCached = allMovieCatalog.size >= VodCatalogEngine.PAGE_SIZE
            if (hasCached) {
                publishMoviePage()
                _movieCatalogScanStatus.value = "${allMovieCatalog.size} film hazır"
            } else {
                _movieCatalogScanStatus.value = "Film kaynakları taranıyor…"
            }

            runCatching {
                catalogEngine.scanMovies(quickSync = hasCached) { provider, count ->
                    _movieCatalogScanStatus.value = "$provider: $count içerik"
                    allMovieCatalog = catalogEngine.movies()
                    if (hasCached || allMovieCatalog.size >= VodCatalogEngine.PAGE_SIZE) {
                        publishMoviePage()
                    }
                }
            }

            allMovieCatalog = catalogEngine.movies()
            publishMoviePage()
            _movieCatalogScanStatus.value = "${allMovieCatalog.size} film hazır"
            if (allMovieCatalog.isEmpty()) _error.value = "Film kaynaklarına şu anda ulaşılamıyor. Yeniden deneyin."
            _isLoading.value = false
        }
    }

    fun loadSeries(force: Boolean = false, nextPage: Boolean = false) {
        if (nextPage) {
            setSeriesCatalogPage(_seriesCatalogPage.value + 1)
            return
        }
        if (!force && allSeriesCatalog.isNotEmpty()) {
            publishSeriesPage()
            return
        }
        if (seriesScanJob?.isActive == true) {
            if (force) seriesScanJob?.cancel() else return
        }
        seriesScanJob = viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            allSeriesCatalog = catalogEngine.series()
            val hasCached = allSeriesCatalog.size >= VodCatalogEngine.PAGE_SIZE
            if (hasCached) {
                publishSeriesPage()
                _seriesCatalogScanStatus.value = "${allSeriesCatalog.size} dizi hazır"
            } else {
                _seriesCatalogScanStatus.value = "Dizi kaynakları taranıyor…"
            }

            runCatching {
                catalogEngine.scanSeries(quickSync = hasCached) { provider, count ->
                    _seriesCatalogScanStatus.value = "$provider: $count içerik"
                    allSeriesCatalog = catalogEngine.series()
                    if (hasCached || allSeriesCatalog.size >= VodCatalogEngine.PAGE_SIZE) {
                        publishSeriesPage()
                    }
                }
            }

            allSeriesCatalog = catalogEngine.series()
            publishSeriesPage()
            _seriesCatalogScanStatus.value = "${allSeriesCatalog.size} dizi hazır"
            if (allSeriesCatalog.isEmpty()) _error.value = "Dizi kaynaklarına şu anda ulaşılamıyor. Yeniden deneyin."
            _isLoading.value = false
        }
    }

    fun setMovieCatalogPage(page: Int) {
        val genre = selectedMovieGenre.value
        val year = selectedMovieYear.value
        val sortKey = selectedMovieSort.value?.value
        if (genre != null || year != null) {
            loadFilteredMovies(page)
        } else if (sortKey in listOf("tavsiye-filmler", "imdb-7", "en-cok-yorumlananlar", "en-cok-begenilenler")) {
            loadTabMovies(sortKey!!, page)
        } else {
            _movieCatalogPage.value = page.coerceIn(1, _movieCatalogPageCount.value)
            publishMoviePage()
        }
    }

    fun setSeriesCatalogPage(page: Int) {
        _seriesCatalogPage.value = page.coerceIn(1, _seriesCatalogPageCount.value)
        publishSeriesPage()
    }

    private fun publishMoviePage(reset: Boolean = false) {
        val genre = selectedMovieGenre.value
        val year = selectedMovieYear.value
        if (genre != null || year != null) {
            return
        }
        val sort = selectedMovieSort.value
        val sortKey = sort?.value
        if (sortKey in listOf("tavsiye-filmler", "imdb-7", "en-cok-yorumlananlar", "en-cok-begenilenler")) {
            loadTabMovies(sortKey!!, if (reset) 1 else _movieCatalogPage.value)
            return
        }
        val sourceList = if (sort != null && sort.value != "default" && sort.value != "yeni-eklenenler") {
            applySorting(allMovieCatalog, sort.value)
        } else {
            allMovieCatalog
        }
        val pageCount = maxOf(1, (sourceList.size + VodCatalogEngine.PAGE_SIZE - 1) / VodCatalogEngine.PAGE_SIZE)
        _movieCatalogPageCount.value = pageCount
        if (reset) _movieCatalogPage.value = 1
        val page = _movieCatalogPage.value.coerceIn(1, pageCount)
        val from = (page - 1) * VodCatalogEngine.PAGE_SIZE
        val items = sourceList.drop(from).take(VodCatalogEngine.PAGE_SIZE)
        val titleText = if (sort != null && sort.value != "yeni-eklenenler" && sort.value != "default") {
            "Filmler • ${sort.label}"
        } else {
            "Yeni Eklenenler"
        }
        _movies.value = if (items.isEmpty()) emptyList() else listOf(tv.newtv.data.models.VodCategory(titleText, items))
    }

    private fun publishSeriesPage(reset: Boolean = false) {
        val genre = selectedSeriesGenre.value
        val year = selectedSeriesYear.value
        val sort = selectedSeriesSort.value

        var sourceList = allSeriesCatalog
        if (genre != null && genre.value != "tum") {
            val gVal = genre.value.lowercase(java.util.Locale("tr"))
            val gLabel = genre.label.lowercase(java.util.Locale("tr"))
            sourceList = sourceList.filter { s ->
                val u = s.url.lowercase()
                val t = s.title.lowercase(java.util.Locale("tr"))
                u.contains(gVal) || t.contains(gLabel) || s.languages.any { it.contains(gLabel, ignoreCase = true) }
            }
        }
        if (year != null && year.value != "tum") {
            val yVal = year.value
            sourceList = sourceList.filter { s ->
                s.year == yVal || s.year.contains(yVal) || s.url.contains(yVal)
            }
        }
        if (sort != null && sort.value != "default") {
            sourceList = applySeriesSorting(sourceList, sort.value)
        }

        val pageCount = maxOf(1, (sourceList.size + VodCatalogEngine.PAGE_SIZE - 1) / VodCatalogEngine.PAGE_SIZE)
        _seriesCatalogPageCount.value = pageCount
        if (reset) _seriesCatalogPage.value = 1
        val page = _seriesCatalogPage.value.coerceIn(1, pageCount)
        val from = (page - 1) * VodCatalogEngine.PAGE_SIZE
        val items = sourceList.drop(from).take(VodCatalogEngine.PAGE_SIZE)
        val titleText = when {
            genre != null || year != null || (sort != null && sort.value != "default") -> {
                listOfNotNull(genre?.label, year?.label, sort?.label).joinToString(" • ")
            }
            else -> "Tüm Diziler"
        }
        _series.value = if (items.isEmpty()) emptyList() else listOf(tv.newtv.data.models.VodCategory(titleText, items))
    }
    
    fun openMovie(item: MovieItem) = load {
        _movieDetail.value = null
        _seriesDetail.value = null
        val candidates = (listOf(item) + catalogEngine.movieSources(item.title)).distinctBy { it.url }
        val resolved = mutableListOf<Pair<MovieItem, MovieDetail>>()
        for (candidate in candidates) {
            val scraper = providerManager.getMovieScraperForUrl(candidate.url)
            val detail = runCatching { scraper.getMovieDetail(candidate.url) }.getOrNull() ?: continue
            val playable = detail.iframes.isNotEmpty() || detail.dubbingIframes.isNotEmpty() ||
                detail.subtitleIframes.isNotEmpty() || (detail.isSeries && detail.seasons.isNotEmpty()) ||
                detail.hasDub || detail.hasSubtitle
            if (playable) {
                resolved += candidate to detail
                break
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
                posterUrl = selectedItem.posterUrl.ifBlank { detail.posterUrl },
                seasons = detail.seasons,
                provider = detail.provider.ifEmpty { selectedItem.provider },
                languages = detail.languages.ifEmpty { selectedItem.languages },
                sourceUrl = selectedItem.url
            )
        } else {
            _seriesDetail.value = null
            _movieDetail.value = detail.copy(
                posterUrl = selectedItem.posterUrl.ifBlank { detail.posterUrl },
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
        _seriesDetail.value = null // Dizi detayını temizle (Yeniden tıklamada StateFlow tetiklensin)
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
            posterUrl = sourceItem.posterUrl.ifBlank { firstDetail.posterUrl },
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
