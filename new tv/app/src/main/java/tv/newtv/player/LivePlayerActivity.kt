package tv.newtv.player

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import tv.newtv.data.local.ChannelEntity
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import tv.newtv.R
import java.util.Collections
import java.util.Locale

/**
 * C:\tv projesindeki PlayerActivity.java'nın birebir tam kopyası.
 * Canlı yayınlar için hiçbir Compose/yapay kütüphane araya sokulmadan,
 * doğrudan Android yerel Activity ve PlayerView üzerinden sıfır gecikmeyle çalışır.
 */
@OptIn(UnstableApi::class)
class LivePlayerActivity : Activity() {

    private var video: PlayerView? = null
    private var topPanel: LinearLayout? = null
    private var bottomPanel: LinearLayout? = null
    private var playerTitle: TextView? = null
    private var playerStatusBadge: TextView? = null
    private var tvCurrentTime: TextView? = null
    private var tvTotalTime: TextView? = null
    private var playerProgress: SeekBar? = null

    private var btnTopBack: Button? = null
    private var btnRewind: Button? = null
    private var btnPlayPause: Button? = null
    private var btnForward: Button? = null
    private var btnChannels: Button? = null
    private var btnSources: Button? = null
    private var btnResize: Button? = null
    private var btnQuality: Button? = null
    private var btnAudio: Button? = null
    private var btnSubtitle: Button? = null
    private var btnSpeed: Button? = null

    private var player: ExoPlayer? = null
    private var title: String = "Canlı Yayın"
    private var url: String? = null
    private val headers = mutableMapOf<String, String>()
    private val fallbackList = mutableListOf<String>()
    private val allSources = mutableListOf<String>()
    private var position: Long = 0L
    private var shouldPlay: Boolean = true
    private var closing: Boolean = false
    private var userScrubbing: Boolean = false
    private var resizeModeIndex: Int = 0

    private val RESIZE_MODES = intArrayOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        AspectRatioFrameLayout.RESIZE_MODE_FILL
    )
    private val RESIZE_LABELS = arrayOf(
        "🔲 Boyut: Sığdır",
        "🔲 Boyut: Yakınlaştır",
        "🔲 Boyut: Ekrana Yay"
    )

    private val progressHandler = Handler(Looper.getMainLooper())
    private var selectedQualityHeight: Int = 0
    private var currentSpeed: Float = 1.0f

    private val hideControls = Runnable {
        if (player != null && player!!.isPlaying && !insideControls(currentFocus)) {
            overlays(false)
        }
    }

    private val updateProgressTask = object : Runnable {
        override fun run() {
            val p = player
            if (p != null && !closing) {
                val isLive = p.isCurrentMediaItemLive || p.duration <= 0
                if (isLive) {
                    tvCurrentTime?.text = "CANLI YAYIN"
                    tvTotalTime?.text = "🔴 CANLI"
                    playerProgress?.isEnabled = false
                    playerProgress?.progress = 1000
                    btnRewind?.visibility = View.GONE
                    btnForward?.visibility = View.GONE
                } else {
                    val pos = p.currentPosition
                    val dur = p.duration
                    if (dur > 0 && !userScrubbing) {
                        tvCurrentTime?.text = formatTime(pos)
                        tvTotalTime?.text = formatTime(dur)
                        playerProgress?.progress = (pos * 1000 / dur).toInt()
                    }
                }
                updateQualityLabel()
                progressHandler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        title = intent.getStringExtra("title") ?: "Seyir TV"
        url = intent.getStringExtra("url")
        if (url.isNullOrBlank()) {
            finish()
            return
        }

        val fallbacks = intent.getStringArrayExtra("fallbackUrls")
        if (fallbacks != null) {
            for (fb in fallbacks) {
                if (!fb.isNullOrBlank() && !fallbackList.contains(fb)) {
                    fallbackList.add(fb)
                }
            }
        }

        val extraSources = intent.getStringArrayExtra("allSources")
        allSources.clear()
        if (extraSources != null) {
            for (s in extraSources) {
                if (!s.isNullOrBlank() && !allSources.contains(s)) {
                    allSources.add(s)
                }
            }
        } else {
            if (!url.isNullOrBlank()) allSources.add(url!!)
            for (fb in fallbackList) {
                if (!allSources.contains(fb)) allSources.add(fb)
            }
        }

        // Pipe ayrıştırması (|User-Agent=...&Referer=...)
        if (url!!.contains("|")) {
            val parts = url!!.split("|", limit = 2)
            url = parts[0].trim()
            val queryHeaders = parts[1].split("&")
            for (header in queryHeaders) {
                val kv = header.split("=", limit = 2)
                if (kv.size == 2) {
                    headers[kv[0].trim()] = kv[1].trim()
                }
            }
        }

        val rawHeaders = intent.getStringExtra("headers")
        if (!rawHeaders.isNullOrBlank()) {
            try {
                val jsonH = org.json.JSONObject(rawHeaders)
                val it = jsonH.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    headers[k] = jsonH.optString(k)
                }
            } catch (_: Exception) {}
        }

        val extraHeaders = intent.getSerializableExtra("headers") as? HashMap<String, String>
        if (extraHeaders != null) {
            headers.putAll(extraHeaders)
        }

        setContentView(R.layout.activity_player)
        hideBars()
        bindViews()
    }

    private fun bindViews() {
        video = findViewById(R.id.player_view)
        topPanel = findViewById(R.id.player_top_panel)
        bottomPanel = findViewById(R.id.player_bottom_panel)
        playerTitle = findViewById(R.id.player_title)
        playerStatusBadge = findViewById(R.id.player_status_badge)
        tvCurrentTime = findViewById(R.id.tv_current_time)
        tvTotalTime = findViewById(R.id.tv_total_time)
        playerProgress = findViewById(R.id.player_progress)

        btnTopBack = findViewById(R.id.btn_top_back)
        btnRewind = findViewById(R.id.btn_rewind)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnForward = findViewById(R.id.btn_forward)
        btnChannels = findViewById(R.id.btn_channels)
        btnSources = findViewById(R.id.btn_sources)
        btnResize = findViewById(R.id.btn_resize)
        btnQuality = findViewById(R.id.btn_quality)
        btnAudio = findViewById(R.id.btn_audio)
        btnSubtitle = findViewById(R.id.btn_subtitle)
        btnSpeed = findViewById(R.id.btn_speed)

        playerTitle?.text = title

        val allButtons = arrayOf(btnTopBack, btnRewind, btnPlayPause, btnForward, btnChannels, btnSources, btnResize, btnQuality, btnAudio, btnSubtitle, btnSpeed)
        for (b in allButtons) {
            if (b != null) LivePlayerUi.focus(b)
        }

        btnTopBack?.setOnClickListener { closePlayer() }
        btnRewind?.setOnClickListener { seek(-10000) }
        btnPlayPause?.setOnClickListener { togglePlay() }
        btnForward?.setOnClickListener { seek(10000) }
        btnChannels?.setOnClickListener { selectChannelDialog() }
        btnSources?.setOnClickListener { selectSourceDialog() }
        btnResize?.setOnClickListener { cycleResizeMode() }
        btnQuality?.setOnClickListener { selectQuality() }
        btnAudio?.setOnClickListener { selectTracks(C.TRACK_TYPE_AUDIO) }
        btnSubtitle?.setOnClickListener { selectTracks(C.TRACK_TYPE_TEXT) }
        btnSpeed?.setOnClickListener { selectSpeed() }

        playerProgress?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && player != null && player!!.duration > 0) {
                    val target = player!!.duration * progress / 1000
                    tvCurrentTime?.text = formatTime(target)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                userScrubbing = true
                progressHandler.removeCallbacks(hideControls)
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                userScrubbing = false
                if (player != null && player!!.duration > 0 && seekBar != null) {
                    val target = player!!.duration * seekBar.progress / 1000
                    player!!.seekTo(target)
                }
                scheduleHide()
            }
        })
    }

    private fun initialize() {
        if (player != null || url == null || closing || isFinishing || video == null) return
        try {
            var cleanUrl = url!!.trim()
            val requestHeaders = mutableMapOf(
                "User-Agent" to LivePlayerManager.DEFAULT_USER_AGENT,
                "Accept" to "*/*"
            )
            if (cleanUrl.contains("|")) {
                val parts = cleanUrl.split("|", limit = 2)
                cleanUrl = parts[0].trim()
                val queryHeaders = parts[1].split("&")
                for (header in queryHeaders) {
                    val kv = header.split("=", limit = 2)
                    if (kv.size == 2) requestHeaders[kv[0].trim()] = kv[1].trim()
                }
            }
            cleanUrl = cleanUrl.replace(" ", "%20")
            requestHeaders.putAll(headers)

            // C:\tv ile birebir: DefaultHttpDataSource + ResolvingDataSource
            val http = DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(requestHeaders)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000)
                .setAllowCrossProtocolRedirects(true)

            val data = ResolvingDataSource.Factory(http) { spec ->
                val reqCookie = try { CookieManager.getInstance().getCookie(spec.uri.toString()) } catch (_: Exception) { null }
                if (reqCookie != null) spec.withAdditionalHeaders(Collections.singletonMap("Cookie", reqCookie)) else spec
            }

            val renderersFactory = DefaultRenderersFactory(this).setEnableDecoderFallback(true)

            player = ExoPlayer.Builder(this, renderersFactory)
                .setMediaSourceFactory(DefaultMediaSourceFactory(data))
                .setSeekBackIncrementMs(10000)
                .setSeekForwardIncrementMs(10000)
                .build()

            player?.setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true
            )
            player?.trackSelectionParameters = player!!.trackSelectionParameters.buildUpon()
                .setPreferredAudioLanguage("tr")
                .setPreferredTextLanguage("tr")
                .build()

            video?.player = player

            player?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    btnPlayPause?.text = if (playing) "⏸ Duraklat" else "▶ Oynat"
                    if (playing) {
                        playerStatusBadge?.text = "Oynatılıyor"
                        scheduleHide()
                    } else {
                        playerStatusBadge?.text = "Duraklatıldı"
                        overlays(true)
                    }
                }

                override fun onPlaybackStateChanged(s: Int) {
                    if (player == null || closing) return
                    if (s == Player.STATE_READY) {
                        val isLive = player!!.isCurrentMediaItemLive || player!!.duration <= 0
                        playerStatusBadge?.text = if (isLive) "🔴 CANLI" else "Hazır"
                        updateQualityLabel()
                        scheduleHide()
                    } else if (s == Player.STATE_BUFFERING) {
                        playerStatusBadge?.text = "Yükleniyor…"
                    } else if (s == Player.STATE_ENDED) {
                        playerStatusBadge?.text = "Tamamlandı"
                        overlays(true)
                    }
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    updateQualityLabel()
                }

                override fun onPlayerError(e: PlaybackException) {
                    if (fallbackList.isNotEmpty()) {
                        val nextUrl = fallbackList.removeAt(0)
                        if (nextUrl != url) {
                            url = nextUrl
                            playerStatusBadge?.text = "Yedek sunucu deneniyor…"
                            release()
                            initialize()
                            return
                        }
                    }
                    showError(e.errorCodeName)
                }
            })

            val item = MediaItem.Builder().setUri(Uri.parse(cleanUrl))
            val path = try { Uri.parse(cleanUrl).path } catch (_: Exception) { null }
            if ((path != null && path.lowercase(Locale.ROOT).endsWith(".m3u8")) ||
                cleanUrl.lowercase(Locale.ROOT).contains(".m3u8") ||
                cleanUrl.lowercase(Locale.ROOT).contains(".m3u")
            ) {
                item.setMimeType(MimeTypes.APPLICATION_M3U8)
            }
            player?.setMediaItem(item.build())
            if (position > 0) player?.seekTo(position)
            player?.prepare()
            player?.playWhenReady = shouldPlay
            btnPlayPause?.requestFocus()
            progressHandler.post(updateProgressTask)
        } catch (e: Exception) {
            release()
            showError(e.javaClass.simpleName)
        }
    }

    private fun updateQualityLabel() {
        val btn = btnQuality ?: return
        if (selectedQualityHeight > 0) {
            btn.text = "⚙ Kalite: ${selectedQualityHeight}p"
        } else {
            val currentH = player?.videoSize?.height ?: 0
            if (currentH > 0) {
                btn.text = "⚙ Kalite: Otomatik (${currentH}p)"
            }
        }
    }

    private fun selectQuality() {
        val p = player ?: return
        val labels = mutableListOf<String>()
        val heights = mutableListOf<Int>()
        val overrides = mutableListOf<TrackSelectionOverride?>()

        labels.add("⚙ Otomatik (Önerilen)")
        heights.add(0)
        overrides.add(null)

        val seenHeights = mutableSetOf<Int>()
        for (group in p.currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_VIDEO) {
                for (i in 0 until group.length) {
                    if (group.isTrackSupported(i)) {
                        val f = group.getTrackFormat(i)
                        if (f.height > 0 && seenHeights.add(f.height)) {
                            val desc = "${f.height}p" + if (f.height >= 1080) " (FHD)" else if (f.height >= 720) " (HD)" else " (SD)"
                            labels.add(desc)
                            heights.add(f.height)
                            overrides.add(TrackSelectionOverride(group.mediaTrackGroup, listOf(i)))
                        }
                    }
                }
            }
        }

        if (labels.size <= 1) {
            AlertDialog.Builder(this).setTitle("Görüntü kalitesi")
                .setMessage("Bu yayında tek bir çözünürlük sunuluyor (Otomatik).")
                .setPositiveButton("Tamam", null).show()
            return
        }

        AlertDialog.Builder(this).setTitle("Görüntü kalitesi seçin")
            .setItems(labels.toTypedArray()) { _, which ->
                selectedQualityHeight = heights[which]
                val override = overrides[which]
                val params = p.trackSelectionParameters.buildUpon()
                if (override == null) {
                    params.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                } else {
                    params.setOverrideForType(override)
                }
                p.trackSelectionParameters = params.build()
                updateQualityLabel()
                scheduleHide()
            }.show()
    }

    private fun selectTracks(type: Int) {
        val p = player ?: return
        val labels = mutableListOf<String>()
        val overrides = mutableListOf<TrackSelectionOverride?>()

        if (type == C.TRACK_TYPE_TEXT) {
            labels.add("Kapalı")
            overrides.add(null)
        }

        for (group in p.currentTracks.groups) {
            if (group.type == type) {
                for (i in 0 until group.length) {
                    if (group.isTrackSupported(i)) {
                        val f = group.getTrackFormat(i)
                        val lang = f.language?.uppercase(Locale.ROOT) ?: ""
                        val name = f.label ?: if (lang.isNotEmpty()) lang else "Kanal ${labels.size + 1}"
                        labels.add(name)
                        overrides.add(TrackSelectionOverride(group.mediaTrackGroup, listOf(i)))
                    }
                }
            }
        }

        val titleText = if (type == C.TRACK_TYPE_AUDIO) "Ses Dili" else "Altyazı"
        if (labels.isEmpty()) {
            AlertDialog.Builder(this).setTitle(titleText)
                .setMessage("Bu yayında alternatif seçenek bulunamadı.")
                .setPositiveButton("Tamam", null).show()
            return
        }

        AlertDialog.Builder(this).setTitle("$titleText Seçin")
            .setItems(labels.toTypedArray()) { _, which ->
                val override = overrides[which]
                val builder = p.trackSelectionParameters.buildUpon()
                if (type == C.TRACK_TYPE_TEXT && which == 0) {
                    builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                } else if (override != null) {
                    if (type == C.TRACK_TYPE_TEXT) builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    builder.setOverrideForType(override)
                }
                p.trackSelectionParameters = builder.build()
                if (type == C.TRACK_TYPE_AUDIO) btnAudio?.text = "🔊 Ses: ${labels[which]}"
                else btnSubtitle?.text = "💬 Altyazı: ${labels[which]}"
                scheduleHide()
            }.show()
    }

    private fun selectSpeed() {
        val speeds = floatArrayOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val speedLabels = arrayOf("0.75x", "1.0x (Normal)", "1.25x", "1.5x", "2.0x")
        AlertDialog.Builder(this).setTitle("Oynatma hızı")
            .setItems(speedLabels) { _, w ->
                currentSpeed = speeds[w]
                player?.playbackParameters = androidx.media3.common.PlaybackParameters(currentSpeed)
                btnSpeed?.text = "⚡ ${speeds[w]}x"
                scheduleHide()
            }.show()
    }

    private fun cycleResizeMode() {
        resizeModeIndex = (resizeModeIndex + 1) % RESIZE_MODES.size
        video?.resizeMode = RESIZE_MODES[resizeModeIndex]
        btnResize?.text = RESIZE_LABELS[resizeModeIndex]
        scheduleHide()
    }

    private fun togglePlay() {
        val p = player ?: return
        if (p.playWhenReady) p.pause()
        else p.play()
        overlays(true)
        scheduleHide()
    }

    private fun seek(delta: Long) {
        val p = player ?: return
        val end = p.duration
        var target = Math.max(0L, p.currentPosition + delta)
        if (end > 0) target = Math.min(end, target)
        p.seekTo(target)
        overlays(true)
        tvCurrentTime?.text = formatTime(target)
        scheduleHide()
    }

    private fun formatTime(ms: Long): String {
        if (ms <= 0) return "00:00"
        val totalSec = ms / 1000
        val sec = totalSec % 60
        val min = (totalSec / 60) % 60
        val hour = totalSec / 3600
        if (hour > 0) return String.format(Locale.ROOT, "%d:%02d:%02d", hour, min, sec)
        return String.format(Locale.ROOT, "%02d:%02d", min, sec)
    }

    private fun insideControls(view: View?): Boolean {
        var v = view
        while (v != null) {
            if (v === topPanel || v === bottomPanel) return true
            v = if (v.parent is View) v.parent as View else null
        }
        return false
    }

    private fun insideTopPanel(view: View?): Boolean {
        var v = view
        while (v != null) {
            if (v === topPanel) return true
            v = if (v.parent is View) v.parent as View else null
        }
        return false
    }

    private fun insideBottomPanel(view: View?): Boolean {
        var v = view
        while (v != null) {
            if (v === bottomPanel) return true
            v = if (v.parent is View) v.parent as View else null
        }
        return false
    }

    private fun hideBars() {
        window.decorView.systemUiVisibility = 5894
    }

    private fun overlays(show: Boolean) {
        topPanel?.visibility = if (show) View.VISIBLE else View.GONE
        bottomPanel?.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            scheduleHide()
            if (currentFocus == null || !insideControls(currentFocus)) {
                btnPlayPause?.requestFocus()
            }
        } else {
            video?.requestFocus()
        }
        hideBars()
    }

    private fun scheduleHide() {
        progressHandler.removeCallbacks(hideControls)
        progressHandler.postDelayed(hideControls, 5000)
    }

    private fun showError(code: String) {
        if (isFinishing || isDestroyed) return
        overlays(true)
        playerStatusBadge?.text = "Hata: $code"
        AlertDialog.Builder(this).setTitle("Yayın açılamadı")
            .setMessage("Hata kodu: $code\nFarklı bir yayın kaynağı deneyebilirsiniz.")
            .setPositiveButton("Geri dön") { _, _ -> closePlayer() }
            .setNegativeButton("Tekrar dene") { _, _ ->
                if (player == null) initialize()
                else { player?.prepare(); player?.play() }
            }.show()
    }

    private fun release() {
        progressHandler.removeCallbacks(hideControls)
        progressHandler.removeCallbacks(updateProgressTask)
        val old = player ?: return
        player = null
        position = old.currentPosition
        shouldPlay = old.playWhenReady
        video?.player = null
        old.release()
    }

    private fun closePlayer() {
        closing = true
        release()
        finish()
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val p = player ?: return super.dispatchKeyEvent(e)
        val k = e.keyCode
        if (e.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e)
        scheduleHide()

        if (k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) { if (e.repeatCount == 0) togglePlay(); return true }
        if (k == KeyEvent.KEYCODE_MEDIA_PLAY) { p.play(); btnPlayPause?.text = "⏸ Duraklat"; return true }
        if (k == KeyEvent.KEYCODE_MEDIA_PAUSE) { p.pause(); btnPlayPause?.text = "▶ Oynat"; return true }
        if (k == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD || k == KeyEvent.KEYCODE_MEDIA_NEXT) { seek(30000); return true }
        if (k == KeyEvent.KEYCODE_MEDIA_REWIND || k == KeyEvent.KEYCODE_MEDIA_PREVIOUS) { seek(-30000); return true }
        if (k == KeyEvent.KEYCODE_MENU || k == KeyEvent.KEYCODE_INFO) {
            overlays(true)
            btnQuality?.requestFocus()
            return true
        }

        if (k == KeyEvent.KEYCODE_CHANNEL_UP || k == KeyEvent.KEYCODE_PAGE_UP) {
            playNextChannel()
            return true
        }
        if (k == KeyEvent.KEYCODE_CHANNEL_DOWN || k == KeyEvent.KEYCODE_PAGE_DOWN) {
            playPreviousChannel()
            return true
        }

        val controlsShown = bottomPanel != null && bottomPanel!!.visibility == View.VISIBLE
        if (!controlsShown) {
            if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_DPAD_DOWN) {
                overlays(true)
                btnPlayPause?.requestFocus()
                return true
            }
            if (k == KeyEvent.KEYCODE_DPAD_UP) {
                overlays(true)
                btnTopBack?.requestFocus()
                return true
            }
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT) { seek(if (e.repeatCount > 4) 30000 else 10000); return true }
            if (k == KeyEvent.KEYCODE_DPAD_LEFT) { seek(if (e.repeatCount > 4) -30000 else -10000); return true }
        } else {
            if (k == KeyEvent.KEYCODE_DPAD_UP && insideBottomPanel(currentFocus)) {
                btnTopBack?.requestFocus()
                return true
            }
            if (k == KeyEvent.KEYCODE_DPAD_DOWN && insideTopPanel(currentFocus)) {
                btnPlayPause?.requestFocus()
                return true
            }
        }
        return super.dispatchKeyEvent(e)
    }

    private fun selectChannelDialog() {
        val list = channelList
        if (list.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Kanallar")
                .setMessage("Kanal listesi bulunamadı.")
                .setPositiveButton("Tamam", null).show()
            return
        }
        val names = list.map { it.name }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Kanal Seçin (${names.size})")
            .setItems(names) { _, which ->
                currentChannelIndex = which
                playChannelEntity(list[which])
            }.show()
    }

    private fun selectSourceDialog() {
        if (allSources.size <= 1) {
            AlertDialog.Builder(this).setTitle("Yayın Kaynağı")
                .setMessage("Bu kanal için alternatif kaynak bulunmuyor.")
                .setPositiveButton("Tamam", null).show()
            return
        }
        val sourceLabels = allSources.mapIndexed { idx, s ->
            val isCurrent = s == url
            "Kaynak ${idx + 1}" + if (isCurrent) " (Aktif)" else ""
        }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Kaynak Seçin (${allSources.size})")
            .setItems(sourceLabels) { _, which ->
                val chosen = allSources[which]
                if (chosen != url) {
                    url = chosen
                    playerStatusBadge?.text = "Kaynak değiştiriliyor…"
                    release()
                    initialize()
                }
            }.show()
    }

    private fun playChannelEntity(ch: ChannelEntity) {
        release()
        title = ch.name
        val streamUrls = ch.getStreamUrls().filter { it.isNotBlank() }
        url = streamUrls.firstOrNull() ?: ch.streamUrl
        fallbackList.clear()
        allSources.clear()
        allSources.addAll(streamUrls)
        if (streamUrls.size > 1) {
            for (i in 1 until streamUrls.size) {
                fallbackList.add(streamUrls[i])
            }
        }
        playerTitle?.text = title
        playerStatusBadge?.text = "Bağlanıyor…"
        position = 0L
        initialize()
    }

    fun playNextChannel() {
        if (channelList.isEmpty()) return
        currentChannelIndex = (currentChannelIndex + 1) % channelList.size
        playChannelEntity(channelList[currentChannelIndex])
    }

    fun playPreviousChannel() {
        if (channelList.isEmpty()) return
        currentChannelIndex = if (currentChannelIndex - 1 < 0) channelList.size - 1 else currentChannelIndex - 1
        playChannelEntity(channelList[currentChannelIndex])
    }

    override fun onBackPressed() {
        if (bottomPanel != null && bottomPanel!!.visibility == View.VISIBLE) {
            overlays(false)
        } else {
            closePlayer()
        }
    }

    override fun onWindowFocusChanged(focus: Boolean) {
        super.onWindowFocusChanged(focus)
        if (focus) hideBars()
    }

    override fun onStart() { super.onStart(); initialize() }
    override fun onStop() { release(); super.onStop() }
    override fun onDestroy() { release(); super.onDestroy() }

    companion object {
        var channelList: List<ChannelEntity> = emptyList()
        var currentChannelIndex: Int = -1

        fun start(
            context: Context,
            channel: ChannelEntity,
            sourceIndex: Int = 0,
            allChannels: List<ChannelEntity> = emptyList()
        ) {
            channelList = allChannels
            currentChannelIndex = allChannels.indexOfFirst { it.id == channel.id }
            val streamUrls = channel.getStreamUrls().filter { it.isNotBlank() }
            val primary = streamUrls.getOrNull(sourceIndex) ?: channel.streamUrl
            val fallbacks = if (streamUrls.size > 1) {
                streamUrls.filterIndexed { idx, _ -> idx != sourceIndex }
            } else emptyList()

            val intent = Intent(context, LivePlayerActivity::class.java).apply {
                putExtra("title", channel.name)
                putExtra("url", primary)
                putExtra("fallbackUrls", fallbacks.toTypedArray())
                putExtra("allSources", streamUrls.toTypedArray())
                putExtra("channelId", channel.id)
            }
            context.startActivity(intent)
        }
    }
}
