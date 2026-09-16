package tv.newtv.player

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import tv.newtv.data.models.Subtitle
import java.util.Collections
import java.util.Locale

/**
 * CANLI TV OYNATICI MOTORU (LivePlayerManager)
 * 
 * Bu sınıf C:\tv (eski Seyir TV) projesindeki PlayerActivity.java canlı yayın
 * oynatma motorunun birebir Kotlin uyarlamasıdır.
 * 
 * Önemli Özellikleri:
 * 1. Android yerleşik DefaultHttpDataSource (15s connect, 20s read, allowCrossProtocolRedirects = true).
 * 2. ResolvingDataSource ile dinamik CookieManager çerez entegrasyonu.
 * 3. Yapay LoadControl/Buffer kısıtlaması YOKTUR; ExoPlayer'ın kendi doğal canlı HLS tamponu kullanılır.
 * 4. Yapay LiveConfiguration/hız değiştirici gecikmesi YOKTUR; yayın doğrudan canlı kenardan pürüzsüz akar.
 * 5. Asla HLS akışlarını .ts dosyasına zorlamaz; orijinal .m3u8 formatında kesintisiz izletir.
 */
@OptIn(UnstableApi::class)
class LivePlayerManager(private val context: Context) : ITvPlayer {

    private var exoPlayer: ExoPlayer? = null
    private var currentSources: List<String> = emptyList()
    private var currentSourceIndex: Int = 0
    private var cachedHeaders: Map<String, String> = emptyMap()
    private val listeners = mutableListOf<Player.Listener>()

    companion object {
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
    }

    override fun initializePlayer(): ExoPlayer {
        if (exoPlayer != null) return exoPlayer!!

        // C:\tv ile birebir: EnableDecoderFallback(true) ile renderers factory
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setExceedRendererCapabilitiesIfNecessary(true))
        }

        // C:\tv ile birebir: Standart ExoPlayer Builder (Özel kısıtlayıcı LoadControl YOK)
        exoPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build().apply {
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT

                // C:\tv ile birebir: AudioAttributes ve dil ayarları
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true
                )

                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setPreferredAudioLanguage("tr")
                    .setPreferredTextLanguage("tr")
                    .build()
            }

        // Önceden eklenmiş dinleyicileri bağla
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
        playCurrentSource()
    }

    private fun playCurrentSource() {
        val player = exoPlayer ?: initializePlayer()
        if (currentSources.isEmpty() || currentSourceIndex !in currentSources.indices) return

        val rawUrl = currentSources[currentSourceIndex]
        var cleanUrl = rawUrl.trim()
        val headers = mutableMapOf(
            "User-Agent" to DEFAULT_USER_AGENT,
            "Accept" to "*/*"
        )

        // URL içindeki pipe (|) ayrıştırıcı başlıkları (Örn: url|Referer=...&Origin=...)
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

        // C:\tv ile birebir HTTP veri kaynağı ve çerez çözücü (ResolvingDataSource):
        val httpFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
            .setAllowCrossProtocolRedirects(true)

        val resolvingDataSourceFactory = ResolvingDataSource.Factory(httpFactory) { spec ->
            val reqCookie = try {
                CookieManager.getInstance().getCookie(spec.uri.toString())
            } catch (_: Exception) {
                null
            }
            if (reqCookie != null) {
                spec.withAdditionalHeaders(Collections.singletonMap("Cookie", reqCookie))
            } else {
                spec
            }
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(resolvingDataSourceFactory)

        // C:\tv ile birebir MediaItem format hazırlığı (HLS .m3u8 tespiti)
        val mediaItemBuilder = MediaItem.Builder().setUri(Uri.parse(cleanUrl))
        val path = try { Uri.parse(cleanUrl).path } catch (_: Exception) { null }
        if ((path != null && path.lowercase(Locale.ROOT).endsWith(".m3u8")) ||
            cleanUrl.lowercase(Locale.ROOT).contains(".m3u8") ||
            cleanUrl.lowercase(Locale.ROOT).contains(".m3u")
        ) {
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }

        val mediaItem = mediaItemBuilder.build()
        val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)

        player.stop()
        player.clearMediaItems()
        player.setMediaSource(mediaSource)
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

    override fun recoverLiveStream() {
        val player = exoPlayer ?: return
        try {
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            player.seekToDefaultPosition()
            player.playWhenReady = true
        } catch (_: Exception) {
            try { retryCurrentSource() } catch (_: Exception) {}
        }
    }

    override fun setFallbackSeekPosition(posMs: Long) {
        // Canlı yayında geriye sarma yerine canlı kenar korunur (C:\tv standardı)
    }

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
