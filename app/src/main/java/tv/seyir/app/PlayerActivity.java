package tv.seyir.app;

import android.app.*;
import android.content.Context;
import android.media.AudioManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.datasource.*;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.AspectRatioFrameLayout;
import org.json.JSONObject;
import java.util.*;

@androidx.media3.common.util.UnstableApi
public final class PlayerActivity extends Activity {
    private ExoPlayer player;
    private PlayerView video;
    private TitleItem title;
    private Library library;
    private LinearLayout topPanel, bottomPanel;
    private TextView playerTitle, playerStatusBadge, tvCurrentTime, tvTotalTime, tvGestureHud;
    private SeekBar playerProgress;
    private Button btnTopBack, btnRewind, btnPlayPause, btnNextSource, btnForward, btnResize, btnQuality, btnAudio, btnSubtitle, btnSpeed;
    private Button btnChannels, btnPrevChannel, btnNextChannel, btnLock, btnFloatingUnlock;

    private String url;
    private final Map<String,String> headers = new HashMap<>();
    private final Map<String,String> initialHeaders = new HashMap<>();
    private String initialUrl;
    private final java.util.concurrent.ExecutorService probeExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private java.util.concurrent.Future<?> pendingProbe;
    private String probedUrl, probedMime;
    private int probeGeneration;
    private boolean probing;
    private final LiveRecovery liveRecovery = new LiveRecovery();
    private Runnable pendingRecovery;
    private final List<String> fallbackList = new ArrayList<>();
    private long position;
    private boolean shouldPlay = true, closing = false, userScrubbing = false;
    private int resizeModeIndex = 0;
    private static final int[] RESIZE_MODES = {
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
    };
    private static final String[] RESIZE_LABELS = {
        "📐 Boyut: Sığdır (Fit)",
        "📐 Boyut: Ekrana Yay (Fill)",
        "📐 Boyut: Yakınlaştır (Zoom)",
        "📐 Boyut: 16:9 Sabit"
    };
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Handler hudHandler = new Handler(Looper.getMainLooper());
    private int selectedQualityHeight = 0;
    private float currentSpeed = 1.0f;

    // Gestures and Lock Support
    private boolean isLocked = false;
    private AudioManager audioManager;
    private GestureDetector gestureDetector;
    private float touchStartX, touchStartY;
    private boolean isTrackingBrightness = false, isTrackingVolume = false;
    private float initialBrightness = 0.5f;
    private int initialVolume = 0, maxVolume = 15;

    private final Runnable hideHudRunnable = () -> {
        if (tvGestureHud != null) tvGestureHud.setVisibility(View.GONE);
    };

    private final Runnable hideUnlockBtn = () -> {
        if (btnFloatingUnlock != null) btnFloatingUnlock.setVisibility(View.GONE);
    };

    private final Runnable hideControls = () -> {
        if (player != null && player.isPlaying() && !insideControls(getCurrentFocus())) overlays(false);
    };

    private final Runnable updateProgressTask = new Runnable() {
        @Override public void run() {
            if (player != null && !closing) {
                liveRecovery.playing(player.isPlaying(), SystemClock.elapsedRealtime());
                boolean isLive = player.isCurrentMediaItemLive() || player.getDuration() <= 0;
                if (isLive) {
                    tvCurrentTime.setText("CANLI");
                    tvTotalTime.setText("🔴 CANLI");
                    playerProgress.setEnabled(false);
                    playerProgress.setProgress(1000);
                    btnRewind.setVisibility(View.GONE);
                    btnForward.setVisibility(View.GONE);
                } else {
                    btnRewind.setVisibility(View.VISIBLE);
                    btnForward.setVisibility(View.VISIBLE);
                    playerProgress.setEnabled(true);
                    long pos = player.getCurrentPosition();
                    long dur = player.getDuration();
                    if (dur > 0 && !userScrubbing) {
                        tvCurrentTime.setText(formatTime(pos));
                        tvTotalTime.setText(formatTime(dur));
                        playerProgress.setProgress((int)(pos * 1000 / dur));
                    }
                }
                updateQualityLabel();
                progressHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        }

        try {
            title = TitleItem.read(new JSONObject(getIntent().getStringExtra("item")), Source.SPORTS);
            url = getIntent().getStringExtra("url");
            if (title.source != Source.SPORTS && !MediaPolicy.isVideo(url)) { finish(); return; }
            String[] fallbacks = getIntent().getStringArrayExtra("fallbackUrls");
            if (fallbacks != null) {
                for (String fb : fallbacks) {
                    if (fb != null && !fb.isEmpty() && !fallbackList.contains(fb)) {
                        fallbackList.add(fb);
                    }
                }
            }
            String rawHeaders = getIntent().getStringExtra("headers");
            if (rawHeaders != null) {
                JSONObject h = new JSONObject(rawHeaders);
                Iterator<String> it = h.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    if (k.equalsIgnoreCase("Referer") || k.equalsIgnoreCase("Origin") || k.equalsIgnoreCase("User-Agent")) {
                        headers.put(k, h.optString(k));
                    }
                }
            }
        } catch (Exception e) { finish(); return; }

        initialUrl = url;
        initialHeaders.putAll(headers);
        fallbackList.remove(url);
        library = new Library(this);
        position = state == null ? library.position(title) : state.getLong("position", 0);
        setContentView(R.layout.activity_player);
        hideBars();
        bindViews();
        setupGestures();
    }

    private void bindViews() {
        video = findViewById(R.id.player_view);
        topPanel = findViewById(R.id.player_top_panel);
        bottomPanel = findViewById(R.id.player_bottom_panel);
        playerTitle = findViewById(R.id.player_title);
        playerStatusBadge = findViewById(R.id.player_status_badge);
        tvCurrentTime = findViewById(R.id.tv_current_time);
        tvTotalTime = findViewById(R.id.tv_total_time);
        playerProgress = findViewById(R.id.player_progress);
        tvGestureHud = findViewById(R.id.tv_gesture_hud);
        btnFloatingUnlock = findViewById(R.id.btn_floating_unlock);

        btnTopBack = findViewById(R.id.btn_top_back);
        btnChannels = findViewById(R.id.btn_channels);
        btnPrevChannel = findViewById(R.id.btn_prev_channel);
        btnRewind = findViewById(R.id.btn_rewind);
        btnPlayPause = findViewById(R.id.btn_play_pause);
        btnForward = findViewById(R.id.btn_forward);
        btnNextChannel = findViewById(R.id.btn_next_channel);
        btnNextSource = findViewById(R.id.btn_next_source);
        btnResize = findViewById(R.id.btn_resize);
        btnQuality = findViewById(R.id.btn_quality);
        btnLock = findViewById(R.id.btn_lock);
        btnAudio = findViewById(R.id.btn_audio);
        btnSubtitle = findViewById(R.id.btn_subtitle);
        btnSpeed = findViewById(R.id.btn_speed);

        playerTitle.setText(title != null ? title.title : "Seyir TV");

        Button[] allButtons = {btnTopBack, btnChannels, btnPrevChannel, btnRewind, btnPlayPause, btnNextSource,
                btnForward, btnNextChannel, btnResize, btnQuality, btnLock, btnAudio, btnSubtitle, btnSpeed};
        for (Button b : allButtons) {
            if (b != null) Ui.focus(b);
        }

        btnTopBack.setOnClickListener(v -> closePlayer());
        btnChannels.setOnClickListener(v -> showChannelsDialog());
        btnPrevChannel.setOnClickListener(v -> prevChannel());
        btnNextChannel.setOnClickListener(v -> nextChannel());
        btnRewind.setOnClickListener(v -> seek(-10000));
        btnPlayPause.setOnClickListener(v -> togglePlay());
        btnForward.setOnClickListener(v -> seek(10000));
        btnNextSource.setOnClickListener(v -> nextSource());
        btnResize.setOnClickListener(v -> cycleResizeMode());
        btnQuality.setOnClickListener(v -> selectQuality());
        btnAudio.setOnClickListener(v -> selectTracks(C.TRACK_TYPE_AUDIO));
        btnSubtitle.setOnClickListener(v -> selectTracks(C.TRACK_TYPE_TEXT));
        btnSpeed.setOnClickListener(v -> selectSpeed());

        btnLock.setOnClickListener(v -> lockControls(true));
        btnFloatingUnlock.setOnClickListener(v -> lockControls(false));

        updateNextSourceButton();

        playerProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && player != null && player.getDuration() > 0) {
                    long target = player.getDuration() * progress / 1000;
                    tvCurrentTime.setText(formatTime(target));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {
                userScrubbing = true;
                progressHandler.removeCallbacks(hideControls);
            }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                userScrubbing = false;
                if (player != null && player.getDuration() > 0) {
                    long target = player.getDuration() * seekBar.getProgress() / 1000;
                    player.seekTo(target);
                }
                scheduleHide();
            }
        });
    }

    private void setupGestures() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (isLocked) {
                    showUnlockButton();
                    return true;
                }
                boolean showing = bottomPanel != null && bottomPanel.getVisibility() == View.VISIBLE;
                overlays(!showing);
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (isLocked) return false;
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                if (e.getX() < screenWidth / 2f) {
                    seek(-10000);
                    showHud("⏪ -10 sn");
                } else {
                    seek(10000);
                    showHud("⏩ +10 sn");
                }
                return true;
            }
        });
    }

    private void showHud(String text) {
        if (tvGestureHud == null) return;
        hudHandler.removeCallbacks(hideHudRunnable);
        tvGestureHud.setText(text);
        tvGestureHud.setVisibility(View.VISIBLE);
        hudHandler.postDelayed(hideHudRunnable, 1100);
    }

    private void showUnlockButton() {
        if (btnFloatingUnlock != null) {
            btnFloatingUnlock.setVisibility(View.VISIBLE);
            progressHandler.removeCallbacks(hideUnlockBtn);
            progressHandler.postDelayed(hideUnlockBtn, 3000);
        }
    }

    private void lockControls(boolean lock) {
        isLocked = lock;
        if (lock) {
            overlays(false);
            showUnlockButton();
            showHud("🔒 Ekran Kilitlendi");
        } else {
            if (btnFloatingUnlock != null) btnFloatingUnlock.setVisibility(View.GONE);
            overlays(true);
            showHud("🔓 Kilit Açıldı");
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (isLocked) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                showUnlockButton();
            }
            return super.dispatchTouchEvent(event);
        }

        gestureDetector.onTouchEvent(event);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchStartX = event.getX();
                touchStartY = event.getY();
                isTrackingBrightness = touchStartX < (screenWidth * 0.45f);
                isTrackingVolume = touchStartX > (screenWidth * 0.55f);
                if (audioManager != null) {
                    initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                }
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                initialBrightness = lp.screenBrightness < 0 ? 0.5f : lp.screenBrightness;
                break;

            case MotionEvent.ACTION_MOVE:
                float deltaY = touchStartY - event.getY();
                float percent = deltaY / (screenHeight * 0.70f);
                if (Math.abs(deltaY) > 30) {
                    if (isTrackingBrightness) {
                        float newB = Math.max(0.01f, Math.min(1.0f, initialBrightness + percent));
                        WindowManager.LayoutParams wlp = getWindow().getAttributes();
                        wlp.screenBrightness = newB;
                        getWindow().setAttributes(wlp);
                        showHud("☀️ Parlaklık: %" + (int)(newB * 100));
                    } else if (isTrackingVolume && audioManager != null) {
                        int deltaVol = (int)(percent * maxVolume);
                        int newVol = Math.max(0, Math.min(maxVolume, initialVolume + deltaVol));
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0);
                        showHud("🔊 Ses: %" + (int)(newVol * 100 / maxVolume));
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isTrackingBrightness = false;
                isTrackingVolume = false;
                break;
        }

        return super.dispatchTouchEvent(event);
    }

    private void showChannelsDialog() {
        List<SportsManager.SportChannel> channels = SportsManager.getCachedChannels();
        if (channels.isEmpty()) {
            Toast.makeText(this, "Kanal listesi yükleniyor, lütfen bekleyin...", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] items = new String[channels.size()];
        for (int i = 0; i < channels.size(); i++) {
            SportsManager.SportChannel c = channels.get(i);
            boolean active = title != null && (c.name.equalsIgnoreCase(title.title) || (c.urls != null && c.urls.contains(url)));
            items[i] = (active ? "▶ " : "    ") + c.name + " (" + c.category + ")";
        }
        new AlertDialog.Builder(this)
            .setTitle("📺 Kanal Seç (" + channels.size() + " Kanal)")
            .setItems(items, (dialog, which) -> switchChannel(channels.get(which)))
            .setNegativeButton("Kapat", null)
            .show();
    }

    private void prevChannel() {
        List<SportsManager.SportChannel> channels = SportsManager.getCachedChannels();
        if (channels.isEmpty() || title == null) return;
        int currentIdx = -1;
        for (int i = 0; i < channels.size(); i++) {
            if (channels.get(i).name.equalsIgnoreCase(title.title)) { currentIdx = i; break; }
        }
        int prevIdx = currentIdx <= 0 ? channels.size() - 1 : currentIdx - 1;
        switchChannel(channels.get(prevIdx));
    }

    private void nextChannel() {
        List<SportsManager.SportChannel> channels = SportsManager.getCachedChannels();
        if (channels.isEmpty() || title == null) return;
        int currentIdx = -1;
        for (int i = 0; i < channels.size(); i++) {
            if (channels.get(i).name.equalsIgnoreCase(title.title)) { currentIdx = i; break; }
        }
        int nextIdx = (currentIdx + 1) % channels.size();
        switchChannel(channels.get(nextIdx));
    }

    private void switchChannel(SportsManager.SportChannel ch) {
        if (ch == null || ch.urls == null || ch.urls.isEmpty()) return;
        release();
        title = new TitleItem(Source.SPORTS, ch.name, ch.urls.get(0), ch.logo, "");
        playerTitle.setText(ch.name);
        fallbackList.clear();
        for (int i = 1; i < ch.urls.size(); i++) {
            fallbackList.add(ch.urls.get(i));
        }
        url = ch.urls.get(0);
        initialUrl = url;
        headers.clear();
        if (ch.headers != null) headers.putAll(ch.headers);
        liveRecovery.reset();
        position = 0;
        shouldPlay = true;
        updateNextSourceButton();
        playerStatusBadge.setText("Kanal açılıyor…");
        showHud("📺 " + ch.name);
        initialize();
    }

    @Override protected void onResume() {
        super.onResume();
        hideBars();
        if (player == null && !closing) initialize();
    }

    @Override protected void onPause() {
        super.onPause();
        release();
    }

    private void initialize() {
        if (closing || isFinishing() || isDestroyed()) return;
        playerStatusBadge.setText("Bağlanıyor…");
        scheduleHide();
        startProbe(url);
    }

    private void startProbe(String targetUrl) {
        int gen = ++probeGeneration;
        probing = true;
        if (pendingProbe != null) pendingProbe.cancel(true);
        StreamRequest request = new StreamRequest(targetUrl, headers);
        pendingProbe = probeExecutor.submit(() -> {
            StreamProbe.Result probe = null;
            try {
                probe = StreamProbe.inspect(request, request.headers);
            } catch (Exception ignored) {
            }
            final StreamProbe.Result p = probe;
            runOnUiThread(() -> {
                if (closing || isFinishing() || isDestroyed() || gen != probeGeneration) return;
                probing = false;
                probedUrl = p != null && p.resolvedUrl != null ? p.resolvedUrl : request.url;
                probedMime = p != null ? p.mime : null;
                initPlayer(request);
            });
        });
    }

    private void initPlayer(StreamRequest request) {
        try {
            release();
            DefaultHttpDataSource.Factory httpData = new DefaultHttpDataSource.Factory()
                .setUserAgent(request.headers.getOrDefault("User-Agent", "SeyirTV-Player"))
                .setConnectTimeoutMs(8000)
                .setReadTimeoutMs(8000)
                .setAllowCrossProtocolRedirects(true);
            if (!request.headers.isEmpty()) httpData.setDefaultRequestProperties(request.headers);

            DataSource.Factory playbackData = httpData;
            if (title.source == Source.SPORTS) {
                playbackData = () -> new RefreshingPlaylistDataSource(httpData.createDataSource(), new PlaylistRoute(request.url));
            }

            player = new ExoPlayer.Builder(this, new DefaultRenderersFactory(this).setEnableDecoderFallback(true))
                .setMediaSourceFactory(new DefaultMediaSourceFactory(playbackData))
                .setSeekBackIncrementMs(10000)
                .setSeekForwardIncrementMs(10000)
                .build();

            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
            player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .setPreferredAudioLanguage("tr")
                .setPreferredTextLanguage("tr")
                .setForceHighestSupportedBitrate(true)
                .build());

            video.setPlayer(player);
            player.addListener(new Player.Listener() {
                @Override public void onIsPlayingChanged(boolean playing) {
                    liveRecovery.playing(playing, SystemClock.elapsedRealtime());
                    btnPlayPause.setText(playing ? "❚❚ Duraklat" : "▶ Oynat");
                    if (playing) {
                        playerStatusBadge.setText("Oynatılıyor");
                        scheduleHide();
                    } else {
                        playerStatusBadge.setText("Duraklatıldı");
                        overlays(true);
                    }
                }
                @Override public void onPlaybackStateChanged(int s) {
                    if (player == null || closing) return;
                    if (s == Player.STATE_READY) {
                        boolean isLive = player.isCurrentMediaItemLive() || player.getDuration() <= 0;
                        playerStatusBadge.setText(isLive ? "🔴 CANLI" : "Hazır");
                        if (!isLive) library.save("history", title, false);
                        updateQualityLabel();
                        scheduleHide();
                    } else if (s == Player.STATE_BUFFERING) {
                        playerStatusBadge.setText("Yükleniyor…");
                    } else if (s == Player.STATE_ENDED) {
                        library.progress(title, player.getDuration(), player.getDuration());
                        playerStatusBadge.setText("Tamamlandı");
                        overlays(true);
                    }
                }
                @Override public void onVideoSizeChanged(VideoSize videoSize) {
                    updateQualityLabel();
                }
                @Override public void onPlayerError(PlaybackException e) {
                    handlePlaybackError(e);
                }
            });

            MediaItem.Builder item = new MediaItem.Builder().setUri(request.url);
            if (StreamProbe.HLS.equals(probedMime) || (title.source != Source.SPORTS && StreamRequest.isHls(request.url))) {
                item.setMimeType(MimeTypes.APPLICATION_M3U8);
            }
            player.setMediaItem(item.build());
            if (title.source == Source.SPORTS) player.seekToDefaultPosition();
            else player.seekTo(position);
            player.prepare();
            player.setPlayWhenReady(shouldPlay);
            btnPlayPause.requestFocus();
            progressHandler.post(updateProgressTask);
        } catch (RuntimeException e) {
            release();
            showError(e.getClass().getSimpleName());
        }
    }

    private void handlePlaybackError(PlaybackException error) {
        int httpStatus = 0;
        String requestKind = "";
        Throwable cause = error;
        while (cause != null) {
            if (cause instanceof HttpDataSource.InvalidResponseCodeException) {
                HttpDataSource.InvalidResponseCodeException response = (HttpDataSource.InvalidResponseCodeException) cause;
                httpStatus = response.responseCode;
                String path = response.dataSpec.uri.getPath();
                requestKind = path != null && (path.endsWith(".m3u8") || path.endsWith(".m3u"))
                    ? "Yayın listesi" : "Yayın isteği";
                break;
            }
            cause = cause.getCause();
        }
        String detail = httpStatus == 0 ? error.getErrorCodeName() : requestKind + " — HTTP " + httpStatus;
        boolean recoverable = LiveRecovery.retryHttp(httpStatus)
            || error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW
            || error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
            || error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT;
        long delay = title.source == Source.SPORTS && recoverable ? liveRecovery.nextDelay() : -1;
        if (delay < 0) { tryNextSource(detail); return; }
        release();
        position = 0;
        playerStatusBadge.setText("Bağlantı yenileniyor… " + detail);
        pendingRecovery = () -> {
            pendingRecovery = null;
            if (!closing && !isFinishing() && !isDestroyed()) initialize();
        };
        progressHandler.postDelayed(pendingRecovery, delay);
    }

    private void tryNextSource(String error) {
        if (url != null && !url.contains("150.230.29.27:3000/proxy")) {
            String proxied = SportsManager.toOracleProxy(url);
            if (!fallbackList.contains(proxied)) {
                fallbackList.add(0, proxied);
            }
        }
        while (!fallbackList.isEmpty()) {
            String next = fallbackList.remove(0);
            if (next == null || next.trim().isEmpty() || next.equals(url)) continue;
            release();
            url = next;
            liveRecovery.reset();
            if (title.source == Source.SPORTS) position = 0;
            playerStatusBadge.setText("Yedek sunucu deneniyor…");
            initialize();
            updateNextSourceButton();
            return;
        }
        showError(error);
    }

    private void updateQualityLabel() {
        if (btnQuality == null) return;
        int currentH = player != null && player.getVideoSize().height > 0 ? player.getVideoSize().height : selectedQualityHeight;
        btnQuality.setText(currentH > 0 ? "⚙️ " + currentH + "p" + (currentH >= 1080 ? " FHD" : (currentH >= 720 ? " HD" : "")) : "⚙️ Kalite: 1080p");
    }

    private void selectQuality() {
        if (player == null) return;
        List<String> labels = new ArrayList<>();
        List<Integer> heights = new ArrayList<>();
        List<TrackSelectionOverride> overrides = new ArrayList<>();

        labels.add("● Otomatik (1080p Maksimum)");
        heights.add(0);
        overrides.add(null);

        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() == C.TRACK_TYPE_VIDEO) {
                for (int i = 0; i < group.length; i++) {
                    if (group.isTrackSupported(i)) {
                        Format f = group.getTrackFormat(i);
                        if (f.height > 0) {
                            String desc = f.height + "p" + (f.height >= 2160 ? " (UHD 4K)" : f.height >= 1080 ? " (Full HD)" : f.height >= 720 ? " (HD)" : " (SD)");
                            if (f.averageBitrate > 0) desc += String.format(Locale.ROOT," · %.1f Mbps", f.averageBitrate/1000000.0);
                            if (f.frameRate > 0) desc += String.format(Locale.ROOT," · %.0f fps", f.frameRate);
                            labels.add(desc);
                            heights.add(f.height);
                            overrides.add(new TrackSelectionOverride(group.getMediaTrackGroup(), i));
                        }
                    }
                }
            }
        }
        new AlertDialog.Builder(this).setTitle("⚙️ Görüntü Kalitesi Seç")
            .setItems(labels.toArray(new String[0]), (d, w) -> {
                if (player == null) return;
                TrackSelectionParameters.Builder p = player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO);
                if (w == 0) {
                    selectedQualityHeight = 0;
                    p.setForceHighestSupportedBitrate(true);
                } else {
                    selectedQualityHeight = heights.get(w);
                    p.setOverrideForType(overrides.get(w));
                }
                player.setTrackSelectionParameters(p.build());
                updateQualityLabel();
                scheduleHide();
            }).show();
    }

    private void selectTracks(int type) {
        if (player == null) return;
        List<String> labels = new ArrayList<>();
        List<TrackSelectionOverride> overrides = new ArrayList<>();
        labels.add(type == C.TRACK_TYPE_TEXT ? "Kapalı" : "Otomatik");
        overrides.add(null);

        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() == type) {
                for (int i = 0; i < group.length; i++) {
                    if (group.isTrackSupported(i)) {
                        Format f = group.getTrackFormat(i);
                        String label = f.label != null && !f.label.isEmpty() ? f.label : f.language != null ? f.language : "Parça " + (i + 1);
                        labels.add(label);
                        overrides.add(new TrackSelectionOverride(group.getMediaTrackGroup(), i));
                    }
                }
            }
        }
        if (labels.size() <= 1) {
            Toast.makeText(this, "Bu yayında alternatif parça bulunamadı", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle(type == C.TRACK_TYPE_AUDIO ? "🔊 Ses Dili Seç" : "💬 Altyazı Seç")
            .setItems(labels.toArray(new String[0]), (d, w) -> {
                if (player == null) return;
                TrackSelectionParameters.Builder p = player.getTrackSelectionParameters().buildUpon()
                    .clearOverridesOfType(type)
                    .setTrackTypeDisabled(type, type == C.TRACK_TYPE_TEXT && w == 0);
                if (w > 0) p.setOverrideForType(overrides.get(w));
                player.setTrackSelectionParameters(p.build());
                scheduleHide();
            }).show();
    }

    private void selectSpeed() {
        float[] speeds = {0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
        String[] speedLabels = {"0.75×", "1.0× (Normal)", "1.25×", "1.5×", "2.0×"};
        new AlertDialog.Builder(this).setTitle("⚡ Oynatma Hızı")
            .setItems(speedLabels, (d, w) -> {
                currentSpeed = speeds[w];
                if (player != null) player.setPlaybackSpeed(currentSpeed);
                btnSpeed.setText("⚡ " + speeds[w] + "×");
                scheduleHide();
            }).show();
    }

    private void updateNextSourceButton() {
        if (btnNextSource != null) {
            btnNextSource.setVisibility(fallbackList.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    private void nextSource() {
        while (!fallbackList.isEmpty()) {
            String nextUrl = fallbackList.remove(0);
            if (nextUrl != null && !nextUrl.trim().isEmpty() && !nextUrl.equals(url)) {
                url = nextUrl;
                playerStatusBadge.setText("Yedek kaynak açılıyor…");
                showHud("🔄 Yedek Kaynak");
                release();
                initialize();
                updateNextSourceButton();
                return;
            }
        }
        Toast.makeText(this, "Alternatif kaynak bulunamadı", Toast.LENGTH_SHORT).show();
    }

    private void cycleResizeMode() {
        resizeModeIndex = (resizeModeIndex + 1) % RESIZE_MODES.length;
        video.setResizeMode(RESIZE_MODES[resizeModeIndex]);
        btnResize.setText(RESIZE_LABELS[resizeModeIndex]);
        showHud(RESIZE_LABELS[resizeModeIndex]);
        scheduleHide();
    }

    private void togglePlay() {
        if (player == null) return;
        if (player.getPlayWhenReady()) {
            player.pause();
            showHud("❚❚ Duraklatıldı");
        } else {
            player.play();
            showHud("▶ Oynatılıyor");
        }
        overlays(true);
        scheduleHide();
    }

    private void seek(long delta) {
        if (player == null) return;
        long end = player.getDuration();
        long target = Math.max(0, player.getCurrentPosition() + delta);
        if (end > 0) target = Math.min(target, end);
        player.seekTo(target);
        overlays(true);
        scheduleHide();
    }

    private static String formatTime(long ms) {
        long s = ms / 1000;
        long m = s / 60;
        s %= 60;
        long h = m / 60;
        m %= 60;
        return h > 0 ? String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, s) : String.format(Locale.ROOT, "%02d:%02d", m, s);
    }

    private boolean insideControls(View view) {
        return insideTopPanel(view) || insideBottomPanel(view);
    }

    private boolean insideTopPanel(View view) {
        while (view != null) {
            if (view == topPanel) return true;
            view = view.getParent() instanceof View ? (View) view.getParent() : null;
        }
        return false;
    }

    private boolean insideBottomPanel(View view) {
        while (view != null) {
            if (view == bottomPanel) return true;
            view = view.getParent() instanceof View ? (View) view.getParent() : null;
        }
        return false;
    }

    private void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void overlays(boolean show) {
        if (topPanel == null || bottomPanel == null) return;
        topPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        bottomPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            scheduleHide();
            if (getCurrentFocus() == null || !insideControls(getCurrentFocus())) {
                btnPlayPause.requestFocus();
            }
        } else {
            video.requestFocus();
        }
        hideBars();
    }

    private void scheduleHide() {
        progressHandler.removeCallbacks(hideControls);
        progressHandler.postDelayed(hideControls, 5000);
    }

    private void showError(String code) {
        if (isFinishing() || isDestroyed()) return;
        overlays(true);
        playerStatusBadge.setText("Hata: " + code);
        new AlertDialog.Builder(this).setTitle("Yayın Açılamadı")
            .setMessage("Kaynak yanıt vermedi (" + code + ").\nFarklı bir kaynak veya kanal deneyebilirsiniz.")
            .setPositiveButton("Kanal Listesi", (d, w) -> showChannelsDialog())
            .setNegativeButton("Tekrar Dene", (d, w) -> {
                release();
                liveRecovery.reset();
                if (title.source == Source.SPORTS) position = 0;
                shouldPlay = true;
                initialize();
                updateNextSourceButton();
            }).show();
    }

    private void release() {
        if (pendingRecovery != null) { progressHandler.removeCallbacks(pendingRecovery); pendingRecovery = null; }
        probeGeneration++;
        probing = false;
        if (pendingProbe != null) { pendingProbe.cancel(true); pendingProbe = null; }
        progressHandler.removeCallbacks(hideControls);
        progressHandler.removeCallbacks(updateProgressTask);
        if (player == null) return;
        ExoPlayer old = player;
        player = null;
        position = old.getCurrentPosition();
        shouldPlay = old.getPlayWhenReady();
        if (title.source != Source.SPORTS && position > 1000) library.progress(title, position, old.getDuration());
        video.setPlayer(null);
        old.release();
    }

    private void closePlayer() {
        closing = true;
        release();
        finish();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (player == null) return super.dispatchKeyEvent(e);
        int k = e.getKeyCode();
        if (e.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e);
        scheduleHide();

        if (k == KeyEvent.KEYCODE_CHANNEL_UP || k == KeyEvent.KEYCODE_PAGE_UP) {
            nextChannel();
            return true;
        }
        if (k == KeyEvent.KEYCODE_CHANNEL_DOWN || k == KeyEvent.KEYCODE_PAGE_DOWN) {
            prevChannel();
            return true;
        }
        if (k == KeyEvent.KEYCODE_PROG_GREEN || k == KeyEvent.KEYCODE_GUIDE) {
            showChannelsDialog();
            return true;
        }

        if (k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) { if (e.getRepeatCount() == 0) togglePlay(); return true; }
        if (k == KeyEvent.KEYCODE_MEDIA_PLAY) { player.play(); btnPlayPause.setText("❚❚ Duraklat"); return true; }
        if (k == KeyEvent.KEYCODE_MEDIA_PAUSE) { player.pause(); btnPlayPause.setText("▶ Oynat"); return true; }
        if (k == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD || k == KeyEvent.KEYCODE_MEDIA_NEXT) { seek(30000); return true; }
        if (k == KeyEvent.KEYCODE_MEDIA_REWIND || k == KeyEvent.KEYCODE_MEDIA_PREVIOUS) { seek(-30000); return true; }
        if (k == KeyEvent.KEYCODE_MENU || k == KeyEvent.KEYCODE_INFO) {
            overlays(true);
            btnQuality.requestFocus();
            return true;
        }

        boolean controlsShown = bottomPanel != null && bottomPanel.getVisibility() == View.VISIBLE;
        if (!controlsShown) {
            if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) {
                overlays(true);
                btnPlayPause.requestFocus();
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_DOWN) {
                overlays(true);
                btnPlayPause.requestFocus();
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_UP) {
                showChannelsDialog();
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT) { seek(e.getRepeatCount() > 4 ? 30000 : 10000); return true; }
            if (k == KeyEvent.KEYCODE_DPAD_LEFT) { seek(e.getRepeatCount() > 4 ? -30000 : -10000); return true; }
        } else {
            if (k == KeyEvent.KEYCODE_DPAD_UP && insideBottomPanel(getCurrentFocus())) {
                btnTopBack.requestFocus();
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_DOWN && insideTopPanel(getCurrentFocus())) {
                btnPlayPause.requestFocus();
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    @Override public void onBackPressed() {
        if (isLocked) {
            showUnlockButton();
            return;
        }
        if (bottomPanel != null && bottomPanel.getVisibility() == View.VISIBLE) overlays(false);
        else closePlayer();
    }
}
