package tv.seyir.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native channel browser. The existing player and source pipeline remain unchanged. */
public class TvHomeActivity extends Activity {
    private Library library;
    private SharedPreferences prefs;
    private ScrollView scroll;
    private LinearLayout shell, content;
    private TextView status;
    private String mode = "favorites", category = "", query = "", expanded = "", focusKey = "";
    private int savedScroll, loadGeneration, imageGeneration;
    private boolean loading, returning;
    private List<SportsManager.SportChannel> channels = new ArrayList<>();
    private final Map<String, View> focusViews = new LinkedHashMap<>();
    private final ExecutorService images = Executors.newFixedThreadPool(3);
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap b) { return b.getByteCount(); }
    };
    private static class Group {
        final String key, name;
        final List<SportsManager.SportChannel> variants = new ArrayList<>();
        Group(String name) { this.key = ChannelPresentation.key(name); this.name = ChannelPresentation.name(name); }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(5894);
        library = new Library(this); prefs = getSharedPreferences("tv_home", MODE_PRIVATE);
        category = prefs.getString("category", "");
        if (state != null) {
            mode = state.getString("mode", "favorites"); category = state.getString("category", category);
            query = state.getString("query", ""); expanded = state.getString("expanded", "");
            focusKey = state.getString("focus", ""); savedScroll = state.getInt("scroll");
        }
        scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        scroll.setBackgroundColor(HomeStyle.BG); scroll.setVerticalScrollBarEnabled(false);
        shell = HomeStyle.column(this); shell.setPadding(dp(30), dp(16), dp(30), dp(24));
        shell.setClipChildren(false); shell.setClipToPadding(false);
        scroll.addView(shell, new ScrollView.LayoutParams(-1, -2)); setContentView(scroll);
        load(false);
        AppUpdater.check(this, false);
    }

    private int dp(float n) { return HomeStyle.dp(this, n); }
    private boolean alive() { return !isFinishing() && !isDestroyed(); }
    private void load(boolean refresh) {
        loading = true; int generation = ++loadGeneration;
        render(false);
        SportsManager.loadChannels(this, refresh, result -> {
            if (!alive() || generation != loadGeneration) return;
            channels = new ArrayList<>(result); loading = false;
            render(true);
        }, message -> {
            if (alive() && loading && generation == loadGeneration && status != null) status.setText(message);
        });
    }

    private Set<String> favoriteKeys() {
        Set<String> result = new LinkedHashSet<>();
        for (TitleItem item : library.list("favorites")) result.add(ChannelPresentation.key(item.title));
        return result;
    }

    private List<Group> visibleGroups() {
        Map<String, Group> groups = new LinkedHashMap<>(); Set<String> favorites = favoriteKeys();
        String search = ChannelFilter.normalized(query).replaceAll("[^a-z0-9]", "");
        for (SportsManager.SportChannel ch : channels) {
            if (ch.getPrimaryUrl().isEmpty()) continue;
            String key = ChannelPresentation.key(ch.name);
            if (mode.equals("favorites") && !favorites.contains(key)) continue;
            if (mode.equals("channels") && !category.isEmpty() && !category.equals(ch.category)) continue;
            if (mode.equals("search") && !key.contains(search)) continue;
            Group group = groups.get(key);
            if (group == null) { group = new Group(ch.name); groups.put(key, group); }
            group.variants.add(ch);
        }
        List<Group> result = new ArrayList<>(groups.values());
        for (Group group : result) group.variants.sort(Comparator.comparingInt(ch -> ChannelPresentation.rank(ch.name)));
        return result;
    }

    private void render(boolean restore) {
        if (!alive()) return;
        int generation = ++imageGeneration;
        shell.removeAllViews(); focusViews.clear();
        // Header belongs to the page's ScrollView; it never obscures the channel grid.
        LinearLayout header = HomeStyle.row(this);
        TextView brand = HomeStyle.text(this, "seyir tv", 22, HomeStyle.WHITE, true);
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(46), 1)); brand.setGravity(Gravity.CENTER_VERTICAL);
        nav(header, "Favoriler", "favorites", mode.equals("favorites"), () -> switchMode("favorites"));
        nav(header, "Kanallar", "channels", mode.equals("channels"), () -> switchMode("channels"));
        nav(header, "Ara", "search", mode.equals("search"), this::search);
        nav(header, "⚙", "settings", false, this::settings);
        focusViews.get("nav:settings").setContentDescription("Ayarlar");
        shell.addView(header, new LinearLayout.LayoutParams(-1, -2));

        List<Group> groups = visibleGroups();
        LinearLayout heading = HomeStyle.row(this); heading.setPadding(0, dp(16), 0, dp(14));
        String title = mode.equals("favorites") ? "Favorilerin" : mode.equals("search") ? "“" + query + "”" : category.isEmpty() ? "Tüm kanallar" : category;
        TextView h = HomeStyle.text(this, title, 23, HomeStyle.WHITE, true); h.setSingleLine(true);
        h.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heading.addView(h, new LinearLayout.LayoutParams(0, -2, 1));
        status = HomeStyle.text(this, loading ? "Kanallar hazırlanıyor…" : groups.size() + " kanal", 12, HomeStyle.MUTED, false);
        status.setMaxLines(1); status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heading.addView(status, new LinearLayout.LayoutParams(dp(160), -2)); status.setGravity(Gravity.END);
        TextView filter = HomeStyle.button(this, "Kategoriler  ⌄", !category.isEmpty() && mode.equals("channels"), this::categories);
        LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(-2, dp(40)); filterParams.leftMargin = dp(18);
        heading.addView(filter, filterParams); register("nav:categories", filter);
        shell.addView(heading);
        content = HomeStyle.column(this); content.setClipChildren(false); content.setClipToPadding(false); shell.addView(content);
        if (groups.isEmpty()) {
            emptyState();
        } else {
            int width = Math.round(getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density);
            int columns = Math.max(2, Math.min(6, (width - 60) / 190));
            Set<String> favorites = favoriteKeys();
            for (int start = 0; start < groups.size(); start += columns) {
                LinearLayout row = HomeStyle.row(this); row.setGravity(Gravity.TOP); row.setClipChildren(false); row.setClipToPadding(false);
                Group open = null;
                for (int c = 0; c < columns; c++) {
                    int index = start + c;
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(172), 1);
                    lp.setMargins(c == 0 ? 0 : dp(9), 0, c == columns - 1 ? 0 : dp(9), dp(18));
                    if (index < groups.size()) {
                        Group group = groups.get(index);
                        row.addView(card(group, favorites.contains(group.key), generation), lp);
                        if (expanded.equals(group.key)) open = group;
                    } else row.addView(new View(this), lp);
                }
                content.addView(row);
                if (open != null) content.addView(variants(open, favorites.contains(open.key)));
            }
        }
        TextView hint = HomeStyle.text(this, "OK  Seç     ·     OK basılı tut  Favori     ·     Geri  Önceki görünüm", 11, HomeStyle.MUTED, false);
        hint.setPadding(0, dp(10), 0, 0); shell.addView(hint);
        if (restore) restoreFocus();
    }

    private void nav(LinearLayout row, String label, String key, boolean active, Runnable action) {
        TextView b = HomeStyle.tab(this, label, active, action);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(44)); p.leftMargin = dp(7);
        row.addView(b, p); register("nav:" + key, b);
    }

    private void register(String key, View view) {
        view.setId(View.generateViewId()); focusViews.put(key, view);
        View.OnFocusChangeListener styling = view.getOnFocusChangeListener();
        view.setOnFocusChangeListener((v, focused) -> {
            if (styling != null) styling.onFocusChange(v, focused);
            if (focused) focusKey = key;
        });
    }

    private View card(Group group, boolean favorite, int generation) {
        LinearLayout card = HomeStyle.column(this); card.setPadding(dp(12), dp(12), dp(12), dp(10));
        card.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        HomeStyle.focus(card, expanded.equals(group.key), 20);
        FrameLayout art = new FrameLayout(this);
        art.setBackground(HomeStyle.shape(this, Color.rgb(228, 229, 232), 0, 14)); art.setClipToOutline(true);
        TextView initials = HomeStyle.text(this, group.name.substring(0, Math.min(2, group.name.length())).toUpperCase(java.util.Locale.ROOT), 28, Color.rgb(80, 89, 106), true);
        initials.setGravity(Gravity.CENTER); art.addView(initials, new FrameLayout.LayoutParams(-1, -1));
        ImageView logo = new ImageView(this); logo.setScaleType(ImageView.ScaleType.FIT_CENTER); logo.setPadding(dp(12), dp(9), dp(12), dp(9));
        art.addView(logo, new FrameLayout.LayoutParams(-1, -1));
        card.addView(art, new LinearLayout.LayoutParams(-1, dp(110)));
        if (favorite) {
            TextView star = HomeStyle.text(this, "★", 14, HomeStyle.WHITE, true); star.setGravity(Gravity.CENTER);
            star.setBackground(HomeStyle.shape(this, Color.rgb(50, 52, 58), 0, 16));
            FrameLayout.LayoutParams badge = new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.TOP | Gravity.END);
            badge.setMargins(0, dp(6), dp(6), 0); art.addView(star, badge);
        }
        String logoUrl = ""; for (SportsManager.SportChannel ch : group.variants) if (ch.logo != null && !ch.logo.isEmpty()) { logoUrl = ch.logo; break; }
        poster(logo, initials, logoUrl, generation);
        TextView title = HomeStyle.text(this, group.name, 14, HomeStyle.WHITE, true);
        title.setSingleLine(true); title.setEllipsize(android.text.TextUtils.TruncateAt.END); title.setPadding(0, dp(10), 0, dp(5)); card.addView(title);
        card.setContentDescription(group.name + (favorite ? ", favori" : "") + ", kalite seçeneklerini aç");
        register("card:" + group.key, card);
        card.setOnClickListener(v -> {
            savedScroll = scroll.getScrollY();
            boolean closing = expanded.equals(group.key); expanded = closing ? "" : group.key;
            focusKey = closing ? "card:" + group.key : "quality:" + group.variants.get(0).id;
            render(true);
        });
        card.setOnLongClickListener(v -> { toggleFavorite(group); return true; });
        return card;
    }

    private View variants(Group group, boolean favorite) {
        LinearLayout panel = HomeStyle.column(this); panel.setPadding(dp(16), dp(14), dp(16), dp(14));
        panel.setBackground(HomeStyle.shape(this, Color.rgb(29, 30, 35), 0, 22));
        panel.setAlpha(0f); panel.animate().alpha(1f).setDuration(160).start();
        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(-1, -2); panelParams.bottomMargin = dp(14); panel.setLayoutParams(panelParams);
        LinearLayout titleRow = HomeStyle.row(this);
        titleRow.addView(HomeStyle.text(this, group.name + "  ·  Yayın seç", 17, HomeStyle.WHITE, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView fav = HomeStyle.button(this, favorite ? "★" : "☆", favorite, () -> toggleFavorite(group));
        fav.setContentDescription(favorite ? "Favoriden çıkar" : "Favoriye ekle");
        titleRow.addView(fav); register("favorite:" + group.key, fav);
        TextView close = HomeStyle.button(this, "×", false, () -> {
            expanded = ""; savedScroll = scroll.getScrollY(); focusKey = "card:" + group.key; render(true);
        });
        close.setContentDescription("Kalite seçeneklerini kapat");
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2); cp.leftMargin = dp(8); titleRow.addView(close, cp); register("close:" + group.key, close);
        panel.addView(titleRow);
        TextView note = HomeStyle.text(this, "Kaynak kalitesi", 11, HomeStyle.MUTED, false);
        note.setPadding(0, dp(7), 0, dp(10)); panel.addView(note);
        Map<String, Integer> totals = new LinkedHashMap<>(), seen = new LinkedHashMap<>();
        for (SportsManager.SportChannel ch : group.variants) totals.merge(ChannelPresentation.quality(ch.name), 1, Integer::sum);
        LinearLayout row = null;
        for (int i = 0; i < group.variants.size(); i++) {
            if (i % 4 == 0) { row = HomeStyle.row(this); panel.addView(row); }
            SportsManager.SportChannel ch = group.variants.get(i);
            String quality = ChannelPresentation.quality(ch.name); int number = seen.merge(quality, 1, Integer::sum);
            String label = "▶  " + quality + (totals.get(quality) > 1 ? " · Seçenek " + number : "");
            TextView b = HomeStyle.button(this, label, false, () -> play(ch));
            b.setContentDescription(group.name + ", " + quality + ", seçenek " + number);
            register("quality:" + ch.id, b);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(46), 1); p.setMargins(0, 0, dp(8), dp(6)); row.addView(b, p);
        }
        return panel;
    }

    private void toggleFavorite(Group group) {
        boolean remove = favoriteKeys().contains(group.key);
        if (remove) {
            for (TitleItem old : library.list("favorites")) if (ChannelPresentation.key(old.title).equals(group.key)) library.save("favorites", old, true);
        } else library.save("favorites", group.variants.get(0).toTitleItem(), false);
        savedScroll = scroll.getScrollY();
        Toast.makeText(this, remove ? "Favorilerden çıkarıldı" : "Favorilere eklendi", Toast.LENGTH_SHORT).show();
        render(true);
    }

    private void emptyState() {
        LinearLayout empty = HomeStyle.column(this); empty.setPadding(dp(28), dp(34), dp(28), dp(34));
        empty.setBackground(HomeStyle.shape(this, HomeStyle.PANEL, 0, 20));
        String title = loading ? "Kanallar yükleniyor" : mode.equals("favorites") ? "Sevdiğin kanallar burada" : "Kanal bulunamadı";
        empty.addView(HomeStyle.text(this, title, 22, HomeStyle.WHITE, true));
        String subtitle = loading ? "Liste hazır olduğunda burada görünecek." : mode.equals("favorites") ? "Kanallar bölümünde bir kartta OK tuşunu basılı tutarak favorilerine ekleyebilirsin." : mode.equals("search") ? "Farklı bir kanal adıyla tekrar ara." : "Başka bir kategori seçebilir veya Ayarlar’dan listeyi yenileyebilirsin.";
        TextView description = HomeStyle.text(this, subtitle, 14, HomeStyle.MUTED, false); description.setPadding(0, dp(12), 0, dp(20)); empty.addView(description);
        if (!loading) {
            TextView b = HomeStyle.button(this, mode.equals("search") ? "Yeniden ara" : "Kanallara git", true, () -> {
                if (mode.equals("search")) search(); else { category = ""; switchMode("channels"); }
            });
            empty.addView(b, new LinearLayout.LayoutParams(-2, -2)); register("empty", b);
        }
        content.addView(empty);
    }

    private void switchMode(String next) {
        mode = next; expanded = ""; focusKey = ""; savedScroll = 0; render(true);
    }

    private void search() {
        EditText input = new EditText(this); input.setSingleLine(true); input.setText(query);
        input.setHint("Kanal adı"); input.setInputType(InputType.TYPE_CLASS_TEXT); input.setSelectAllOnFocus(true);
        input.setTextColor(HomeStyle.WHITE); input.setHintTextColor(HomeStyle.MUTED);
        LinearLayout box = HomeStyle.column(this); box.setPadding(dp(24), dp(8), dp(24), dp(8)); box.addView(input);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Kanal ara").setView(box)
            .setPositiveButton("Ara", (d, w) -> {
                ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(), 0);
                query = input.getText().toString().trim(); switchMode("search");
            }).setNegativeButton("Vazgeç", null).create();
        dialog.setOnShowListener(d -> { styleDialog(dialog); input.requestFocus(); }); dialog.show();
    }

    private void categories() {
        java.text.Collator trCollator = java.text.Collator.getInstance(new java.util.Locale("tr"));
        Set<String> unique = new java.util.TreeSet<>((a, b) -> trCollator.compare(a, b));
        for (SportsManager.SportChannel ch : channels) if (ch.category != null && !ch.category.trim().isEmpty()) unique.add(ch.category);
        List<String> choices = new ArrayList<>(); choices.add("Tüm kanallar"); choices.addAll(unique);
        int selected = category.isEmpty() ? 0 : Math.max(0, choices.indexOf(category));
        AlertDialog d = new AlertDialog.Builder(this).setTitle("Kategoriler")
            .setSingleChoiceItems(choices.toArray(new String[0]), selected, (dialog, which) -> {
                category = which == 0 ? "" : choices.get(which); prefs.edit().putString("category", category).apply();
                dialog.dismiss(); switchMode("channels");
            }).setNegativeButton("Kapat", null).create(); d.show(); styleDialog(d);
    }

    private void settings() {
        String version = ""; try { version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) { }
        AlertDialog d = new AlertDialog.Builder(this).setTitle("Ayarlar · " + version)
            .setItems(new String[]{"Kanal listesini yenile", "Güncellemeleri kontrol et"}, (dialog, which) -> {
                if (which == 0) { savedScroll = scroll.getScrollY(); load(true); }
                else AppUpdater.check(this, true);
            }).setNegativeButton("Kapat", null).create(); d.show(); styleDialog(d);
    }

    private void styleDialog(AlertDialog d) {
        if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(HomeStyle.shape(this, HomeStyle.PANEL, 0, 22));
        d.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(HomeStyle.ACCENT);
        d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(HomeStyle.ACCENT);
    }

    private void play(SportsManager.SportChannel ch) {
        savedScroll = scroll.getScrollY(); returning = true;
        // Identical player contract to the previous MainActivity. Never combine quality tiers.
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra("item", ch.toTitleItem().json().toString());
        i.putExtra("url", ch.getPrimaryUrl());
        if (ch.urls.size() > 1) i.putExtra("fallbackUrls", ch.urls.toArray(new String[0]));
        startActivity(i);
    }

    private void restoreFocus() {
        String desired = focusKey; int y = savedScroll; int generation = imageGeneration;
        scroll.post(() -> {
            if (!alive() || generation != imageGeneration) return;
            View target = focusViews.get(desired);
            if (target == null) {
                for (Map.Entry<String, View> entry : focusViews.entrySet()) if (entry.getKey().startsWith("card:")) { target = entry.getValue(); break; }
            }
            if (target == null) target = focusViews.getOrDefault("empty", focusViews.get("nav:channels"));
            if (target != null) target.requestFocus();
            // Restore exact position only when the focused control was already on this page.
            if (focusViews.containsKey(desired)) scroll.post(() -> scroll.scrollTo(0, y));
            else scroll.scrollTo(0, 0);
        });
    }

    @Override protected void onResume() {
        super.onResume(); getWindow().getDecorView().setSystemUiVisibility(5894);
        if (returning) { returning = false; restoreFocus(); }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("mode", mode); state.putString("category", category); state.putString("query", query);
        state.putString("expanded", expanded); state.putString("focus", focusKey); state.putInt("scroll", scroll.getScrollY());
        super.onSaveInstanceState(state);
    }

    @Override public void onBackPressed() {
        if (!expanded.isEmpty()) {
            focusKey = "card:" + expanded; expanded = ""; savedScroll = scroll.getScrollY(); render(true);
        } else if (!mode.equals("favorites")) switchMode("favorites");
        else super.onBackPressed();
    }

    private void poster(ImageView view, TextView placeholder, String url, int generation) {
        if (url.isEmpty()) return;
        Bitmap cached = cache.get(url);
        if (cached != null) { view.setImageBitmap(cached); placeholder.setVisibility(View.GONE); return; }
        images.execute(() -> {
            if (generation != imageGeneration || !alive()) return;
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection)new URL(url).openConnection(); conn.setConnectTimeout(6000); conn.setReadTimeout(6000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                byte[] data;
                try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[8192]; int n;
                    while ((n = in.read(buf)) != -1) { if (out.size() + n > 3 * 1024 * 1024) return; out.write(buf, 0, n); }
                    data = out.toByteArray();
                }
                BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, options); options.inSampleSize = 1;
                while (options.outWidth / options.inSampleSize > 400 || options.outHeight / options.inSampleSize > 240) options.inSampleSize *= 2;
                options.inJustDecodeBounds = false; Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
                if (bitmap == null) return; cache.put(url, bitmap);
                runOnUiThread(() -> { if (alive() && generation == imageGeneration) { view.setImageBitmap(bitmap); placeholder.setVisibility(View.GONE); } });
            } catch (Exception ignored) { } finally { if (conn != null) conn.disconnect(); }
        });
    }

    @Override protected void onDestroy() { ++loadGeneration; ++imageGeneration; images.shutdownNow(); super.onDestroy(); }
}
