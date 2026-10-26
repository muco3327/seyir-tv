package tv.seyir.app;

import android.app.*;
import android.content.Context;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.datasource.*;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import androidx.media3.ui.AspectRatioFrameLayout;
import org.json.JSONObject;
import java.util.*;

@androidx.media3.common.util.UnstableApi
public final class PlayerActivity extends Activity {
    private ExoPlayer player;
    private PlayerView video;
    private TitleItem title;
    private Library library;

    // Layout Containers
    private View playerControlsOverlay;
    private LinearLayout topPanel, bottomPanel;
    private View playerBufferingLayout;
    private TextView tvBufferingStatus;
    private LinearLayout channelSidebar;

    // Badges & Info
    private TextView playerTitle, playerEpgTitle, playerStatusBadge, badgeLive, badgeHd, tvPlayerClock;
    private TextView tvCurrentTime, tvTotalTime, tvGestureHud;
    private SeekBar playerProgress;

    // Control Buttons
    private Button btnTopBack, btnTopChannels, btnTopClose;
    private Button btnChannels, btnPrevChannel, btnRewind, btnPlayPause, btnForward, btnNextChannel;
    private Button btnQuality, btnResize, btnNextSource, btnLock, btnFloatingUnlock;
    private Button btnAudio, btnSubtitle, btnSpeed;

    // Sidebar Views
    private TextView tvSidebarTitle, tvSidebarCount;
    private Button btnCloseSidebar, btnFilterAll, btnFilterSports, btnFilterNational;
    private ListView lvSidebarChannels;
    private ChannelAdapter sidebarAdapter;
    private final List<SportsManager.SportChannel> allChannels = new ArrayList<>();
    private final List<SportsManager.SportChannel> filteredChannels = new ArrayList<>();
    private String currentFilter = "all";

    // Playback state
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

    // Resize modes
    private int resizeModeIndex = 0;
    private static final int[] RESIZE_MODES = {
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
    };
    private static final String[] RESIZE_LABELS = {
        "Sığdır",
        "Ekrana Yay",
        "Yakınlaştır",
        "16:9 Sabit"
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
        if (player != null && player.isPlaying() && (channelSidebar == null || channelSidebar.getVisibility() != View.VISIBLE)) {
            overlays(false);
        }
    };

    private final Runnable clockRunnable = new Runnable() {
        @Override public void run() {
            if (!closing && tvPlayerClock != null) {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", Locale.getDefault());
                tvPlayerClock.setText(sdf.format(new Date()));
                progressHandler.postDelayed(this, 30000);
            }
        }
    };

    private final Runnable updateProgressTask = new Runnable() {
        @Override public void run() {
            if (player != null && !closing) {
                liveRecovery.playing(player.isPlaying(), SystemClock.elapsedRealtime());
                boolean isLive = player.isCurrentMediaItemLive() || player.getDuration() <= 0;
                if (isLive) {
                    tvCurrentTime.setText("CANLI");
                    tvTotalTime.setText("🔴 CANLI");
                    playerProgress.setProgress(1000);
                } else {
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
        setupSidebar();
        setupGestures();
        progressHandler.post(clockRunnable);
    }

    private void bindViews() {
        video = findViewById(R.id.player_view);
        playerControlsOverlay = findViewById(R.id.player_controls_overlay);
        topPanel = findViewById(R.id.player_top_panel);
        bottomPanel = findViewById(R.id.player_bottom_panel);
        playerBufferingLayout = findViewById(R.id.player_buffering_layout);
        tvBufferingStatus = findViewById(R.id.tv_buffering_status);
        channelSidebar = findViewById(R.id.channel_sidebar);

        playerTitle = findViewById(R.id.player_title);
        playerEpgTitle = findViewById(R.id.player_epg_title);
        playerStatusBadge = findViewById(R.id.player_status_badge);
        badgeLive = findViewById(R.id.badge_live);
        badgeHd = findViewById(R.id.badge_hd);
        tvPlayerClock = findViewById(R.id.tv_player_clock);

        tvCurrentTime = findViewById(R.id.tv_current_time);
        tvTotalTime = findViewById(R.id.tv_total_time);
        playerProgress = findViewById(R.id.player_progress);
        tvGestureHud = findViewById(R.id.tv_gesture_hud);
        btnFloatingUnlock = findViewById(R.id.btn_floating_unlock);

        btnTopBack = findViewById(R.id.btn_top_back);
        btnTopChannels = findViewById(R.id.btn_top_channels);
        btnTopClose = findViewById(R.id.btn_top_close);

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

        // Sidebar Views
        tvSidebarTitle = findViewById(R.id.tv_sidebar_title);
        tvSidebarCount = findViewById(R.id.tv_sidebar_count);
        btnCloseSidebar = findViewById(R.id.btn_close_sidebar);
        btnFilterAll = findViewById(R.id.btn_filter_all);
        btnFilterSports = findViewById(R.id.btn_filter_sports);
        btnFilterNational = findViewById(R.id.btn_filter_national);
        lvSidebarChannels = findViewById(R.id.lv_sidebar_channels);

        playerTitle.setText(title != null ? title.title : "Seyir TV");
        if (title != null) {
            updateEpgInfo(title.title);
            SportsManager.SportChannel ch = SportsManager.getChannel(title.title);
            if (ch == null && url != null) ch = SportsManager.getChannel(url);
            if (ch != null && ch.urls != null) {
                for (String u : ch.urls) {
                    if (u != null && !u.isEmpty() && !u.equals(url) && !fallbackList.contains(u)) {
                        fallbackList.add(u);
                    }
                }
            }
        }

        Button[] modernButtons = {btnTopChannels, btnChannels, btnPrevChannel, btnRewind,
                btnPlayPause, btnNextSource, btnForward, btnNextChannel, btnResize, btnQuality,
                btnAudio, btnSubtitle, btnSpeed, btnFilterAll, btnFilterSports, btnFilterNational};
        for (Button b : modernButtons) {
            if (b != null) styleYouTubeButton(b);
        }
        Button[] roundButtons = {btnTopBack, btnTopClose, btnCloseSidebar};
        for (Button b : roundButtons) {
            if (b != null) styleYouTubeRoundButton(b);
        }

        btnTopBack.setOnClickListener(v -> closePlayer());
        btnTopClose.setOnClickListener(v -> closePlayer());
        btnTopChannels.setOnClickListener(v -> showChannelSidebar());
        btnChannels.setOnClickListener(v -> showChannelSidebar());

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

        if (btnLock != null) btnLock.setVisibility(View.GONE);
        if (btnFloatingUnlock != null) btnFloatingUnlock.setVisibility(View.GONE);

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

    private int dp(float n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private void styleYouTubeButton(Button b) {
        if (b == null) return;
        b.setAllCaps(false);
        b.setFocusable(true);
        b.setFocusableInTouchMode(false);

        int radius = dp(20);
        android.graphics.drawable.GradientDrawable normalBg = new android.graphics.drawable.GradientDrawable();
        normalBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        normalBg.setCornerRadius(radius);
        normalBg.setColor(Color.argb(45, 255, 255, 255));

        android.graphics.drawable.GradientDrawable focusedBg = new android.graphics.drawable.GradientDrawable();
        focusedBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        focusedBg.setCornerRadius(radius);
        focusedBg.setColor(Color.WHITE);

        b.setBackground(normalBg);
        b.setTextColor(Color.WHITE);

        b.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                b.setBackground(focusedBg);
                b.setTextColor(Color.BLACK);
                b.animate().scaleX(1.06f).scaleY(1.06f).setDuration(120).start();
                b.setElevation(dp(8));
                scheduleHide();
            } else {
                b.setBackground(normalBg);
                b.setTextColor(Color.WHITE);
                b.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
                b.setElevation(0);
            }
        });
    }

    private void styleYouTubeRoundButton(Button b) {
        if (b == null) return;
        b.setAllCaps(false);
        b.setFocusable(true);
        b.setFocusableInTouchMode(false);

        android.graphics.drawable.GradientDrawable normalBg = new android.graphics.drawable.GradientDrawable();
        normalBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        normalBg.setColor(Color.argb(45, 255, 255, 255));

        android.graphics.drawable.GradientDrawable focusedBg = new android.graphics.drawable.GradientDrawable();
        focusedBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        focusedBg.setColor(Color.WHITE);

        b.setBackground(normalBg);
        b.setTextColor(Color.WHITE);

        b.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                b.setBackground(focusedBg);
                b.setTextColor(Color.BLACK);
                b.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).start();
                b.setElevation(dp(8));
                scheduleHide();
            } else {
                b.setBackground(normalBg);
                b.setTextColor(Color.WHITE);
                b.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
                b.setElevation(0);
            }
        });
    }

    private final Runnable bufferingTimeoutTask = new Runnable() {
        @Override public void run() {
            if (player != null && player.getPlaybackState() == Player.STATE_BUFFERING && !closing && !isFinishing()) {
                if (!fallbackList.isEmpty()) {
                    showHud("Donma algılandı, yedek kaynağa geçiliyor…");
                    tryNextSource("Akış Zaman Aşımı");
                }
            }
        }
    };

    private void startBufferingTimeout() {
        progressHandler.removeCallbacks(bufferingTimeoutTask);
        progressHandler.postDelayed(bufferingTimeoutTask, 4500);
    }

    private void cancelBufferingTimeout() {
        progressHandler.removeCallbacks(bufferingTimeoutTask);
    }

    private void updateEpgInfo(String chName) {
        if (playerEpgTitle == null || chName == null) return;
        playerEpgTitle.setText(getLiveProgramInfo(chName));
    }

    private String getLiveProgramInfo(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);

        if (n.contains("bein") && n.contains("1")) {
            if (hour >= 18 && hour < 23) return "Trendyol Süper Lig Canlı Karşılaşma";
            if (hour >= 23 || hour < 2) return "Gün Sonu & Süper Lig Maç Özetleri";
            return "Süper Lig Maç Önü & Özel Dosyalar";
        }
        if (n.contains("bein") && n.contains("2")) {
            if (hour >= 19 && hour < 23) return "Trendyol Süper Lig & Ligue 1 Canlı";
            return "Ligue 1 & Süper Lig Karşılaşmaları";
        }
        if (n.contains("bein") && n.contains("3")) {
            if (hour >= 17 && hour < 22) return "Trendyol 1. Lig Canlı Maç Yayını";
            return "1. Lig & Basketbol Süper Ligi";
        }
        if (n.contains("bein") && n.contains("4")) return "EuroLeague Basketbol & WTA Tenis";
        if (n.contains("bein") && n.contains("5")) return "Sultanlar Voleybol Ligi & Motor Sporları";
        if (n.contains("bein") && n.contains("max")) return "Trendyol 1. Lig Canlı Karşılaşma";
        if (n.contains("bein") && n.contains("haber")) return "beIN Manşet & Günün Spor Haberleri";
        if (n.contains("s sport")) {
            if (hour >= 17 && hour < 23) return "İspanya LaLiga & Serie A Canlı";
            return "LaLiga Özetler & NBA Action";
        }
        if (n.contains("exxen")) return "UEFA Şampiyonlar Ligi & Avrupa Ligi";
        if (n.contains("tivibu")) return "LaLiga, Serie A & İtalya Kupası";
        if (n.contains("smart spor")) return "Sultanlar Voleybol Ligi & Canlı Maç";
        if (n.contains("tabii")) return "UEFA Avrupa Ligi & Konferans Ligi";
        if (n.contains("trt spor")) {
            if (hour >= 18 && hour < 23) return "Canlı Maç Kuşağı & Aktüel Futbol";
            return "Spor Bülteni & Gündem Spor";
        }
        if (n.contains("a spor")) {
            if (hour >= 21) return "Son Sayfa Canlı & Transfer Raporu";
            return "Günün Spor Başlıkları & Sabah Sporu";
        }
        if (n.contains("fb tv")) return "Fenerbahçe Günlüğü & Maç Önü";
        if (n.contains("gs tv")) return "Galatasaray Günlüğü & Maç Raporu";
        if (n.contains("trt 1")) {
            if (hour >= 19 && hour < 20) return "TRT 1 Ana Haber";
            if (hour >= 20 && hour < 23) return "Prime Time Dizi / Canlı Karşılaşma";
            return "TRT 1 Gün Kuşağı";
        }
        if (n.contains("atv")) {
            if (hour >= 19 && hour < 20) return "ATV Ana Haber Bülteni";
            if (hour >= 20 && hour < 23) return "Kuruluş Osman / Prime Time Kuşağı";
            return "Müge Anlı ile Tatlı Sert / Gün Ortası";
        }
        if (n.contains("kanal d")) {
            if (hour >= 19 && hour < 20) return "Kanal D Ana Haber";
            if (hour >= 20 && hour < 23) return "Arka Sokaklar / Dizi Kuşağı";
            return "Gelinim Mutfakta / Kanal D Kuşağı";
        }
        if (n.contains("show")) {
            if (hour >= 19 && hour < 20) return "Show TV Ana Haber";
            if (hour >= 20 && hour < 23) return "Kızılcık Şerbeti / Dizi Kuşağı";
            return "Show TV Gün Kuşağı";
        }
        if (n.contains("star")) {
            if (hour >= 19 && hour < 20) return "Star Ana Haber";
            if (hour >= 20 && hour < 23) return "Yalı Çapkını / Prime Time Kuşağı";
            return "Star TV Gün Kuşağı";
        }
        if (n.contains("tv8")) {
            if (hour >= 20) return "MasterChef Türkiye / Survivor Canlı";
            return "Zuhal Topal'la Yemekteyiz";
        }
        if (n.contains("now")) {
            if (hour >= 19 && hour < 20) return "Selçuk Tepeli ile NOW Ana Haber";
            if (hour >= 20 && hour < 23) return "Kızıl Goncalar / Prime Time Kuşağı";
            return "Çağla ile Yeni Bir Gün";
        }
        if (n.contains("haber")) {
            if (hour >= 20 && hour < 23) return "Gece Raporu & Canlı Tartışma Programı";
            return "Son Dakika & Canlı Gelişmeler";
        }
        if (n.contains("belgesel") || n.contains("dmax") || n.contains("tlc")) return "Yaban Hayatı & Doğa / Macera Belgeseli";
        if (n.contains("sinema") || n.contains("film")) return "Sinema Kuşağı (Türkçe Dublaj / Altyazı)";
        if (n.contains("cocuk")) return "Çizgi Dizi Kuşağı";
        if (n.contains("muzik")) return "Hit Müzik & En İyiler Klip Kuşağı";
        return "Canlı Yayın Akışı";
    }

    private void setupSidebar() {
        sidebarAdapter = new ChannelAdapter();
        lvSidebarChannels.setAdapter(sidebarAdapter);
        lvSidebarChannels.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        lvSidebarChannels.setItemsCanFocus(false);

        lvSidebarChannels.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < filteredChannels.size()) {
                switchChannel(filteredChannels.get(position));
                hideChannelSidebar();
            }
        });

        btnCloseSidebar.setOnClickListener(v -> hideChannelSidebar());
        btnFilterAll.setOnClickListener(v -> applyFilter("all"));
        btnFilterSports.setOnClickListener(v -> applyFilter("sports"));
        btnFilterNational.setOnClickListener(v -> applyFilter("national"));

        refreshChannelList();
    }

    private void refreshChannelList() {
        allChannels.clear();
        List<SportsManager.SportChannel> cached = SportsManager.getCachedChannels();
        allChannels.addAll(cached);
        applyFilter(currentFilter);
    }

    private void applyFilter(String filter) {
        currentFilter = filter;
        btnFilterAll.setTextColor(filter.equals("all") ? 0xFFFFFFFF : 0xFF94A3B8);
        btnFilterSports.setTextColor(filter.equals("sports") ? 0xFFFFFFFF : 0xFF94A3B8);
        btnFilterNational.setTextColor(filter.equals("national") ? 0xFFFFFFFF : 0xFF94A3B8);

        filteredChannels.clear();
        for (SportsManager.SportChannel ch : allChannels) {
            if (filter.equals("all")) {
                filteredChannels.add(ch);
            } else if (filter.equals("sports")) {
                if (ch.category != null && ch.category.toLowerCase(Locale.ROOT).contains("spor")) {
                    filteredChannels.add(ch);
                }
            } else if (filter.equals("national")) {
                if (ch.category != null && !ch.category.toLowerCase(Locale.ROOT).contains("spor")) {
                    filteredChannels.add(ch);
                }
            }
        }
        if (filter.equals("sports")) {
            java.text.Collator trCollator = java.text.Collator.getInstance(new java.util.Locale("tr"));
            filteredChannels.sort((c1, c2) -> trCollator.compare(c1.name, c2.name));
        }
        if (sidebarAdapter != null) sidebarAdapter.notifyDataSetChanged();
        if (tvSidebarCount != null) tvSidebarCount.setText(filteredChannels.size() + " Kanal • Kumanda ile Seçin");
    }

    public void showChannelSidebar() {
        if (channelSidebar == null) return;
        refreshChannelList();
        channelSidebar.setVisibility(View.VISIBLE);
        channelSidebar.bringToFront();
        progressHandler.removeCallbacks(hideControls);

        int currentIdx = 0;
        for (int i = 0; i < filteredChannels.size(); i++) {
            if (title != null && filteredChannels.get(i).name.equalsIgnoreCase(title.title)) {
                currentIdx = i;
                break;
            }
        }
        final int targetIdx = currentIdx;
        lvSidebarChannels.post(() -> {
            lvSidebarChannels.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            lvSidebarChannels.setItemChecked(targetIdx, true);
            lvSidebarChannels.setSelection(targetIdx);
            lvSidebarChannels.requestFocus();
            lvSidebarChannels.requestFocusFromTouch();
        });
    }

    public void hideChannelSidebar() {
        if (channelSidebar != null && channelSidebar.getVisibility() == View.VISIBLE) {
            channelSidebar.setVisibility(View.GONE);
            scheduleHide();
            if (video != null) video.requestFocus();
        }
    }

    private class ChannelAdapter extends BaseAdapter {
        @Override public int getCount() { return filteredChannels.size(); }
        @Override public Object getItem(int pos) { return filteredChannels.get(pos); }
        @Override public long getItemId(int pos) { return pos; }
        @Override public View getView(int pos, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_player_channel, parent, false);
            }
            SportsManager.SportChannel ch = filteredChannels.get(pos);
            TextView tvNum = convertView.findViewById(R.id.tv_item_channel_num);
            TextView tvName = convertView.findViewById(R.id.tv_item_channel_name);
            TextView tvCat = convertView.findViewById(R.id.tv_item_channel_cat);
            TextView tvPlaying = convertView.findViewById(R.id.tv_item_channel_playing);

            tvNum.setText(String.valueOf(pos + 1));
            tvName.setText(ch.name);
            tvCat.setText(ch.category != null ? ch.category : "Genel");

            boolean isPlaying = title != null && (ch.name.equalsIgnoreCase(title.title) || (ch.urls != null && ch.urls.contains(url)));
            if (isPlaying) {
                tvPlaying.setVisibility(View.VISIBLE);
                tvName.setTextColor(0xFF38BDF8);
            } else {
                tvPlaying.setVisibility(View.GONE);
                tvName.setTextColor(0xFFFFFFFF);
            }
            return convertView;
        }
    }

    private void setupGestures() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (isLocked) {
                    showUnlockButton();
                    return true;
                }
                if (channelSidebar != null && channelSidebar.getVisibility() == View.VISIBLE) {
                    return true;
                }
                boolean showing = playerControlsOverlay != null && playerControlsOverlay.getVisibility() == View.VISIBLE;
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
        hudHandler.postDelayed(hideHudRunnable, 1400);
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
            hideChannelSidebar();
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

        if (channelSidebar != null && channelSidebar.getVisibility() == View.VISIBLE) {
            float x = event.getX();
            float y = event.getY();
            int[] loc = new int[2];
            channelSidebar.getLocationOnScreen(loc);
            boolean inside = (x >= loc[0] && x <= (loc[0] + channelSidebar.getWidth())
                           && y >= loc[1] && y <= (loc[1] + channelSidebar.getHeight()));
            if (inside) {
                return super.dispatchTouchEvent(event);
            } else {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    hideChannelSidebar();
                }
                return true;
            }
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

    private void prevChannel() {
        List<SportsManager.SportChannel> channels = allChannels.isEmpty() ? SportsManager.getCachedChannels() : allChannels;
        if (channels.isEmpty() || title == null) return;
        int currentIdx = -1;
        for (int i = 0; i < channels.size(); i++) {
            if (channels.get(i).name.equalsIgnoreCase(title.title)) { currentIdx = i; break; }
        }
        int prevIdx = currentIdx <= 0 ? channels.size() - 1 : currentIdx - 1;
        switchChannel(channels.get(prevIdx));
    }

    private void nextChannel() {
        List<SportsManager.SportChannel> channels = allChannels.isEmpty() ? SportsManager.getCachedChannels() : allChannels;
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
        title = new TitleItem(Source.SPORTS, ch.name, ch.urls.get(0), ch.logo, ch.category != null ? ch.category : "");
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
        fallbackList.sort((u1, u2) -> Integer.compare(SportsManager.getUrlQualityScore(u2), SportsManager.getUrlQualityScore(u1)));
        updateNextSourceButton();
        playerStatusBadge.setText("Kanal açılıyor…");
        updateEpgInfo(ch.name);
        showHud(ch.name + "\n" + getLiveProgramInfo(ch.name));
        if (sidebarAdapter != null) sidebarAdapter.notifyDataSetChanged();
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
        if (playerBufferingLayout != null) {
            playerBufferingLayout.setVisibility(View.VISIBLE);
            tvBufferingStatus.setText("Yayın bağlanıyor…");
        }
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
            StreamRequest effective = request;
            try {
                String resolved = SportsManager.resolveNestedStreamUrl(request.url, request.headers);
                if (resolved != null && !resolved.equals(request.url)) {
                    effective = new StreamRequest(resolved, request.headers);
                }
                probe = StreamProbe.inspect(effective, effective.headers);
            } catch (Exception ignored) {
            }
            final StreamProbe.Result p = probe;
            final StreamRequest playRequest = effective;
            runOnUiThread(() -> {
                if (closing || isFinishing() || isDestroyed() || gen != probeGeneration) return;
                probing = false;
                probedUrl = p != null && p.resolvedUrl != null ? p.resolvedUrl : playRequest.url;
                probedMime = p != null ? p.mime : null;
                initPlayer(playRequest);
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

            DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter.Builder(this)
                .setInitialBitrateEstimate(15_000_000)
                .build();

            player = new ExoPlayer.Builder(this, new DefaultRenderersFactory(this).setEnableDecoderFallback(true))
                .setBandwidthMeter(bandwidthMeter)
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
                    btnPlayPause.setText(playing ? "Duraklat" : "Oynat");
                    if (playing) {
                        if (playerBufferingLayout != null) playerBufferingLayout.setVisibility(View.GONE);
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
                        cancelBufferingTimeout();
                        if (playerBufferingLayout != null) playerBufferingLayout.setVisibility(View.GONE);
                        boolean isLive = player.isCurrentMediaItemLive() || player.getDuration() <= 0;
                        if (badgeLive != null) badgeLive.setVisibility(isLive ? View.VISIBLE : View.GONE);
                        playerStatusBadge.setText(isLive ? "Oracle Gateway" : "Hazır");
                        if (!isLive) library.save("history", title, false);
                        updateQualityLabel();
                        scheduleHide();
                    } else if (s == Player.STATE_BUFFERING) {
                        if (playerBufferingLayout != null) {
                            playerBufferingLayout.setVisibility(View.VISIBLE);
                            tvBufferingStatus.setText("Yayın yükleniyor…");
                        }
                        playerStatusBadge.setText("Yükleniyor…");
                        startBufferingTimeout();
                    } else if (s == Player.STATE_ENDED) {
                        if (playerBufferingLayout != null) playerBufferingLayout.setVisibility(View.GONE);
                        library.progress(title, player.getDuration(), player.getDuration());
                        playerStatusBadge.setText("Tamamlandı");
                        overlays(true);
                    }
                }
                @Override public void onVideoSizeChanged(VideoSize videoSize) {
                    updateQualityLabel();
                }
                @Override public void onTracksChanged(Tracks tracks) {
                    updateQualityLabel();
                    if (selectedQualityHeight == 0 && player != null) {
                        TrackSelectionOverride bestOverride = null;
                        int maxScore = -1;
                        for (Tracks.Group group : tracks.getGroups()) {
                            if (group.getType() == C.TRACK_TYPE_VIDEO) {
                                for (int i = 0; i < group.length; i++) {
                                    if (group.isTrackSupported(i)) {
                                        Format f = group.getTrackFormat(i);
                                        int score = (f.height * 10000) + (f.bitrate > 0 ? f.bitrate / 1000 : 0);
                                        if (score > maxScore) {
                                            maxScore = score;
                                            bestOverride = new TrackSelectionOverride(group.getMediaTrackGroup(), i);
                                        }
                                    }
                                }
                            }
                        }
                        if (bestOverride != null) {
                            player.setTrackSelectionParameters(
                                player.getTrackSelectionParameters().buildUpon()
                                    .setOverrideForType(bestOverride)
                                    .setForceHighestSupportedBitrate(true)
                                    .build()
                            );
                        }
                    }
                }
                @Override public void onPlayerError(PlaybackException e) {
                    if (playerBufferingLayout != null) playerBufferingLayout.setVisibility(View.GONE);
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
        cancelBufferingTimeout();
        if (!fallbackList.isEmpty()) {
            showHud("Yedek kaynağa geçiliyor…");
            tryNextSource(error.getErrorCodeName());
            return;
        }
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
        int currentH = player != null && player.getVideoSize().height > 0 ? player.getVideoSize().height : selectedQualityHeight;
        String qText = currentH >= 2160 ? "4K UHD" : currentH >= 1080 ? "1080p FHD" : currentH >= 720 ? "720p HD" : currentH > 0 ? currentH + "p" : "1080p FHD";
        if (badgeHd != null) badgeHd.setText(qText);
        if (btnQuality != null) btnQuality.setText(qText);
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
                btnSpeed.setText(speeds[w] + "x");
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
                showHud("Yedek Kaynak");
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
        while (view != null) {
            if (view == topPanel || view == bottomPanel || view == channelSidebar) return true;
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
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void overlays(boolean show) {
        if (playerControlsOverlay == null) return;
        if (channelSidebar != null && channelSidebar.getVisibility() == View.VISIBLE) {
            return;
        }
        playerControlsOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            scheduleHide();
            if (getCurrentFocus() == null || !insideControls(getCurrentFocus())) {
                if (btnPlayPause != null) btnPlayPause.requestFocus();
            }
        } else {
            if (video != null) video.requestFocus();
        }
        hideBars();
    }

    private void scheduleHide() {
        progressHandler.removeCallbacks(hideControls);
        if (player != null && player.isPlaying() && (channelSidebar == null || channelSidebar.getVisibility() != View.VISIBLE)) {
            progressHandler.postDelayed(hideControls, 4000);
        }
    }

    private void showError(String code) {
        if (isFinishing() || isDestroyed()) return;
        overlays(true);
        playerStatusBadge.setText("Hata: " + code);
        new AlertDialog.Builder(this).setTitle("Yayın Açılamadı")
            .setMessage("Kaynak yanıt vermedi (" + code + ").\nFarklı bir kaynak veya kanal deneyebilirsiniz.")
            .setPositiveButton("Kanal Listesi", (d, w) -> showChannelSidebar())
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
        if (e.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e);
        int k = e.getKeyCode();

        // 1. Remote BACK key: If sidebar open -> close sidebar. Else -> DIRECT EXIT!
        if (k == KeyEvent.KEYCODE_BACK) {
            if (channelSidebar != null && channelSidebar.getVisibility() == View.VISIBLE) {
                hideChannelSidebar();
                return true;
            }
            closePlayer();
            return true;
        }

        if (player == null) return super.dispatchKeyEvent(e);
        scheduleHide();

        // 2. Channel Zapping Keys
        if (k == KeyEvent.KEYCODE_CHANNEL_UP || k == KeyEvent.KEYCODE_PAGE_UP) {
            nextChannel();
            return true;
        }
        if (k == KeyEvent.KEYCODE_CHANNEL_DOWN || k == KeyEvent.KEYCODE_PAGE_DOWN) {
            prevChannel();
            return true;
        }
        if (k == KeyEvent.KEYCODE_PROG_GREEN || k == KeyEvent.KEYCODE_GUIDE) {
            showChannelSidebar();
            return true;
        }

        // 3. Media playback keys
        if (k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) { if (e.getRepeatCount() == 0) togglePlay(); return true; }
        if (k == KeyEvent.KEYCODE_MEDIA_PLAY) { player.play(); btnPlayPause.setText("Duraklat"); return true; }
        if (k == KeyEvent.KEYCODE_MEDIA_PAUSE) { player.pause(); btnPlayPause.setText("Oynat"); return true; }
        if (k == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD || k == KeyEvent.KEYCODE_MEDIA_NEXT) { seek(30000); return true; }
        if (k == KeyEvent.KEYCODE_MEDIA_REWIND || k == KeyEvent.KEYCODE_MEDIA_PREVIOUS) { seek(-30000); return true; }
        if (k == KeyEvent.KEYCODE_MENU || k == KeyEvent.KEYCODE_INFO) {
            overlays(true);
            btnQuality.requestFocus();
            return true;
        }

        // 4. D-Pad Navigation
        boolean sidebarShown = channelSidebar != null && channelSidebar.getVisibility() == View.VISIBLE;
        boolean controlsShown = playerControlsOverlay != null && playerControlsOverlay.getVisibility() == View.VISIBLE;

        if (sidebarShown) {
            View cur = getCurrentFocus();

            if (k == KeyEvent.KEYCODE_DPAD_UP) {
                if (cur == btnFilterAll || cur == btnFilterSports || cur == btnFilterNational || cur == btnCloseSidebar) {
                    return true;
                }
                int pos = lvSidebarChannels.getSelectedItemPosition();
                if (pos > 0) {
                    lvSidebarChannels.setSelection(pos - 1);
                    lvSidebarChannels.setItemChecked(pos - 1, true);
                    return true;
                } else {
                    if ("sports".equals(currentFilter)) btnFilterSports.requestFocus();
                    else if ("national".equals(currentFilter)) btnFilterNational.requestFocus();
                    else btnFilterAll.requestFocus();
                    return true;
                }
            } else if (k == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (cur == btnFilterAll || cur == btnFilterSports || cur == btnFilterNational || cur == btnCloseSidebar) {
                    lvSidebarChannels.requestFocus();
                    lvSidebarChannels.setSelection(0);
                    lvSidebarChannels.setItemChecked(0, true);
                    return true;
                }
                int pos = lvSidebarChannels.getSelectedItemPosition();
                if (pos < filteredChannels.size() - 1) {
                    int next = pos < 0 ? 0 : pos + 1;
                    lvSidebarChannels.setSelection(next);
                    lvSidebarChannels.setItemChecked(next, true);
                    return true;
                }
                return true;
            } else if (k == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (cur == btnCloseSidebar) {
                    btnFilterNational.requestFocus();
                    return true;
                } else if (cur == btnFilterNational) {
                    btnFilterSports.requestFocus();
                    return true;
                } else if (cur == btnFilterSports) {
                    btnFilterAll.requestFocus();
                    return true;
                }
                return true;
            } else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (cur == btnFilterAll) {
                    btnFilterSports.requestFocus();
                    return true;
                } else if (cur == btnFilterSports) {
                    btnFilterNational.requestFocus();
                    return true;
                } else if (cur == btnFilterNational) {
                    btnCloseSidebar.requestFocus();
                    return true;
                } else if (cur == btnCloseSidebar) {
                    hideChannelSidebar();
                    return true;
                } else {
                    if (e.getRepeatCount() >= 2) {
                        hideChannelSidebar();
                        return true;
                    }
                    return true;
                }
            } else if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                if (cur == btnCloseSidebar) {
                    hideChannelSidebar();
                    return true;
                }
                if (cur == btnFilterAll) { applyFilter("all"); return true; }
                if (cur == btnFilterSports) { applyFilter("sports"); return true; }
                if (cur == btnFilterNational) { applyFilter("national"); return true; }

                int pos = lvSidebarChannels.getSelectedItemPosition();
                if (pos >= 0 && pos < filteredChannels.size()) {
                    switchChannel(filteredChannels.get(pos));
                    hideChannelSidebar();
                    return true;
                }
            }
            return super.dispatchKeyEvent(e);
        }

        if (!controlsShown) {
            if (k == KeyEvent.KEYCODE_DPAD_UP) {
                prevChannel();
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_DOWN) {
                nextChannel();
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_LEFT) {
                showChannelSidebar();
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                seek(10000);
                return true;
            }
            if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                overlays(true);
                btnPlayPause.requestFocus();
                return true;
            }
        } else {
            scheduleHide();
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
        if (channelSidebar != null && channelSidebar.getVisibility() == View.VISIBLE) {
            hideChannelSidebar();
            return;
        }
        boolean controlsShown = playerControlsOverlay != null && playerControlsOverlay.getVisibility() == View.VISIBLE;
        if (controlsShown) {
            overlays(false);
            return;
        }
        closePlayer();
    }
}
