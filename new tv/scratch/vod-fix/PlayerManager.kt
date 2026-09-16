package tv.newtv.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import tv.newtv.network.OkHttpClientProvider

@OptIn(UnstableApi::class)
class PlayerManager(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null
    private val mediaClient = OkHttpClientProvider.getMediaOkHttpClient()

    private var currentSources: List<String> = emptyList()
    private var currentSourceIndex: Int = 0
    private var cachedHeaders: Map<String, String> = emptyMap()
    private var cachedSubtitles: List<tv.newtv.data.models.Subtitle> = emptyList()
    private var currentIsLive: Boolean = false
    private var forcedMimeType: String? = null

    companion object {
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }

    fun initializePlayer(isLive: Boolean = false): ExoPlayer {
        // 1. Geniş Kodek Desteği (AC3, EAC3, AAC, HEVC)
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        // 2. Performans Ayarlarından Dinamik Tamponlama
        val loadControl = if (isLive) {
            val settings = tv.newtv.data.local.IptvSettingsManager.getSettings(context)
            val minBufferMs = settings.liveCachingMs.coerceIn(1000, 20000)
            val maxBufferMs = (settings.targetBufferSec * 1000).coerceIn(minBufferMs + 5000, 120000)
            val bufferForPlaybackMs = (settings.networkCachingMs / 2).coerceIn(1000, 5000)
            val bufferForPlaybackAfterRebufferMs = settings.networkCachingMs.coerceIn(2000, 10000)
            
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    minBufferMs,
                    maxBufferMs,
                    bufferForPlaybackMs,
                    bufferForPlaybackAfterRebufferMs
                )
                .setBackBuffer(30000, true)
                .build()
        } else {
            // VOD (Film/Dizi) için çok daha agresif tamponlama (Sürekli duraklamayı önler)
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    32000,   // Min buffer: En az 32 saniye indirmeden rahat etmez
                    120000,  // Max buffer: İleriye dönük 2 dakikaya kadar indirir
                    2500,    // Oynatmaya başlamak için 2.5 sn yeterli
                    5000     // Rebuffer (takılma) sonrası devam etmek için 5 sn gerekli
                )
                .setBackBuffer(60000, true) // Geri sarmalar için 60 sn geçmişi tut
                .build()
        }

        exoPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .build()

        return exoPlayer!!
    }

    fun getPlayer(): ExoPlayer? = exoPlayer

    /**
     * Çoklu yedek kaynaklı akış oynatma (Kodi StreamScope failover mantığı)
     */
    fun playStreamWithFailover(
        urls: List<String>,
        customHeaders: Map<String, String> = emptyMap(),
        subtitles: List<tv.newtv.data.models.Subtitle> = emptyList(),
        isLive: Boolean = false,
        mimeType: String? = null
    ) {
        currentSources = urls.filter { it.isNotBlank() }
        currentSourceIndex = 0
        cachedHeaders = customHeaders
        cachedSubtitles = subtitles
        currentIsLive = isLive
        forcedMimeType = mimeType
        playCurrentSource()
    }

    /**
     * Tekil URL oynatma (Film/Dizi ve geriye dönük uyumluluk)
     */
    fun playStream(
        streamUrl: String,
        customHeaders: Map<String, String> = emptyMap(),
        subtitles: List<tv.newtv.data.models.Subtitle> = emptyList(),
        isLive: Boolean = false,
        mimeType: String? = null
    ) {
        playStreamWithFailover(listOf(streamUrl), customHeaders, subtitles, isLive, mimeType)
    }

    /**
     * Hata durumunda bir sonraki yedek kaynağa otomatik geçiş yapar.
     * @return Başka yedek kaynak varsa true, tüm kaynaklar bittiyse false
     */
    fun switchToNextSource(): Boolean {
        if (currentSourceIndex + 1 < currentSources.size) {
            currentSourceIndex++
            playCurrentSource()
            return true
        }
        return false
    }

    /**
     * Mevcut kaynağı yeniden dene
     */
    fun retryCurrentSource() {
        if (currentSources.isNotEmpty() && currentSourceIndex in currentSources.indices) {
            playCurrentSource()
        }
    }

    /**
     * Tüm kaynakları baştan (1. kaynaktan) yeniden denemek için
     */
    fun retryFirstSource() {
        if (currentSources.isNotEmpty()) {
            currentSourceIndex = 0
            playCurrentSource()
        }
    }

    /**
     * Belirli bir yedek kaynağa manuel olarak geçiş yapar
     */
    fun playSourceAtIndex(index: Int) {
        if (currentSources.isNotEmpty() && index in currentSources.indices) {
            currentSourceIndex = index
            playCurrentSource()
        }
    }

    fun getCurrentSourceIndex(): Int = currentSourceIndex
    fun getTotalSources(): Int = currentSources.size
    fun getSources(): List<String> = currentSources
    fun getCurrentSourceUrl(): String? = currentSources.getOrNull(currentSourceIndex)

    data class TrackInfo(
        val groupIndex: Int,
        val trackIndex: Int,
        val label: String,
        val language: String?,
        val isSelected: Boolean
    )

    fun getAudioTracks(): List<TrackInfo> {
        val player = exoPlayer ?: return emptyList()
        val tracks = player.currentTracks
        val list = mutableListOf<TrackInfo>()
        for (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            if (group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO) {
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    val isSelected = group.isTrackSelected(trackIndex)
                    val lang = format.language?.uppercase() ?: ""
                    val rawLabel = format.label ?: if (lang.isNotEmpty()) "Ses ($lang)" else "Ses İzi ${list.size + 1}"
                    list.add(TrackInfo(groupIndex, trackIndex, rawLabel, format.language, isSelected))
                }
            }
        }
        return list
    }

    fun selectAudioTrack(groupIndex: Int, trackIndex: Int) {
        val player = exoPlayer ?: return
        val tracks = player.currentTracks
        if (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(
                    androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex))
                )
                .build()
        }
    }

    fun getSubtitleTracks(): List<TrackInfo> {
        val player = exoPlayer ?: return emptyList()
        val tracks = player.currentTracks
        val list = mutableListOf<TrackInfo>()
        for (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            if (group.type == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    val isSelected = group.isTrackSelected(trackIndex)
                    val lang = format.language?.uppercase() ?: ""
                    val rawLabel = format.label ?: if (lang.isNotEmpty()) "Altyazı ($lang)" else "Altyazı ${list.size + 1}"
                    list.add(TrackInfo(groupIndex, trackIndex, rawLabel, format.language, isSelected))
                }
            }
        }
        return list
    }

    fun selectSubtitleTrack(groupIndex: Int?, trackIndex: Int?) {
        val player = exoPlayer ?: return
        if (groupIndex == null || trackIndex == null) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                .build()
            return
        }
        val tracks = player.currentTracks
        if (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(
                    androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex))
                )
                .build()
        }
    }

    data class VideoInfo(
        val width: Int,
        val height: Int,
        val frameRate: Float,
        val videoMime: String?,
        val audioMime: String?,
        val bufferedDurationMs: Long,
        val bitrate: Int
    )

    fun getVideoInfo(): VideoInfo? {
        val player = exoPlayer ?: return null
        val vf = player.videoFormat
        return VideoInfo(
            width = vf?.width ?: 0,
            height = vf?.height ?: 0,
            frameRate = vf?.frameRate ?: 0f,
            videoMime = vf?.sampleMimeType,
            audioMime = player.audioFormat?.sampleMimeType,
            bufferedDurationMs = player.totalBufferedDuration,
            bitrate = vf?.bitrate ?: 0
        )
    }

    private fun playCurrentSource() {
        val player = exoPlayer ?: return
        if (currentSources.isEmpty() || currentSourceIndex !in currentSources.indices) return

        val streamUrl = currentSources[currentSourceIndex]
        var cleanUrl = streamUrl.trim()
        val headers = mutableMapOf(
            "User-Agent" to DEFAULT_USER_AGENT,
            "Accept" to "*/*"
        )

        // Kodi formatındaki pipe (|) başlıklarını ayrıştır (Örn: http://stream.m3u8|User-Agent=...)
        if (cleanUrl.contains("|")) {
            val parts = cleanUrl.split("|", limit = 2)
            cleanUrl = parts[0].trim()
            val queryHeaders = parts[1].split("&")
            for (header in queryHeaders) {
                val kv = header.split("=", limit = 2)
                if (kv.size == 2) {
                    headers[kv[0].trim()] = kv[1].trim()
                }
            }
        }

        headers.putAll(cachedHeaders)

        // 3. OkHttp Tabanlı Veri Kaynağı Fabrikası (Temiz akış istemcisi)
        val dataSourceFactory = OkHttpDataSource.Factory(mediaClient)
            .setUserAgent(headers["User-Agent"] ?: DEFAULT_USER_AGENT)
            .setDefaultRequestProperties(headers)

        // 4. Medya Formatı Tespiti (HLS m3u8, Pichive m.php ve Canlı TV tespiti)
        val isM3u8 = forcedMimeType == MimeTypes.APPLICATION_M3U8 ||
                     cleanUrl.contains(".m3u8", ignoreCase = true) ||
                     cleanUrl.contains("m.php", ignoreCase = true) ||
                     cleanUrl.contains("pichive", ignoreCase = true) ||
                     cleanUrl.contains("hls", ignoreCase = true) ||
                     cleanUrl.contains("format=m3u8", ignoreCase = true) ||
                     cleanUrl.contains("type=m3u8", ignoreCase = true) ||
                     currentIsLive

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(Uri.parse(cleanUrl))

        if (isM3u8) {
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
        } else if (cleanUrl.contains(".mp4", ignoreCase = true) || forcedMimeType == MimeTypes.VIDEO_MP4) {
            mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP4)
        }

        // Yalnızca ve yalnızca Canlı TV yayınları için LiveConfiguration uygula! VOD için ASLA uygulama!
        if (currentIsLive) {
            mediaItemBuilder.setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setMaxPlaybackSpeed(1.02f)
                    .setMinPlaybackSpeed(0.98f)
                    .build()
            )
        }

        // Harici altyazıları ekle
        if (cachedSubtitles.isNotEmpty()) {
            val subtitleConfigs = cachedSubtitles.map { sub ->
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                    .setMimeType(if (sub.url.endsWith(".vtt", ignoreCase = true)) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP)
                    .setLanguage(sub.language)
                    .setLabel(sub.language)
                    .setSelectionFlags(if (sub.isDefault) androidx.media3.common.C.SELECTION_FLAG_DEFAULT else 0)
                    .build()
            }
            mediaItemBuilder.setSubtitleConfigurations(subtitleConfigs)
        }

        val mediaItem = mediaItemBuilder.build()

        // KRİTİK: Media3'ün HLS ve DASH modüllerini tanıyabilmesi için Context ile oluşturulmalı!
        val mediaSource = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(4))
            .createMediaSource(mediaItem)

        player.stop()
        player.clearMediaItems()
        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = true
    }

    /**
     * Canlı yayında pencerenin gerisine düşüldüğünde hemen canlı kenara atlayıp yayını kurtarır
     */
    fun recoverLiveStream() {
        val player = exoPlayer ?: return
        try {
            player.seekToDefaultPosition()
            player.prepare()
            player.playWhenReady = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addListener(listener: Player.Listener) {
        exoPlayer?.addListener(listener)
    }

    fun removeListener(listener: Player.Listener) {
        exoPlayer?.removeListener(listener)
    }

    fun pausePlayer() {
        exoPlayer?.playWhenReady = false
    }

    fun releasePlayer() {
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        exoPlayer?.release()
        exoPlayer = null
    }
}
