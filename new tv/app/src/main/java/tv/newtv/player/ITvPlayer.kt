package tv.newtv.player

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import tv.newtv.data.models.Subtitle

/**
 * Ortak TV Oynatıcı Arayüzü.
 * Canlı TV (LivePlayerManager) ve Film/Dizi (VodPlayerManager) motorları
 * bu arayüz üzerinden birbirinden tamamen bağımsız olarak çalışır.
 */
interface ITvPlayer {
    fun initializePlayer(): ExoPlayer
    fun getPlayer(): ExoPlayer?

    fun playStream(
        streamUrl: String,
        customHeaders: Map<String, String> = emptyMap(),
        subtitles: List<Subtitle> = emptyList(),
        isLive: Boolean = false,
        mimeType: String? = null
    )

    fun playStreamWithFailover(
        urls: List<String>,
        customHeaders: Map<String, String> = emptyMap(),
        subtitles: List<Subtitle> = emptyList(),
        isLive: Boolean = false,
        mimeType: String? = null,
        startIndex: Int = 0
    )

    fun switchToNextSource(): Boolean
    fun retryCurrentSource()
    fun retryFirstSource()
    fun playSourceAtIndex(index: Int)
    fun getCurrentSourceIndex(): Int
    fun getTotalSources(): Int
    fun getSources(): List<String>
    fun getCurrentSourceUrl(): String?

    fun getAudioTracks(): List<PlayerManager.TrackInfo>
    fun selectAudioTrack(groupIndex: Int, trackIndex: Int)
    fun getSubtitleTracks(): List<PlayerManager.TrackInfo>
    fun selectSubtitleTrack(groupIndex: Int?, trackIndex: Int?)
    fun getVideoInfo(): PlayerManager.VideoInfo?

    fun recoverLiveStream()
    fun setFallbackSeekPosition(posMs: Long)

    fun addListener(listener: Player.Listener)
    fun removeListener(listener: Player.Listener)
    fun pausePlayer()
    fun releasePlayer()
}
