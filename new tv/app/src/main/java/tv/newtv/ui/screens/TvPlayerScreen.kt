package tv.newtv.ui.screens

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.newtv.data.local.ChannelEntity
import tv.newtv.player.PlayerManager
import tv.newtv.ui.viewmodel.PlayerMediaType
import tv.newtv.ui.viewmodel.TvPlayerViewModel
import tv.newtv.ui.viewmodel.TvResizeMode

@OptIn(UnstableApi::class)
@Composable
fun TvPlayerScreen(viewModel: TvPlayerViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val currentStream by viewModel.currentStream.collectAsStateWithLifecycle()
    val mediaType by viewModel.mediaType.collectAsStateWithLifecycle()
    val vodExtractionError by viewModel.vodExtractionError.collectAsStateWithLifecycle()
    val streamTitle by viewModel.streamTitle.collectAsStateWithLifecycle()
    val isControlsVisible by viewModel.isControlsVisible.collectAsStateWithLifecycle()
    val showAudioSubMenu by viewModel.showAudioSubMenu.collectAsStateWithLifecycle()
    val showSourceMenu by viewModel.showSourceMenu.collectAsStateWithLifecycle()
    val showInfoMenu by viewModel.showInfoMenu.collectAsStateWithLifecycle()
    val showChannelListMenu by viewModel.showChannelListMenu.collectAsStateWithLifecycle()
    val resizeMode by viewModel.resizeMode.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val currentSources by viewModel.currentSources.collectAsStateWithLifecycle()
    val videoSources by viewModel.videoSources.collectAsStateWithLifecycle()
    val currentSourceIndex by viewModel.currentSourceIndex.collectAsStateWithLifecycle()
    val failoverMessage by viewModel.failoverMessage.collectAsStateWithLifecycle()
    val channelList by viewModel.channelList.collectAsStateWithLifecycle()
    val currentChannel by viewModel.currentChannel.collectAsStateWithLifecycle()
    val dubbingIframes by viewModel.dubbingIframes.collectAsStateWithLifecycle()
    val subtitleIframes by viewModel.subtitleIframes.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val vodSavedPosition by viewModel.vodSavedPosition.collectAsStateWithLifecycle()

    var isBuffering by remember { mutableStateOf(true) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var autoRetryCount by remember { mutableIntStateOf(0) }
    var currentSourceRetryCount by remember { mutableIntStateOf(0) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    var currentPosition by remember { mutableLongStateOf(0L) }
    var savedVodResumePosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }

    // Kumanda ile Kademeli Hızlı İleri / Geri Sarma (Seeking HUD)
    var isSeekingHudVisible by remember { mutableStateOf(false) }
    var seekOffsetSeconds by remember { mutableIntStateOf(0) }
    var seekTargetPosition by remember { mutableLongStateOf(0L) }
    var seekDirectionForward by remember { mutableStateOf(true) }
    var liveWarningVisible by remember { mutableStateOf(false) }
    var vodBufferingStartedAt by remember { mutableLongStateOf(0L) }
    var liveBufferingStartedAt by remember { mutableLongStateOf(0L) }
    var vodRecoveryCount by remember { mutableIntStateOf(0) }
    var pendingSeekPosition by remember { mutableLongStateOf(-1L) }

    LaunchedEffect(isSeekingHudVisible, lastInteractionTime) {
        if (isSeekingHudVisible) {
            delay(1300)
            isSeekingHudVisible = false
            seekOffsetSeconds = 0
        }
    }

    LaunchedEffect(liveWarningVisible) {
        if (liveWarningVisible) {
            delay(2000)
            liveWarningVisible = false
        }
    }

    LaunchedEffect(vodSavedPosition) {
        if (vodSavedPosition > 0L) {
            savedVodResumePosition = vodSavedPosition
        }
    }

    // Parça ve teknik bilgiler
    var audioTracks by remember { mutableStateOf<List<PlayerManager.TrackInfo>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<PlayerManager.TrackInfo>>(emptyList()) }
    var videoInfo by remember { mutableStateOf<PlayerManager.VideoInfo?>(null) }

    val rootFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val channelListFocusRequester = remember { FocusRequester() }
    val seekBarFocusRequester = remember { FocusRequester() }
    val errorRetryFocusRequester = remember { FocusRequester() }
    val seekBarInteraction = remember { MutableInteractionSource() }
    val isSeekBarFocused by seekBarInteraction.collectIsFocusedAsState()

    // VOD çözücü hatası dinleyici
    LaunchedEffect(vodExtractionError) {
        if (vodExtractionError != null) {
            isBuffering = false
            playerError = vodExtractionError
        }
    }

    // Video hata kutusu açıldığında kumanda odağını anında Yeniden Dene butonuna ver
    LaunchedEffect(playerError) {
        if (playerError != null) {
            delay(150)
            try {
                errorRetryFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Oynatıcı Yönetimi (Canlı TV ve VOD motorları birbirinden tamamen ayrılmıştır)
    val isLive = mediaType == PlayerMediaType.LIVE_CHANNEL
    val playerManager = remember(isLive) { PlayerManager(context, initialIsLive = isLive) }
    val exoPlayer = remember(playerManager) { playerManager.initializePlayer(isLive) }

    fun seekVodTo(target: Long) {
        pendingSeekPosition = target.coerceAtLeast(0L)
        vodRecoveryCount = 0
        vodBufferingStartedAt = System.currentTimeMillis()
        playerError = null
        isBuffering = true
        exoPlayer.setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
        exoPlayer.seekTo(pendingSeekPosition)
        exoPlayer.playWhenReady = true
    }

    // Oynatıcı Durum Dinleyicisi
    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isPlaying = exoPlayer.playWhenReady && playbackState == androidx.media3.common.Player.STATE_READY
                when (playbackState) {
                    androidx.media3.common.Player.STATE_BUFFERING -> {
                        isBuffering = true
                        if (vodBufferingStartedAt == 0L) vodBufferingStartedAt = System.currentTimeMillis()
                        if (liveBufferingStartedAt == 0L) liveBufferingStartedAt = System.currentTimeMillis()
                    }
                    androidx.media3.common.Player.STATE_READY -> {
                        isBuffering = false
                        vodBufferingStartedAt = 0L
                        vodRecoveryCount = 0
                        pendingSeekPosition = -1L
                        playerError = null
                        currentSourceRetryCount = 0
                        audioTracks = playerManager.getAudioTracks()
                        subtitleTracks = playerManager.getSubtitleTracks()
                        videoInfo = playerManager.getVideoInfo()
                        if (savedVodResumePosition > 0L) {
                            val pos = savedVodResumePosition
                            savedVodResumePosition = 0L
                            exoPlayer.seekTo(pos)
                        }
                    }
                    androidx.media3.common.Player.STATE_ENDED -> {
                        isBuffering = false
                    }
                    androidx.media3.common.Player.STATE_IDLE -> {}
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                isPlaying = playWhenReady && exoPlayer.playbackState == androidx.media3.common.Player.STATE_READY
            }

            var behindLiveWindowCount = 0

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val isLive = viewModel.mediaType.value == PlayerMediaType.LIVE_CHANNEL
                val perfSettings = tv.newtv.data.local.IptvSettingsManager.getSettings(context)
                val maxRetries = perfSettings.maxRetries.coerceIn(1, 10)

                // 1. Canlı pencerenin gerisine düşüldüyse (BehindLiveWindowException):
                val isBehindLive = error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW ||
                    error.localizedMessage?.contains("BehindLiveWindow", ignoreCase = true) == true

                if (isLive && isBehindLive) {
                    behindLiveWindowCount++
                    if (behindLiveWindowCount <= 1) {
                        playerManager.recoverLiveStream()
                        return
                    }
                    // Peş peşe 2 kez pencere dışı kaldıysa alternatif kaynağa geç
                    behindLiveWindowCount = 0
                }

                // 2. StreamScope Failover: Kopan kaynak için beklemeden doğrudan sıradaki alternatif kaynağa geç!
                currentSourceRetryCount = 0
                val currentPosMs = exoPlayer.currentPosition
                if (isLive && viewModel.switchToNextSource()) {
                    playerManager.setFallbackSeekPosition(currentPosMs)
                    isBuffering = true
                    playerError = null
                    return
                }

                // 3. Tüm kaynaklar bir tur bittiyse baştan 1. kaynağa dönüp maxRetries tur döngü yap
                if (isLive && autoRetryCount < maxRetries) {
                    autoRetryCount++
                    isBuffering = true
                    playerError = null
                    playerManager.retryFirstSource()
                    viewModel.selectSourceIndex(0)
                    return
                }

                // 4. VOD için konumu koruyarak sıradaki kaynağı çöz
                if (!isLive) {
                    savedVodResumePosition = pendingSeekPosition.takeIf { it >= 0L } ?: exoPlayer.currentPosition
                    if (viewModel.switchToNextSource()) {
                        isBuffering = true
                        playerError = null
                        return
                    }
                }

                // 5. Tüm denemeler ve alternatif kaynaklar sonuçsuz kalırsa kullanıcıya hata bildir
                isBuffering = false
                val detail = when {
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Sunucu yanıt vermedi (Zaman aşımı)"
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Ağ bağlantısı kurulamadı"
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Yayın sunucusu yanıt vermedi (HTTP hatası)"
                    else -> error.localizedMessage ?: "Bağlantı hatası"
                }
                val total = currentSources.size
                val errorMsg = if (isLive) {
                    if (total > 1) "Mevcut $total yayın kaynağının tamamı denendi fakat akış alınamadı.\n($detail)"
                    else "Yayın akışı geçici olarak kesildi.\n($detail)"
                } else {
                    "Film/Dizi kaynağı açılamadı veya video sunucusu çevrimdışı.\n($detail)"
                }
                playerError = errorMsg
            }
        }
        playerManager.addListener(listener)
        onDispose {
            playerManager.removeListener(listener)
        }
    }

    // Canlı Yayın Donma & Takılma İzleyicisi (Live Freeze & Stall Watchdog)
    // LaunchedEffect YALNIZCA exoPlayer ve currentStream'e bağlıdır.
    // isBuffering anahtarına BAĞLANMAZ (böylece buffer dalgalanmalarında coroutine iptal edilip sıfırlanmaz!)
    LaunchedEffect(exoPlayer, currentStream) {
        val isLive = viewModel.mediaType.value == PlayerMediaType.LIVE_CHANNEL
        if (!isLive || currentStream == null) return@LaunchedEffect

        var stallScore = 0f
        var lastPosition = -1L
        var lastBufferedMs = -1L
        var networkDeadSeconds = 0
        var healthyPlaybackSeconds = 0

        // Watchdog (Erken Teşhis ve Agresif Geçiş) sistemi kullanıcının isteği üzerine 
        // iptal edildi. Oynatıcı artık sadece onPlayerError üzerinden kendi doğal 
        // hata yönetimiyle çalışacak.
    }


    // VOD sunucusu seek/Range isteğinde hata vermeden takılırsa sonsuz yükleme yerine kullanıcıya bildir.
    LaunchedEffect(exoPlayer, currentStream, mediaType) {
        while (true) {
            delay(1000)
            val isVod = mediaType == PlayerMediaType.VOD_MOVIE || mediaType == PlayerMediaType.VOD_SERIES
            if (!isVod || !isBuffering || currentStream == null || playerError != null) {
                if (!isBuffering) vodBufferingStartedAt = 0L
                continue
            }
            if (vodBufferingStartedAt == 0L) vodBufferingStartedAt = System.currentTimeMillis()
            if (System.currentTimeMillis() - vodBufferingStartedAt < 15_000L) continue

            // VOD'da otomatik failover yok – kullanıcıya doğrudan bildir
            val resumeAt = pendingSeekPosition.takeIf { it >= 0L } ?: exoPlayer.currentPosition.coerceAtLeast(0L)
            savedVodResumePosition = resumeAt
            isBuffering = false
            vodBufferingStartedAt = 0L
            val altSunucuVar = videoSources.size > 1
            playerError = if (altSunucuVar) {
                "Seçili video sunucusu yanıt vermedi. Kaynak menüsünden (${videoSources.size - 1} alternatif) başka bir sunucu seçebilirsiniz."
            } else {
                "Video sunucusu yanıt vermedi. Lütfen daha sonra tekrar deneyin."
            }
        }
    }

    // Polling for player progress & info
    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.takeIf { it > 0 } ?: 0L
            viewModel.savePlaybackProgress(currentPosition, duration)
            videoInfo = playerManager.getVideoInfo()
            delay(1000)
        }
    }

    // Zaman Formatlama Fonksiyonu
    fun formatTime(ms: Long): String {
        if (ms < 0) return "00:00"
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
        else String.format("%02d:%02d", minutes, seconds)
    }

    // Stream Değiştiğinde Oynat
    LaunchedEffect(currentStream) {
        currentStream?.let { source ->
            isBuffering = true
            playerError = null
            val isLiveStream = viewModel.mediaType.value == PlayerMediaType.LIVE_CHANNEL
            val allSources = currentSources.takeIf { it.isNotEmpty() } ?: listOf(source.url)
            val activeIndex = allSources.indexOf(source.url).takeIf { it >= 0 }
                ?: currentSourceIndex.coerceIn(0, (allSources.size - 1).coerceAtLeast(0))

            playerManager.playStreamWithFailover(
                urls = allSources,
                customHeaders = source.headers,
                subtitles = source.subtitles,
                isLive = isLiveStream,
                mimeType = source.mimeType,
                startIndex = activeIndex
            )
        }
    }

    // Menü açıldığında parçaları tazele
    LaunchedEffect(showAudioSubMenu) {
        if (showAudioSubMenu) {
            audioTracks = playerManager.getAudioTracks()
            subtitleTracks = playerManager.getSubtitleTracks()
        }
    }

    LaunchedEffect(showChannelListMenu, channelList, currentChannel) {
        if (showChannelListMenu && channelList.isNotEmpty()) {
            delay(120)
            runCatching { channelListFocusRequester.requestFocus() }
        }
    }

    // Yedek kaynak / ekran boyutu bildirim zamanlayıcısı
    LaunchedEffect(failoverMessage) {
        if (failoverMessage != null) {
            delay(3500)
            viewModel.clearFailoverMessage()
        }
    }

    // İlk açılışta ana ekrana kumanda odağı ver
    LaunchedEffect(Unit) {
        rootFocusRequester.requestFocus()
    }

    // Kontroller açıldığında durdur/oynat butonuna odak ver
    LaunchedEffect(isControlsVisible) {
        if (isControlsVisible) {
            try {
                playPauseFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Hata diyaloğu açıldığında otomatik olarak "Yeniden Dene" butonuna kumanda odağı ver
    LaunchedEffect(playerError) {
        if (playerError != null) {
            delay(200)
            try {
                errorRetryFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // 6 Saniye Hareketsizlik Kontrolü (Zamanlayıcı Sıfırlamalı)
    val anyMenuOpen = isControlsVisible || showAudioSubMenu || showSourceMenu || showInfoMenu || showChannelListMenu
    val autoHideMenuOpen = isControlsVisible || showAudioSubMenu || showSourceMenu || showChannelListMenu
    LaunchedEffect(autoHideMenuOpen, lastInteractionTime) {
        if (autoHideMenuOpen) {
            delay(6500)
            viewModel.hideAutoClosingMenus()
        }
    }

    // Yaşam Döngüsü Yönetimi
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) playerManager.pausePlayer()
            if (event == Lifecycle.Event.ON_DESTROY) playerManager.releasePlayer()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            viewModel.savePlaybackProgress(exoPlayer.currentPosition, exoPlayer.duration.takeIf { it > 0 } ?: 0L, force = true)
            lifecycleOwner.lifecycle.removeObserver(observer)
            playerManager.releasePlayer()
        }
    }

    // Android Sistem Geri Tuşu Yakalayıcısı
    androidx.activity.compose.BackHandler {
        if (playerError != null) {
            playerError = null
            onBack()
        } else if (anyMenuOpen) {
            viewModel.hideAllMenus()
        } else {
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                lastInteractionTime = System.currentTimeMillis()
                viewModel.toggleControls()
            }
            .onKeyEvent { event ->
                lastInteractionTime = System.currentTimeMillis()

                // Hata penceresi açıkken kumanda tuşlarının diyalog butonlarına odaklanmasına izin ver
                if (playerError != null) {
                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                        when (event.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                                playerError = null
                                onBack()
                                return@onKeyEvent true
                            }
                            else -> return@onKeyEvent false
                        }
                    }
                    return@onKeyEvent false
                }

                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE -> {
                            if (!anyMenuOpen) {
                                viewModel.showControls()
                                true
                            } else {
                                false
                            }
                        }
                        // Kumanda CH+ veya Menü kapalıyken YUKARI TUŞU: Önceki Kanal (SADECE CANLI TV!)
                        KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                            if (mediaType == PlayerMediaType.LIVE_CHANNEL) {
                                viewModel.playPreviousChannel()
                                true
                            } else false
                        }
                        // Kumanda CH- veya Menü kapalıyken AŞAĞI TUŞU: Sonraki Kanal (SADECE CANLI TV!)
                        KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                            if (mediaType == PlayerMediaType.LIVE_CHANNEL) {
                                viewModel.playNextChannel()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (!anyMenuOpen) {
                                viewModel.showControls()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (!anyMenuOpen) {
                                if (mediaType == PlayerMediaType.LIVE_CHANNEL && channelList.isNotEmpty()) {
                                    viewModel.toggleChannelListMenu()
                                } else {
                                    viewModel.showControls()
                                }
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (isControlsVisible || showAudioSubMenu || showSourceMenu || showInfoMenu || showChannelListMenu) {
                                // Menüler veya kontroller açıksa focus gezinmesine izin ver (false dön)
                                false
                            } else if (mediaType == PlayerMediaType.LIVE_CHANNEL) {
                                liveWarningVisible = true
                                viewModel.showControls()
                                true
                            } else {
                                // VOD modunda kontroller kapalıyken anında SAR ve kontrolleri GÖSTER
                                val isForward = event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                                val repeat = event.nativeKeyEvent.repeatCount
                                val stepSeconds = when {
                                    repeat == 0 -> 10       // Tek basış: 10 sn
                                    repeat in 1..4 -> 20     // Kısa basılı tutuş: 20 sn / adım
                                    repeat in 5..10 -> 45    // Orta basılı tutuş: 45 sn / adım
                                    else -> 90               // Uzun basılı tutuş: 90 sn / adım
                                }
                                val stepMs = stepSeconds * 1000L
                                val totalDur = if (exoPlayer.duration > 0) exoPlayer.duration else duration
                                val basePos = if (isSeekingHudVisible) seekTargetPosition else exoPlayer.currentPosition
                                val newPos = if (isForward) {
                                    (basePos + stepMs).coerceAtMost(maxOf(0L, totalDur))
                                } else {
                                    (basePos - stepMs).coerceAtLeast(0L)
                                }

                                seekDirectionForward = isForward
                                seekTargetPosition = newPos
                                seekOffsetSeconds = ((newPos - exoPlayer.currentPosition) / 1000L).toInt()
                                isSeekingHudVisible = true
                                currentPosition = newPos
                                seekVodTo(newPos)
                                viewModel.showControls() // YouTube tarzı: sarınca menüyü göster
                                true
                            }
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            exoPlayer.playWhenReady = !exoPlayer.playWhenReady
                            true
                        }
                        KeyEvent.KEYCODE_PROG_YELLOW -> {
                            viewModel.toggleSourceMenu()
                            lastInteractionTime = System.currentTimeMillis()
                            true
                        }
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                            if (anyMenuOpen) {
                                viewModel.hideAllMenus()
                                true
                            } else {
                                onBack()
                                true
                            }
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // Native ExoPlayer View (Media3) - Dinamik Aspect Ratio Destekli
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    this.resizeMode = resizeMode.mode
                    setKeepContentOnPlayerReset(true)
                    setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_NEVER)
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.resizeMode = resizeMode.mode
            },
            modifier = Modifier.fillMaxSize()
        )

        // Yükleniyor / İlk Bağlanıyor Göstergesi (Büyük Kutu)
        if (isLoading) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.82f), RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(14.dp))
                    .padding(horizontal = 28.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val isLive = mediaType == PlayerMediaType.LIVE_CHANNEL
                    val loadingTitle = if (isLive) {
                        if (autoRetryCount > 0) "⏳ Yayına Yeniden Bağlanılıyor ($autoRetryCount/2)…" else "⏳ Yayın Bağlanıyor…"
                    } else {
                        if (autoRetryCount > 0) "⏳ Videoya Yeniden Bağlanılıyor ($autoRetryCount/2)…" else "⏳ Video Yükleniyor…"
                    }
                    val loadingSubtitle = if (isLive) {
                        if (autoRetryCount > 0) "Alternatif akış toparlanıyor, lütfen bekleyin" else "Lütfen bekleyin, akış çözümleniyor"
                    } else {
                        if (autoRetryCount > 0) "Alternatif sunucu kontrol ediliyor, lütfen bekleyin" else "Lütfen bekleyin, video hazırlanıyor"
                    }
                    Text(
                        text = loadingTitle,
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = loadingSubtitle,
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Anlık Ara Belleğe Alma (Buffering) Göstergesi (Ufak Spinner)
        if (isBuffering && !isLoading && playerError == null) {
            androidx.compose.material3.CircularProgressIndicator(
                color = Color(0xFFE50914),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
            )
        }

        // Yedek Kaynak / Ekran Boyutu Bildirim HUD'ı (Toast Bildirim)
        if (failoverMessage != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp)
                    .background(Color(0xF21C1605), RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFFFFC107), RoundedCornerShape(20.dp))
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚡ ", fontSize = 15.sp)
                    Text(
                        text = failoverMessage ?: "",
                        color = Color(0xFFFFD54F),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Hata Bildirimi (Yayın veya video açılamadığında)
        if (playerError != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xFF1A1A1A), RoundedCornerShape(16.dp))
                    .border(2.dp, Color(0xFFE50914), RoundedCornerShape(16.dp))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (mediaType == PlayerMediaType.LIVE_CHANNEL) "⚠️ Yayın Hatası" else "⚠️ Video Hatası",
                        color = Color(0xFFFF5252),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = playerError!!,
                        color = Color.White,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val retryInteractionSource = remember { MutableInteractionSource() }
                        val isRetryFocused by retryInteractionSource.collectIsFocusedAsState()

                        Box(
                            modifier = Modifier
                                .focusRequester(errorRetryFocusRequester)
                                .scale(if (isRetryFocused) 1.08f else 1.0f)
                                .background(
                                    color = if (isRetryFocused) Color.White else Color(0xFFE50914),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .border(
                                    width = if (isRetryFocused) 3.dp else 1.dp,
                                    color = if (isRetryFocused) Color(0xFFFF8080) else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .focusable(interactionSource = retryInteractionSource)
                                .clickable {
                                    currentSourceRetryCount = 0
                                    isBuffering = true
                                    playerError = null
                                    currentStream?.let { source ->
                                        playerManager.playStream(
                                            streamUrl = source.url,
                                            customHeaders = source.headers,
                                            subtitles = source.subtitles,
                                            isLive = mediaType == PlayerMediaType.LIVE_CHANNEL,
                                            mimeType = source.mimeType
                                        )
                                    }
                                }
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                        (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)) {
                                        currentSourceRetryCount = 0
                                        isBuffering = true
                                        playerError = null
                                        currentStream?.let { source ->
                                            playerManager.playStream(
                                                streamUrl = source.url,
                                                customHeaders = source.headers,
                                                subtitles = source.subtitles,
                                                isLive = mediaType == PlayerMediaType.LIVE_CHANNEL,
                                                mimeType = source.mimeType
                                            )
                                        }
                                        true
                                    } else false
                                }
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "Yeniden Dene",
                                color = if (isRetryFocused) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        val altSources = if (mediaType == PlayerMediaType.LIVE_CHANNEL) currentSources.size else videoSources.size
                        if (altSources > 1) {
                            val sourceBtnInteractionSource = remember { MutableInteractionSource() }
                            val isSourceBtnFocused by sourceBtnInteractionSource.collectIsFocusedAsState()

                            Box(
                                modifier = Modifier
                                    .scale(if (isSourceBtnFocused) 1.08f else 1.0f)
                                    .background(
                                        color = if (isSourceBtnFocused) Color.White else Color(0xFF2979FF),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .border(
                                        width = if (isSourceBtnFocused) 3.dp else 1.dp,
                                        color = if (isSourceBtnFocused) Color(0xFF82B1FF) else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .focusable(interactionSource = sourceBtnInteractionSource)
                                    .clickable {
                                        playerError = null
                                        viewModel.toggleSourceMenu()
                                    }
                                    .onKeyEvent { keyEvent ->
                                        if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                            (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)) {
                                            playerError = null
                                            viewModel.toggleSourceMenu()
                                            true
                                        } else false
                                    }
                                    .padding(horizontal = 24.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = "Kaynak Değiştir ($altSources)",
                                    color = if (isSourceBtnFocused) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }

                        val backInteractionSource = remember { MutableInteractionSource() }
                        val isBackFocused by backInteractionSource.collectIsFocusedAsState()

                        Box(
                            modifier = Modifier
                                .scale(if (isBackFocused) 1.08f else 1.0f)
                                .background(
                                    color = if (isBackFocused) Color.White else Color(0xFF333333),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .border(
                                    width = if (isBackFocused) 3.dp else 1.dp,
                                    color = if (isBackFocused) Color(0xFFCCCCCC) else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .focusable(interactionSource = backInteractionSource)
                                .clickable {
                                    playerError = null
                                    onBack()
                                }
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                        (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)) {
                                        playerError = null
                                        onBack()
                                        true
                                    } else false
                                }
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "Geri Dön",
                                color = if (isBackFocused) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // --- GELİŞMİŞ OYNATICI KONTROL ARAYÜZÜ ---
        if (isControlsVisible) {
            // 1. ÜST BAR: Başlık, Geri Tuşu, Kaynak Sayısı ve Durum Rozeti
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.90f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 32.dp, vertical = 20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val topBackInteraction = remember { MutableInteractionSource() }
                        val isTopBackFocused by topBackInteraction.collectIsFocusedAsState()

                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .scale(if (isTopBackFocused) 1.15f else 1.0f)
                                .background(
                                    if (isTopBackFocused) Color.White else Color(0xFF22262E),
                                    CircleShape
                                )
                                .border(
                                    1.dp,
                                    if (isTopBackFocused) Color(0xFFFFCC00) else Color(0xFF444444),
                                    CircleShape
                                )
                                .focusable(interactionSource = topBackInteraction)
                                .clickable {
                                    viewModel.hideAllMenus()
                                    onBack()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Geri",
                                tint = if (isTopBackFocused) Color.Black else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(
                                text = streamTitle,
                                color = Color.White,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val subTitle = when (mediaType) {
                                PlayerMediaType.LIVE_CHANNEL -> currentChannel?.groupTitle ?: ""
                                PlayerMediaType.VOD_MOVIE -> "Film"
                                PlayerMediaType.VOD_SERIES -> "Dizi Bölümü"
                            }
                            if (subTitle.isNotEmpty()) {
                                Text(
                                    text = subTitle,
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Ekran Boyutu Rozeti
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF263238), RoundedCornerShape(6.dp))
                                .border(1.dp, Color(0xFF455A64), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = resizeMode.title.substringBefore(" ").uppercase(),
                                color = Color(0xFF80CBC4),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Kaynak Rozeti (Canlı TV)
                        if (mediaType == PlayerMediaType.LIVE_CHANNEL) {
                            val totalSources = currentSources.size.coerceAtLeast(1)
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF2C2411), RoundedCornerShape(6.dp))
                                    .border(1.dp, Color(0xFFFFB300), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "⚡ Kaynak ${currentSourceIndex + 1}/$totalSources",
                                    color = Color(0xFFFFD54F),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Box(
                            modifier = Modifier
                                .background(
                                    if (mediaType == PlayerMediaType.LIVE_CHANNEL) Color(0xFFE50914) else Color(0xFF2196F3),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (mediaType == PlayerMediaType.LIVE_CHANNEL) "CANLI" else "HD",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 2. ALT BAR: İlerleme Çubuğu & Oynatıcı Kontrol Araçları
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.94f))
                        )
                    )
                    .padding(horizontal = 32.dp, vertical = 20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // İlerleme Çubuğu ve Süre Bilgisi (VOD veya Canlı)
                    if (mediaType != PlayerMediaType.LIVE_CHANNEL) {
                        val progress = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.94f)
                                .height(if (isSeekBarFocused) 12.dp else 7.dp)
                                .focusRequester(seekBarFocusRequester)
                                .focusable(interactionSource = seekBarInteraction)
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                                    when (keyEvent.nativeKeyEvent.keyCode) {
                                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                            val direction = if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1
                                            val repeat = keyEvent.nativeKeyEvent.repeatCount
                                            val seconds = when {
                                                repeat <= 1 -> 10
                                                repeat <= 5 -> 30
                                                else -> 60
                                            }
                                            val target = (exoPlayer.currentPosition + direction * seconds * 1000L)
                                                .coerceIn(0L, duration.coerceAtLeast(0L))
                                            seekVodTo(target)
                                            currentPosition = target
                                            seekTargetPosition = target
                                            seekDirectionForward = direction > 0
                                            seekOffsetSeconds = direction * seconds
                                            isSeekingHudVisible = true
                                            lastInteractionTime = System.currentTimeMillis()
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                                            runCatching { playPauseFocusRequester.requestFocus() }
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                            exoPlayer.playWhenReady = !exoPlayer.playWhenReady
                                            true
                                        }
                                        else -> false
                                    }
                                }
                                .border(
                                    width = if (isSeekBarFocused) 2.dp else 0.dp,
                                    color = if (isSeekBarFocused) Color(0xFFFFD54F) else Color.Transparent,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .background(Color(0xFF333333), RoundedCornerShape(3.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .fillMaxHeight()
                                    .background(if (isSeekBarFocused) Color(0xFFFF3D3D) else Color(0xFFE50914), RoundedCornerShape(3.dp))
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(0.94f),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                formatTime(currentPosition),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (duration > 0) formatTime(duration) else "--:--",
                                color = Color.LightGray,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        // Canlı Yayın Çizgisi
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(0.94f)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFF00E676), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Kesintisiz Canlı Yayın Akışı",
                                color = Color(0xFF00E676),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Kumanda Odaklı Oynatıcı Araçları
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (mediaType != PlayerMediaType.LIVE_CHANNEL) {
                            TvPlayerControlButton(
                                icon = Icons.Default.Replay10,
                                label = "-10 Sn",
                                onClick = {
                                    seekVodTo(maxOf(0L, exoPlayer.currentPosition - 10000L))
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                        }

                        TvPlayerControlButton(
                            icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            label = if (isPlaying) "Durdur" else "Oynat",
                            isPrimary = true,
                            focusRequester = playPauseFocusRequester,
                            onClick = {
                                exoPlayer.playWhenReady = !exoPlayer.playWhenReady
                                lastInteractionTime = System.currentTimeMillis()
                            }
                        )

                        if (mediaType != PlayerMediaType.LIVE_CHANNEL) {
                            Spacer(modifier = Modifier.width(14.dp))
                            TvPlayerControlButton(
                                icon = Icons.Default.Forward10,
                                label = "+10 Sn",
                                onClick = {
                                    val target = exoPlayer.currentPosition + 10000L
                                    seekVodTo(if (duration > 0L) target.coerceAtMost(duration) else target)
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                            )
                        } else {
                            // Canlı Yayına Dön (Live Sync)
                            Spacer(modifier = Modifier.width(14.dp))
                            TvPlayerControlButton(
                                icon = Icons.Default.Sync,
                                label = "Canlıya Dön",
                                onClick = {
                                    playerManager.recoverLiveStream()
                                    viewModel.clearFailoverMessage()
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                            )
                        }

                        if (mediaType == PlayerMediaType.LIVE_CHANNEL && channelList.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(14.dp))
                            TvPlayerControlButton(
                                icon = Icons.Default.Tv,
                                label = "Kanallar",
                                onClick = {
                                    viewModel.toggleChannelListMenu()
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        // Ekran Boyutu (Aspect Ratio: Fit / Zoom / Fill)
                        TvPlayerControlButton(
                            icon = Icons.Default.AspectRatio,
                            label = resizeMode.title.substringBefore(" "),
                            onClick = {
                                viewModel.cycleResizeMode()
                                lastInteractionTime = System.currentTimeMillis()
                            }
                        )

                        // Manuel Yedek Kaynak / Sunucu Seçimi (Hem Canlı TV hem Film/Dizi!)
                        Spacer(modifier = Modifier.width(14.dp))
                        val isLiveTv = mediaType == PlayerMediaType.LIVE_CHANNEL
                        val totalSourcesCount = currentSources.size.coerceAtLeast(1)
                        val labelText = if (isLiveTv) "Kaynak (${currentSourceIndex + 1}/$totalSourcesCount)" else "Sunucu (${currentSourceIndex + 1}/$totalSourcesCount)"
                        TvPlayerControlButton(
                            icon = Icons.Default.Layers,
                            label = labelText,
                            onClick = {
                                viewModel.toggleSourceMenu()
                                lastInteractionTime = System.currentTimeMillis()
                            }
                        )

                        // Ses & Altyazı
                        Spacer(modifier = Modifier.width(14.dp))
                        TvPlayerControlButton(
                            icon = Icons.Default.Subtitles,
                            label = "Ses & Altyazı",
                            onClick = {
                                viewModel.toggleAudioSubMenu()
                                lastInteractionTime = System.currentTimeMillis()
                            }
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        // Teknik Bilgi HUD
                        TvPlayerControlButton(
                            icon = Icons.Default.Info,
                            label = "Bilgi",
                            onClick = {
                                viewModel.toggleInfoMenu()
                                lastInteractionTime = System.currentTimeMillis()
                            }
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        // Çıkış / Geri Dön
                        TvPlayerControlButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            label = "Geri",
                            onClick = {
                                viewModel.hideAllMenus()
                                onBack()
                            }
                        )
                    }
                }
            }
        }

        // --- YAN PANELLER (DRAWER MODALLARI) ---

        // A. Yedek Kaynaklar & Alternatif Sunucular Menüsü (Sağ Panel - Hem Canlı TV Hem VOD)
        if (showSourceMenu) {
            val isLiveTv = mediaType == PlayerMediaType.LIVE_CHANNEL
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(360.dp)
                    .align(Alignment.CenterEnd)
                    .background(Color(0xF512141A))
                    .border(1.dp, Color(0xFF2C3240))
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isLiveTv) "⚡ Yayın Kaynakları" else "🎬 Oynatıcı Sunucuları",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isLiveTv) "${currentSources.size} Kaynak" else "${currentSources.size} Sunucu",
                            color = Color(0xFFFFD54F),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isLiveTv) "Yayın donuyorsa veya takılıyorsa alternatif bir yedek seçin"
                               else "Video açılmıyorsa veya takılıyorsa alternatif bir sunucu seçin",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val sourceMenuFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
                    androidx.compose.runtime.LaunchedEffect(showSourceMenu) {
                        if (showSourceMenu) {
                            delay(120)
                            try { sourceMenuFocusRequester.requestFocus() } catch (e: Exception) {}
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(currentSources) { index, _ ->
                            val isSelected = index == currentSourceIndex
                            val itemInteraction = remember { MutableInteractionSource() }
                            val isItemFocused by itemInteraction.collectIsFocusedAsState()

                            val vSource = videoSources.getOrNull(index)
                            val titleText = if (isLiveTv) {
                                "Yayın Kaynağı ${index + 1}"
                            } else {
                                vSource?.serverName?.ifEmpty { "Sunucu ${index + 1}" } ?: "Sunucu ${index + 1}"
                            }
                            val subtitleText = if (isLiveTv) {
                                val url = currentSources.getOrNull(index) ?: ""
                                when {
                                    url.contains(".ts", ignoreCase = true) -> "Doğrudan MPEG-TS Akışı (Yedek)"
                                    url.contains(".m3u8", ignoreCase = true) -> "HLS Canlı Akış"
                                    index == 0 -> "Birincil Yayın Akışı"
                                    else -> "Yedek Alternatif $index"
                                }
                            } else {
                                if (index == 0) "Birincil Video Sunucusu" else "Alternatif Sunucu ${index}"
                            }

                            var boxModifier = Modifier
                                .fillMaxWidth()
                                .scale(if (isItemFocused) 1.04f else 1.0f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        isItemFocused -> Color.White
                                        isSelected -> Color(0xFF2C2411)
                                        else -> Color(0xFF1E212A)
                                    }
                                )
                                .border(
                                    width = if (isItemFocused) 2.dp else if (isSelected) 1.dp else 0.5.dp,
                                    color = if (isItemFocused) Color.White else if (isSelected) Color(0xFFFFB300) else Color(0xFF333846),
                                    shape = RoundedCornerShape(10.dp)
                                )

                            if (isSelected) {
                                boxModifier = boxModifier.focusRequester(sourceMenuFocusRequester)
                            }

                            Box(
                                modifier = boxModifier
                                    .onKeyEvent { keyEvent ->
                                        if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                            (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                             keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
                                             keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                                            if (!isLiveTv) {
                                                savedVodResumePosition = exoPlayer.currentPosition
                                            }
                                            viewModel.selectSourceIndex(index)
                                            lastInteractionTime = System.currentTimeMillis()
                                            true
                                        } else false
                                    }
                                    .clickable(
                                        interactionSource = itemInteraction,
                                        indication = null
                                    ) {
                                        if (!isLiveTv) {
                                            savedVodResumePosition = exoPlayer.currentPosition
                                        }
                                        viewModel.selectSourceIndex(index)
                                        lastInteractionTime = System.currentTimeMillis()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = titleText,
                                            color = if (isItemFocused) Color.Black else Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = subtitleText,
                                            color = if (isItemFocused) Color(0xFF555555) else Color.Gray,
                                            fontSize = 12.sp
                                        )
                                    }

                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    if (isItemFocused) Color.Black else Color(0xFFFFB300),
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "AKTİF",
                                                color = if (isItemFocused) Color.White else Color.Black,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // B. Hızlı Kanal Rehberi / Kanal Değiştirici (Sol Panel - SADECE CANLI TV)
        if (showChannelListMenu && mediaType == PlayerMediaType.LIVE_CHANNEL) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(380.dp)
                    .align(Alignment.CenterStart)
                    .background(Color(0xF50F1116))
                    .border(1.dp, Color(0xFF2C3240))
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📺 Hızlı Kanal Geçişi", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("${channelList.size} Kanal", color = Color(0xFF00E676), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "İzlemeye ara vermeden başka bir kanala geçin",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(channelList) { index, channel ->
                            val isCurrent = channel.id == currentChannel?.id
                            val itemInteraction = remember { MutableInteractionSource() }
                            val isItemFocused by itemInteraction.collectIsFocusedAsState()

                            val focusTargetIndex = channelList.indexOfFirst { it.id == currentChannel?.id }.let { if (it >= 0) it else 0 }
                            var channelModifier = Modifier
                                    .fillMaxWidth()
                                    .scale(if (isItemFocused) 1.04f else 1.0f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        when {
                                            isItemFocused -> Color.White
                                            isCurrent -> Color(0xFF2D1619)
                                            else -> Color(0xFF1B1E26)
                                        }
                                    )
                                    .border(
                                        width = if (isItemFocused) 2.dp else if (isCurrent) 1.5.dp else 0.5.dp,
                                        color = if (isItemFocused) Color.White else if (isCurrent) Color(0xFFE50914) else Color(0xFF2F3442),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                            if (index == focusTargetIndex) {
                                channelModifier = channelModifier.focusRequester(channelListFocusRequester)
                            }

                            Box(
                                modifier = channelModifier
                                    .focusable(interactionSource = itemInteraction)
                                    .clickable {
                                        viewModel.playChannel(channel, channelList)
                                        lastInteractionTime = System.currentTimeMillis()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Kanal Sıra Numarası
                                    Text(
                                        text = "${index + 1}",
                                        color = if (isItemFocused) Color.Black else Color.Gray,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(28.dp)
                                    )

                                    // Kanal Logosu / İkonu
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isItemFocused) Color(0xFFECEFF1) else Color(0xFF262C38)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!channel.logoUrl.isNullOrEmpty()) {
                                            AsyncImage(
                                                model = channel.logoUrl,
                                                contentDescription = channel.name,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Fit
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Tv,
                                                contentDescription = null,
                                                tint = if (isItemFocused) Color.Black else Color.LightGray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = channel.name,
                                            color = if (isItemFocused) Color.Black else Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (channel.sourceCount > 1) {
                                            Text(
                                                text = "⚡ ${channel.sourceCount} Kaynak",
                                                color = if (isItemFocused) Color(0xFF795548) else Color(0xFFFFD54F),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    if (isCurrent) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(Color(0xFFE50914), CircleShape)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // C. Ses İzi & Altyazı Menüsü (Sağ Panel)
        if (showAudioSubMenu) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(360.dp)
                    .align(Alignment.CenterEnd)
                    .background(Color(0xF514161E))
                    .border(1.dp, Color(0xFF2C3240))
                    .padding(24.dp)
            ) {
                Column {
                    Text("🔊 Ses & Altyazı", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(18.dp))

                    // VOD Dublaj & Altyazı Kaynak Tercihi
                    if (mediaType != PlayerMediaType.LIVE_CHANNEL && (dubbingIframes.isNotEmpty() || subtitleIframes.isNotEmpty())) {
                        Text("Yayın / Dil Seçeneği", color = Color.LightGray, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (dubbingIframes.isNotEmpty()) {
                                val isDubSelected = selectedLanguage.contains("dublaj", ignoreCase = true)
                                val dubInteraction = remember { MutableInteractionSource() }
                                val isDubFocused by dubInteraction.collectIsFocusedAsState()
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isDubFocused) Color.White else if (isDubSelected) Color(0xFFE50914) else Color(0xFF1E212A))
                                        .border(1.dp, if (isDubSelected) Color(0xFFFFCC00) else Color(0xFF333744), RoundedCornerShape(8.dp))
                                        .focusable(interactionSource = dubInteraction)
                                        .clickable {
                                            savedVodResumePosition = currentPosition
                                            viewModel.switchVodLanguage("Türkçe Dublaj", currentPosition)
                                            lastInteractionTime = System.currentTimeMillis()
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "TR Dublaj",
                                        color = if (isDubFocused) Color.Black else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = if (isDubSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }

                            if (subtitleIframes.isNotEmpty()) {
                                val isSubSelected = selectedLanguage.contains("altyaz", ignoreCase = true)
                                val subInteraction = remember { MutableInteractionSource() }
                                val isSubFocused by subInteraction.collectIsFocusedAsState()
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSubFocused) Color.White else if (isSubSelected) Color(0xFF0284C7) else Color(0xFF1E212A))
                                        .border(1.dp, if (isSubSelected) Color(0xFFFFCC00) else Color(0xFF333744), RoundedCornerShape(8.dp))
                                        .focusable(interactionSource = subInteraction)
                                        .clickable {
                                            savedVodResumePosition = currentPosition
                                            viewModel.switchVodLanguage("Türkçe Altyazı", currentPosition)
                                            lastInteractionTime = System.currentTimeMillis()
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "TR Altyazı",
                                        color = if (isSubFocused) Color.Black else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSubSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                    }

                    Text("Ses İzi", color = Color.LightGray, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (audioTracks.isEmpty()) {
                        Text("• Varsayılan Ses İzi", color = Color(0xFFFFCC00), fontSize = 14.sp)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 160.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            itemsIndexed(audioTracks) { _, track ->
                                val trackInteraction = remember { MutableInteractionSource() }
                                val isTrackFocused by trackInteraction.collectIsFocusedAsState()
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isTrackFocused) Color.White else if (track.isSelected) Color(0xFF2A2810) else Color(0xFF1E212A))
                                        .border(
                                            1.dp,
                                            if (track.isSelected) Color(0xFFFFCC00) else Color(0xFF333744),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .focusable(interactionSource = trackInteraction)
                                        .clickable {
                                            playerManager.selectAudioTrack(track.groupIndex, track.trackIndex)
                                            audioTracks = playerManager.getAudioTracks()
                                            lastInteractionTime = System.currentTimeMillis()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = track.label,
                                            color = if (isTrackFocused) Color.Black else Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (track.isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = if (isTrackFocused) Color.Black else Color(0xFFFFCC00),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text("Altyazı", color = Color.LightGray, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (subtitleTracks.isEmpty()) {
                        Text("• Altyazı bulunamadı", color = Color.Gray, fontSize = 13.sp)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 160.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Altyazı Kapat Seçeneği
                            val noneSelected = subtitleTracks.none { it.isSelected }
                            item {
                                val offInteraction = remember { MutableInteractionSource() }
                                val isOffFocused by offInteraction.collectIsFocusedAsState()
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isOffFocused) Color.White else if (noneSelected) Color(0xFF2A2810) else Color(0xFF1E212A))
                                        .border(1.dp, if (noneSelected) Color(0xFFFFCC00) else Color(0xFF333744), RoundedCornerShape(8.dp))
                                        .focusable(interactionSource = offInteraction)
                                        .clickable {
                                            playerManager.selectSubtitleTrack(null, null)
                                            subtitleTracks = playerManager.getSubtitleTracks()
                                            lastInteractionTime = System.currentTimeMillis()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Kapalı",
                                            color = if (isOffFocused) Color.Black else Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = if (noneSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (noneSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = if (isOffFocused) Color.Black else Color(0xFFFFCC00),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            itemsIndexed(subtitleTracks) { _, track ->
                                val subInteraction = remember { MutableInteractionSource() }
                                val isSubFocused by subInteraction.collectIsFocusedAsState()
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSubFocused) Color.White else if (track.isSelected) Color(0xFF2A2810) else Color(0xFF1E212A))
                                        .border(1.dp, if (track.isSelected) Color(0xFFFFCC00) else Color(0xFF333744), RoundedCornerShape(8.dp))
                                        .focusable(interactionSource = subInteraction)
                                        .clickable {
                                            playerManager.selectSubtitleTrack(track.groupIndex, track.trackIndex)
                                            subtitleTracks = playerManager.getSubtitleTracks()
                                            lastInteractionTime = System.currentTimeMillis()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = track.label,
                                            color = if (isSubFocused) Color.Black else Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        if (track.isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = if (isSubFocused) Color.Black else Color(0xFFFFCC00),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // D. Teknik Yayın Bilgileri HUD Paneli
        if (showInfoMenu) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 80.dp, end = 32.dp)
                    .width(360.dp)
                    .background(Color(0xF012141A), RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0xFF2D3240), RoundedCornerShape(14.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📊 Yayın Bilgisi", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(if (duration <= 0) "CANLI HLS" else "VOD", color = Color(0xFF00E676), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    val info = videoInfo
                    val resText = if (info != null && info.width > 0) "${info.width}x${info.height} @ ${info.frameRate.toInt()} FPS" else "Otomatik"
                    val vCodec = info?.videoMime?.substringAfterLast("/")?.uppercase() ?: "H.264"
                    val aCodec = info?.audioMime?.substringAfterLast("/")?.uppercase() ?: "AAC"
                    val bufferSec = ((info?.bufferedDurationMs ?: 0L) / 1000.0)
                    val streamStateText = when {
                        playerError != null -> "Koptu (Hata veya Zaman Aşımı)"
                        isBuffering -> "Bağlanıyor / Yükleniyor..."
                        else -> "Aktif (Sağlıklı)"
                    }

                    InfoRow(label = "Yayın Durumu", value = streamStateText)
                    InfoRow(label = "Çözünürlük", value = resText)
                    InfoRow(label = "Video Kodek", value = vCodec)
                    InfoRow(label = "Ses Kodek", value = aCodec)
                    InfoRow(label = "Tampon (Buffer)", value = String.format("%.1f sn", bufferSec))
                    InfoRow(label = "Ekran Boyutu", value = resizeMode.title)
                    if (currentSources.size > 1) {
                        InfoRow(label = "Aktif Kaynak", value = "Kaynak ${currentSourceIndex + 1} / ${currentSources.size}")
                    }
                }
            }
        }

        // Hızlı İleri / Geri Sarma HUD Göstergesi (Kumanda Sağ / Sol tuşları ile)
        if (isSeekingHudVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xEE0B0F19), RoundedCornerShape(20.dp))
                    .border(1.5.dp, Color(0xFF38BDF8), RoundedCornerShape(20.dp))
                    .padding(horizontal = 32.dp, vertical = 20.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (seekDirectionForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(36.dp)
                        )
                        val offsetSecAbs = kotlin.math.abs(seekOffsetSeconds)
                        val offsetText = if (offsetSecAbs >= 60) {
                            val mins = offsetSecAbs / 60
                            val secs = offsetSecAbs % 60
                            String.format("%s%02d:%02d", if (seekDirectionForward) "+" else "-", mins, secs)
                        } else {
                            String.format("%s%d sn", if (seekDirectionForward) "+" else "-", offsetSecAbs)
                        }
                        Text(
                            text = offsetText,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val totalDur = if (exoPlayer.duration > 0) exoPlayer.duration else duration
                    val timeProgress = if (totalDur > 0) (seekTargetPosition.toFloat() / totalDur.toFloat()).coerceIn(0f, 1f) else 0f

                    // İnce ilerleme çubuğu
                    Box(
                        modifier = Modifier
                            .width(220.dp)
                            .height(6.dp)
                            .background(Color(0xFF334155), RoundedCornerShape(3.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(timeProgress)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF38BDF8), Color(0xFF818CF8))
                                    ),
                                    RoundedCornerShape(3.dp)
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${formatTime(seekTargetPosition)} / ${formatTime(totalDur)}",
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Canlı Yayında İleri/Geri Sarma Uyarı Rozeti
        if (liveWarningVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 60.dp)
                    .background(Color(0xEE1E1B18), RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(16.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFFEF4444), CircleShape)
                    )
                    Text(
                        text = "Canlı Yayın • İleri/Geri sarma desteklenmiyor",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
