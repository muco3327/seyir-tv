package tv.newtv.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import tv.newtv.data.models.Subtitle

/**
 * TV Oynatıcı Yöneticisi (PlayerManager)
 * 
 * Canlı TV ve VOD (Film/Dizi) oynatıcı motorlarını birbirinden kesin olarak ayırır:
 * - Canlı TV isteklerinde: C:\tv (eski Seyir TV) mimarisini birebir uygulayan LivePlayerManager çalışır.
 * - Film/Dizi isteklerinde: Scraper, altyazı ve yüksek tampon destekli VodPlayerManager çalışır.
 */
@OptIn(UnstableApi::class)
class PlayerManager(
    private val context: Context,
    private val initialIsLive: Boolean = false
) : ITvPlayer {

    private val livePlayer: LivePlayerManager by lazy { LivePlayerManager(context) }
    private val vodPlayer: VodPlayerManager by lazy { VodPlayerManager(context) }
    private var activePlayer: ITvPlayer = if (initialIsLive) livePlayer else vodPlayer

    companion object {
        const val DEFAULT_USER_AGENT = LivePlayerManager.DEFAULT_USER_AGENT
    }

    data class TrackInfo(
        val groupIndex: Int,
        val trackIndex: Int,
        val label: String,
        val language: String?,
        val isSelected: Boolean
    )

    data class VideoInfo(
        val width: Int,
        val height: Int,
        val frameRate: Float,
        val videoMime: String?,
        val audioMime: String?,
        val bufferedDurationMs: Long,
        val bitrate: Int
    )

    override fun initializePlayer(): ExoPlayer {
        return activePlayer.initializePlayer()
    }

    fun initializePlayer(isLive: Boolean): ExoPlayer {
        activePlayer = if (isLive) livePlayer else vodPlayer
        return activePlayer.initializePlayer()
    }

    override fun getPlayer(): ExoPlayer? = activePlayer.getPlayer()

    override fun playStream(
        streamUrl: String,
        customHeaders: Map<String, String>,
        subtitles: List<Subtitle>,
        isLive: Boolean,
        mimeType: String?
    ) {
        activePlayer = if (isLive) livePlayer else vodPlayer
        activePlayer.playStream(streamUrl, customHeaders, subtitles, isLive, mimeType)
    }

    override fun playStreamWithFailover(
        urls: List<String>,
        customHeaders: Map<String, String>,
        subtitles: List<Subtitle>,
        isLive: Boolean,
        mimeType: String?,
        startIndex: Int
    ) {
        activePlayer = if (isLive) livePlayer else vodPlayer
        activePlayer.playStreamWithFailover(urls, customHeaders, subtitles, isLive, mimeType, startIndex)
    }

    override fun switchToNextSource(): Boolean = activePlayer.switchToNextSource()
    override fun retryCurrentSource() = activePlayer.retryCurrentSource()
    override fun retryFirstSource() = activePlayer.retryFirstSource()
    override fun playSourceAtIndex(index: Int) = activePlayer.playSourceAtIndex(index)
    override fun getCurrentSourceIndex(): Int = activePlayer.getCurrentSourceIndex()
    override fun getTotalSources(): Int = activePlayer.getTotalSources()
    override fun getSources(): List<String> = activePlayer.getSources()
    override fun getCurrentSourceUrl(): String? = activePlayer.getCurrentSourceUrl()

    override fun getAudioTracks(): List<TrackInfo> = activePlayer.getAudioTracks()
    override fun selectAudioTrack(groupIndex: Int, trackIndex: Int) = activePlayer.selectAudioTrack(groupIndex, trackIndex)
    override fun getSubtitleTracks(): List<TrackInfo> = activePlayer.getSubtitleTracks()
    override fun selectSubtitleTrack(groupIndex: Int?, trackIndex: Int?) = activePlayer.selectSubtitleTrack(groupIndex, trackIndex)
    override fun getVideoInfo(): VideoInfo? = activePlayer.getVideoInfo()

    override fun recoverLiveStream() = activePlayer.recoverLiveStream()
    override fun setFallbackSeekPosition(posMs: Long) = activePlayer.setFallbackSeekPosition(posMs)

    override fun addListener(listener: Player.Listener) {
        livePlayer.addListener(listener)
        vodPlayer.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        livePlayer.removeListener(listener)
        vodPlayer.removeListener(listener)
    }

    override fun pausePlayer() {
        livePlayer.pausePlayer()
        vodPlayer.pausePlayer()
    }

    override fun releasePlayer() {
        livePlayer.releasePlayer()
        vodPlayer.releasePlayer()
    }
}
