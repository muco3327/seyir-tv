package tv.newtv.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import tv.newtv.data.local.IptvSettingsManager
import tv.newtv.data.models.Subtitle
import tv.newtv.network.OkHttpClientProvider
import java.util.concurrent.TimeUnit

/**
 * FİLM VE DİZİ OYNATICI MOTORU (VodPlayerManager)
 * 
 * Film ve Dizi (VOD) oynatımları için özel olarak optimize edilmiştir.
 * - Film/Dizi kazıyıcı (Scraper) HTTP başlıkları ve Interceptor'ları (Fastplay, Rapidrame) destekler.
 * - Harici altyazı (SRT, VTT) entegrasyonu sunar.
 * - Yüksek çözünürlüklü VOD akışları için 64MB hedef tampon alanı kullanır.
 * - Kaldığı yerden devam etme (Resume position) ve arama (Seeking) desteği sağlar.
 */
@OptIn(UnstableApi::class)
class VodPlayerManager(private val context: Context) : ITvPlayer {

    private var exoPlayer: ExoPlayer? = null
    private val mediaClient = OkHttpClientProvider.getMediaOkHttpClient()

    private var currentSources: List<String> = emptyList()
    private var currentSourceIndex: Int = 0
    private var cachedHeaders: Map<String, String> = emptyMap()
    private var cachedSubtitles: List<Subtitle> = emptyList()
    private var forcedMimeType: String? = null
    private var fallbackSeekPositionMs: Long = -1L
    private val listeners = mutableListOf<Player.Listener>()

    companion object {
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }

    override fun setFallbackSeekPosition(posMs: Long) {
        fallbackSeekPositionMs = posMs
    }

    override fun initializePlayer(): ExoPlayer {
        if (exoPlayer != null) return exoPlayer!!

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)

        // VOD için yüksek bitrateli filmlerde takılmayı önleyen geniş tampon
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(45000, 90000, 3000, 12000)
            .setTargetBufferBytes(64 * 1024 * 1024)
            .setBackBuffer(10000, false)
            .build()

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setExceedRendererCapabilitiesIfNecessary(true))
        }

        exoPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build().apply {
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            }

        for (l in listeners) {
            exoPlayer?.addListener(l)
        }

        return exoPlayer!!
    }

    override fun getPlayer(): ExoPlayer? = exoPlayer

    override fun playStream(
        streamUrl: String,
        customHeaders: Map<String, String>,
        subtitles: List<Subtitle>,
        isLive: Boolean,
        mimeType: String?
    ) {
        playStreamWithFailover(listOf(streamUrl), customHeaders, subtitles, isLive, mimeType, 0)
    }

    override fun playStreamWithFailover(
        urls: List<String>,
        customHeaders: Map<String, String>,
        subtitles: List<Subtitle>,
        isLive: Boolean,
        mimeType: String?,
        startIndex: Int
    ) {
        val cleanList = urls.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        currentSources = cleanList
        currentSourceIndex = startIndex.coerceIn(0, (cleanList.size - 1).coerceAtLeast(0))
        cachedHeaders = customHeaders
        cachedSubtitles = subtitles
        forcedMimeType = mimeType
        playCurrentSource()
    }

    private fun playCurrentSource() {
        val player = exoPlayer ?: initializePlayer()
        if (currentSources.isEmpty() || currentSourceIndex !in currentSources.indices) return

        val streamUrl = currentSources[currentSourceIndex]
        var cleanUrl = streamUrl.trim()
        val headers = mutableMapOf(
            "User-Agent" to DEFAULT_USER_AGENT,
            "Accept" to "*/*"
        )

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

        cleanUrl = cleanUrl.replace(" ", "%20")
        headers.putAll(cachedHeaders)

        val perfSettings = IptvSettingsManager.getSettings(context)
        val connectTimeout = perfSettings.timeoutSec.coerceIn(3, 15).toLong()
        val readTimeout = (perfSettings.timeoutSec + 3).coerceIn(5, 20).toLong()
        val customMediaClient = mediaClient.newBuilder()
            .connectTimeout(connectTimeout, TimeUnit.SECONDS)
            .readTimeout(readTimeout, TimeUnit.SECONDS)
            .build()

        val dataSourceFactory = OkHttpDataSource.Factory(customMediaClient)
            .setUserAgent(headers["User-Agent"] ?: DEFAULT_USER_AGENT)
            .setDefaultRequestProperties(headers)

        val mediaItemBuilder = MediaItem.Builder().setUri(Uri.parse(cleanUrl))

        val isM3u8 = forcedMimeType == MimeTypes.APPLICATION_M3U8 ||
                     cleanUrl.contains(".m3u8", ignoreCase = true) ||
                     cleanUrl.contains(".txt", ignoreCase = true) ||
                     cleanUrl.contains("hls", ignoreCase = true) ||
                     cleanUrl.contains("manifest", ignoreCase = true) ||
                     cleanUrl.contains("fastplay", ignoreCase = true)

        if (isM3u8) {
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
        } else if (cleanUrl.contains(".mp4", ignoreCase = true) || forcedMimeType == MimeTypes.VIDEO_MP4) {
            mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP4)
        }

        // Harici altyazıları ekle
        if (cachedSubtitles.isNotEmpty()) {
            val subtitleConfigs = cachedSubtitles.map { sub ->
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                    .setMimeType(if (sub.url.endsWith(".vtt", ignoreCase = true)) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP)
                    .setLanguage(sub.language)
                    .setLabel(sub.language)
                    .setSelectionFlags(if (sub.isDefault) C.SELECTION_FLAG_DEFAULT else 0)
                    .build()
            }
            mediaItemBuilder.setSubtitleConfigurations(subtitleConfigs)
        }

        val mediaItem = mediaItemBuilder.build()
        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)

        val mediaSource = DefaultMediaSourceFactory(context, extractorsFactory)
            .setDataSourceFactory(dataSourceFactory)
            .createMediaSource(mediaItem)

        player.stop()
        player.clearMediaItems()
        player.setMediaSource(mediaSource)
        if (fallbackSeekPositionMs >= 0L) {
            player.seekTo(fallbackSeekPositionMs)
            fallbackSeekPositionMs = -1L
        }
        player.prepare()
        player.playWhenReady = true
    }

    override fun switchToNextSource(): Boolean {
        if (currentSourceIndex + 1 < currentSources.size) {
            currentSourceIndex++
            playCurrentSource()
            return true
        }
        return false
    }

    override fun retryCurrentSource() {
        if (currentSources.isNotEmpty() && currentSourceIndex in currentSources.indices) {
            playCurrentSource()
        }
    }

    override fun retryFirstSource() {
        if (currentSources.isNotEmpty()) {
            currentSourceIndex = 0
            playCurrentSource()
        }
    }

    override fun playSourceAtIndex(index: Int) {
        if (currentSources.isNotEmpty() && index in currentSources.indices) {
            currentSourceIndex = index
            playCurrentSource()
        }
    }

    override fun getCurrentSourceIndex(): Int = currentSourceIndex
    override fun getTotalSources(): Int = currentSources.size
    override fun getSources(): List<String> = currentSources
    override fun getCurrentSourceUrl(): String? = currentSources.getOrNull(currentSourceIndex)

    override fun getAudioTracks(): List<PlayerManager.TrackInfo> {
        val player = exoPlayer ?: return emptyList()
        val tracks = player.currentTracks
        val list = mutableListOf<PlayerManager.TrackInfo>()
        for (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    val isSelected = group.isTrackSelected(trackIndex)
                    val lang = format.language?.uppercase() ?: ""
                    val rawLabel = format.label ?: if (lang.isNotEmpty()) "Ses ($lang)" else "Ses İzi ${list.size + 1}"
                    list.add(PlayerManager.TrackInfo(groupIndex, trackIndex, rawLabel, format.language, isSelected))
                }
            }
        }
        return list
    }

    override fun selectAudioTrack(groupIndex: Int, trackIndex: Int) {
        val player = exoPlayer ?: return
        val tracks = player.currentTracks
        if (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex)))
                .build()
        }
    }

    override fun getSubtitleTracks(): List<PlayerManager.TrackInfo> {
        val player = exoPlayer ?: return emptyList()
        val tracks = player.currentTracks
        val list = mutableListOf<PlayerManager.TrackInfo>()
        for (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    val isSelected = group.isTrackSelected(trackIndex)
                    val lang = format.language?.uppercase() ?: ""
                    val rawLabel = format.label ?: if (lang.isNotEmpty()) "Altyazı ($lang)" else "Altyazı ${list.size + 1}"
                    list.add(PlayerManager.TrackInfo(groupIndex, trackIndex, rawLabel, format.language, isSelected))
                }
            }
        }
        return list
    }

    override fun selectSubtitleTrack(groupIndex: Int?, trackIndex: Int?) {
        val player = exoPlayer ?: return
        if (groupIndex == null || trackIndex == null) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            return
        }
        val tracks = player.currentTracks
        if (groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[groupIndex]
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(trackIndex)))
                .build()
        }
    }

    override fun getVideoInfo(): PlayerManager.VideoInfo? {
        val player = exoPlayer ?: return null
        val vf = player.videoFormat
        return PlayerManager.VideoInfo(
            width = vf?.width ?: 0,
            height = vf?.height ?: 0,
            frameRate = vf?.frameRate ?: 0f,
            videoMime = vf?.sampleMimeType,
            audioMime = player.audioFormat?.sampleMimeType,
            bufferedDurationMs = player.totalBufferedDuration,
            bitrate = vf?.bitrate ?: 0
        )
    }

    override fun recoverLiveStream() {}

    override fun addListener(listener: Player.Listener) {
        if (!listeners.contains(listener)) listeners.add(listener)
        exoPlayer?.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)
        exoPlayer?.removeListener(listener)
    }

    override fun pausePlayer() {
        exoPlayer?.playWhenReady = false
    }

    override fun releasePlayer() {
        exoPlayer?.let { p ->
            p.stop()
            p.clearMediaItems()
            p.release()
        }
        exoPlayer = null
    }
}
