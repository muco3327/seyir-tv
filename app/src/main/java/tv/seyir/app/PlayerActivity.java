package tv.seyir.app;

import android.app.*;
import android.os.*;
import android.view.*;
import android.webkit.CookieManager;
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
    private TextView playerTitle, playerStatusBadge, tvCurrentTime, tvTotalTime;
    private SeekBar playerProgress;
    private Button btnTopBack, btnRewind, btnPlayPause, btnForward, btnResize, btnQuality, btnAudio, btnSubtitle, btnSpeed;
    private String url;
    private final Map<String,String> headers = new HashMap<>();
    private long position;
    private boolean shouldPlay = true, closing = false, userScrubbing = false;
    private int resizeModeIndex = 0;
    private static final int[] RESIZE_MODES = {
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        AspectRatioFrameLayout.RESIZE_MODE_FILL
    };
    private static final String[] RESIZE_LABELS = {
        "📺 Boyut: Sığdır",
        "📺 Boyut: Yakınlaştır",
        "📺 Boyut: Ekrana Yay"
    };
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private int selectedQualityHeight = 0;
    private float currentSpeed = 1.0f;

    private final Runnable hideControls = () -> {
        if (player != null && player.isPlaying() && !insideControls(getCurrentFocus())) overlays(false);
    };

    private final Runnable updateProgressTask = new Runnable() {
        @Override public void run() {
            if (player != null && !closing) {
                long pos = player.getCurrentPosition();
                long dur = player.getDuration();
                if (dur > 0 && !userScrubbing) {
                    tvCurrentTime.setText(formatTime(pos));
                    tvTotalTime.setText(formatTime(dur));
                    playerProgress.setProgress((int)(pos * 1000 / dur));
                }
                updateQualityLabel();
                progressHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try {
            title = TitleItem.read(new JSONObject(getIntent().getStringExtra("item")), Source.FULLHD);
            url = getIntent().getStringExtra("url");
            if (!MediaPolicy.isVideo(url)) { finish(); return; }
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

        library = new Library(this);
        position = state == null ? library.position(title) : state.getLong("position", 0);
        setContentView(R.layout.activity_player);
        hideBars();
        bindViews();
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

        btnTopBack = findViewById(R.id.btn_top_back);
        btnRewind = findViewById(R.id.btn_rewind);
        btnPlayPause = findViewById(R.id.btn_play_pause);
        btnForward = findViewById(R.id.btn_forward);
        btnResize = findViewById(R.id.btn_resize);
        btnQuality = findViewById(R.id.btn_quality);
        btnAudio = findViewById(R.id.btn_audio);
        btnSubtitle = findViewById(R.id.btn_subtitle);
        btnSpeed = findViewById(R.id.btn_speed);

        playerTitle.setText(title != null ? title.title : "Seyir TV");

        Button[] allButtons = {btnTopBack, btnRewind, btnPlayPause, btnForward, btnResize, btnQuality, btnAudio, btnSubtitle, btnSpeed};
        for (Button b : allButtons) {
            Ui.focus(b);
        }

        btnTopBack.setOnClickListener(v -> closePlayer());
        btnRewind.setOnClickListener(v -> seek(-10000));
        btnPlayPause.setOnClickListener(v -> togglePlay());
        btnForward.setOnClickListener(v -> seek(10000));
        btnResize.setOnClickListener(v -> cycleResizeMode());
        btnQuality.setOnClickListener(v -> selectQuality());
        btnAudio.setOnClickListener(v -> selectTracks(C.TRACK_TYPE_AUDIO));
        btnSubtitle.setOnClickListener(v -> selectTracks(C.TRACK_TYPE_TEXT));
        btnSpeed.setOnClickListener(v -> selectSpeed());

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

    private void initialize() {
        if (player != null || url == null || closing || isFinishing() || video == null) return;
        try {
            String cookie = CookieManager.getInstance().getCookie(url);
            android.net.Uri origin = android.net.Uri.parse(url);
            DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers)
                .setConnectTimeoutMs(15000).setReadTimeoutMs(20000).setAllowCrossProtocolRedirects(false);
            ResolvingDataSource.Factory data = new ResolvingDataSource.Factory(http, spec -> {
                boolean same = Objects.equals(origin.getScheme(), spec.uri.getScheme()) &&
                               Objects.equals(origin.getHost(), spec.uri.getHost()) &&
                               origin.getPort() == spec.uri.getPort();
                return same && cookie != null ? spec.withAdditionalHeaders(Collections.singletonMap("Cookie", cookie)) : spec;
            });

            player = new ExoPlayer.Builder(this, new DefaultRenderersFactory(this).setEnableDecoderFallback(true))
                .setMediaSourceFactory(new DefaultMediaSourceFactory(data))
                .setSeekBackIncrementMs(10000)
                .setSeekForwardIncrementMs(10000)
                .build();

            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true);
            player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .setPreferredAudioLanguage("tr")
                .setPreferredTextLanguage("tr")
                .build());

            video.setPlayer(player);
            player.addListener(new Player.Listener() {
                @Override public void onIsPlayingChanged(boolean playing) {
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
                        playerStatusBadge.setText("Hazır");
                        library.save("history", title, false);
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
                    showError(e.getErrorCodeName());
                }
            });

            MediaItem.Builder item = new MediaItem.Builder().setUri(url);
            String path = android.net.Uri.parse(url).getPath();
            if ((path != null && path.toLowerCase(Locale.ROOT).endsWith(".m3u8")) || url.toLowerCase(Locale.ROOT).contains(".m3u8")) {
                item.setMimeType(MimeTypes.APPLICATION_M3U8);
            }
            player.setMediaItem(item.build());
            player.seekTo(position);
            player.prepare();
            player.setPlayWhenReady(shouldPlay);
            btnPlayPause.requestFocus();
            progressHandler.post(updateProgressTask);
        } catch (RuntimeException e) {
            release();
            showError(e.getClass().getSimpleName());
        }
    }

    private void updateQualityLabel() {
        if (btnQuality == null) return;
        if (selectedQualityHeight > 0) {
            btnQuality.setText("⚙️ Kalite: " + selectedQualityHeight + "p");
        } else {
            int currentH = player != null ? player.getVideoSize().height : 0;
            if (currentH > 0) btnQuality.setText("⚙️ Kalite: Otomatik (" + currentH + "p)");
            else btnQuality.setText("⚙️ Kalite: Otomatik");
        }
    }

    private void selectQuality() {
        if (player == null) return;
        List<String> labels = new ArrayList<>();
        List<Integer> heights = new ArrayList<>();
        List<TrackSelectionOverride> overrides = new ArrayList<>();

        labels.add("● Otomatik (Önerilen)");
        heights.add(0);
        overrides.add(null);

        Set<Integer> seenHeights = new HashSet<>();
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() == C.TRACK_TYPE_VIDEO) {
                for (int i = 0; i < group.length; i++) {
                    if (group.isTrackSupported(i)) {
                        Format f = group.getTrackFormat(i);
                        if (f.height > 0 && seenHeights.add(f.height)) {
                            String desc = f.height + "p" + (f.height >= 1080 ? " (FHD)" : f.height >= 720 ? " (HD)" : " (SD)");
                            labels.add(desc);
                            heights.add(f.height);
                            overrides.add(new TrackSelectionOverride(group.getMediaTrackGroup(), i));
                        }
                    }
                }
            }
        }
        if (labels.size() <= 1) {
            new AlertDialog.Builder(this).setTitle("Görüntü kalitesi")
                .setMessage("Bu yayında tek bir çözünürlük sunuluyor (Otomatik).")
                .setPositiveButton("Tamam", null).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Görüntü kalitesi seç")
            .setItems(labels.toArray(new String[0]), (d, w) -> {
                if (player == null) return;
                TrackSelectionParameters.Builder p = player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO);
                if (w == 0) {
                    selectedQualityHeight = 0;
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
            new AlertDialog.Builder(this).setTitle(type == C.TRACK_TYPE_AUDIO ? "Ses dili" : "Altyazı")
                .setMessage("Bu yayında seçilebilir alternatif parça bulunamadı.").setPositiveButton("Tamam", null).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle(type == C.TRACK_TYPE_AUDIO ? "Ses dili seç" : "Altyazı seç")
            .setItems(labels.toArray(new String[0]), (d, w) -> {
                if (player == null) return;
                TrackSelectionParameters.Builder p = player.getTrackSelectionParameters().buildUpon()
                    .clearOverridesOfType(type)
                    .setTrackTypeDisabled(type, type == C.TRACK_TYPE_TEXT && w == 0);
                if (w > 0) p.setOverrideForType(overrides.get(w));
                player.setTrackSelectionParameters(p.build());
                if (type == C.TRACK_TYPE_AUDIO) btnAudio.setText("🔊 Ses: " + labels.get(w));
                else btnSubtitle.setText("💬 Altyazı: " + labels.get(w));
                scheduleHide();
            }).show();
    }

    private void selectSpeed() {
        float[] speeds = {0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
        String[] speedLabels = {"0.75×", "1.0× (Normal)", "1.25×", "1.5×", "2.0×"};
        new AlertDialog.Builder(this).setTitle("Oynatma hızı")
            .setItems(speedLabels, (d, w) -> {
                currentSpeed = speeds[w];
                if (player != null) player.setPlaybackSpeed(currentSpeed);
                btnSpeed.setText("⚡ " + speeds[w] + "×");
                scheduleHide();
            }).show();
    }

    private void cycleResizeMode() {
        resizeModeIndex = (resizeModeIndex + 1) % RESIZE_MODES.length;
        video.setResizeMode(RESIZE_MODES[resizeModeIndex]);
        btnResize.setText(RESIZE_LABELS[resizeModeIndex]);
        scheduleHide();
    }

    private void togglePlay() {
        if (player == null) return;
        if (player.getPlayWhenReady()) player.pause();
        else player.play();
        overlays(true);
        scheduleHide();
    }

    private void seek(long delta) {
        if (player == null) return;
        long end = player.getDuration();
        long target = Math.max(0, player.getCurrentPosition() + delta);
        if (end > 0) target = Math.min(end, target);
        player.seekTo(target);
        overlays(true);
        tvCurrentTime.setText(formatTime(target));
        scheduleHide();
    }

    private static String formatTime(long ms) {
        if (ms <= 0) return "00:00";
        long totalSec = ms / 1000;
        long sec = totalSec % 60;
        long min = (totalSec / 60) % 60;
        long hour = totalSec / 3600;
        if (hour > 0) return String.format(Locale.ROOT, "%d:%02d:%02d", hour, min, sec);
        return String.format(Locale.ROOT, "%02d:%02d", min, sec);
    }

    private boolean insideControls(View view) {
        while (view != null) {
            if (view == topPanel || view == bottomPanel) return true;
            view = view.getParent() instanceof View ? (View) view.getParent() : null;
        }
        return false;
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
        getWindow().getDecorView().setSystemUiVisibility(5894);
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
        new AlertDialog.Builder(this).setTitle("Video açılamadı")
            .setMessage("Hata kodu: " + code + "\nFarklı bir oynatma kaynağı deneyebilirsiniz.")
            .setPositiveButton("Kaynağa dön", (d, w) -> closePlayer())
            .setNegativeButton("Tekrar dene", (d, w) -> {
                if (player == null) initialize();
                else { player.prepare(); player.play(); }
            }).show();
    }

    private void release() {
        progressHandler.removeCallbacks(hideControls);
        progressHandler.removeCallbacks(updateProgressTask);
        if (player == null) return;
        ExoPlayer old = player;
        player = null;
        position = old.getCurrentPosition();
        shouldPlay = old.getPlayWhenReady();
        if (position > 1000) library.progress(title, position, old.getDuration());
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
                overlays(true);
                btnTopBack.requestFocus();
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
        if (bottomPanel != null && bottomPanel.getVisibility() == View.VISIBLE) overlays(false);
        else closePlayer();
    }

    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) hideBars();
    }

    @Override protected void onStart() { super.onStart(); initialize(); }
    @Override protected void onStop() { release(); super.onStop(); }
    @Override protected void onDestroy() { release(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putLong("position", player == null ? position : player.getCurrentPosition());
        super.onSaveInstanceState(state);
    }
}
