package tv.seyir.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.security.MessageDigest;

public final class SportsManager {
    public static final String REMOTE_SPORTS_URL = "https://raw.githubusercontent.com/muco3327/seyir-tv/main/sports.json";
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final Map<String, SportChannel> channelMap = new LinkedHashMap<>();
    private static List<SportChannel> cachedList = null;

    public static class SportChannel {
        public final String id;
        public final String name;
        public final String logo;
        public final String category;
        public final List<String> urls;
        public final Map<String, String> headers;

        public SportChannel(String id, String name, String logo, String category, List<String> urls, Map<String, String> headers) {
            this.id = id;
            this.name = name;
            this.logo = logo;
            this.category = category;
            this.urls = urls;
            this.headers = headers;
        }

        public String getPrimaryUrl() {
            return (urls != null && !urls.isEmpty()) ? urls.get(0) : "";
        }

        public TitleItem toTitleItem() {
            return new TitleItem(Source.SPORTS, name, getPrimaryUrl(), logo, category);
        }
    }

    public interface Callback {
        void onLoaded(List<SportChannel> channels);
    }

    public interface StatusCallback {
        void onStatus(String status);
    }

    private static String md5(String s) {
        try {
            MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            digest.update(s.getBytes("UTF-8"));
            byte[] messageDigest = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : messageDigest) {
                String h = Integer.toHexString(0xFF & b);
                while (h.length() < 2) h = "0" + h;
                hexString.append(h);
            }
            return hexString.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString().substring(0, 8);
        }
    }

    private static String cleanChannelName(String name) {
        if (name == null) return "";
        String cleaned = name.replaceAll("(?i)^[A-Z0-9]{1,4}[:|\\\\-]\\\\s*", "");
        cleaned = cleaned.replaceAll("\\[.*?\\]|\\\\(.*?\\\\)", "");
        cleaned = cleaned.replaceAll("(?i)\\\\b(FHD|UHD|4K|HD|SD|HEVC|H\\\\.265|1080p|720p)\\\\b", "");
        cleaned = cleaned.replaceAll("[^a-zA-Z0-9\\u00C0-\\u017F]+", "");
        return cleaned.toLowerCase(Locale.ROOT).trim();
    }

    public static void loadChannels(Context context, boolean forceRefresh, Callback callback, StatusCallback statusCallback) {
        if (!forceRefresh && cachedList != null && !cachedList.isEmpty()) {
            callback.onLoaded(new ArrayList<>(cachedList));
            return;
        }

        if (forceRefresh) {
            GithubScanner.clearCache();
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            Map<String, SportChannel> aggregated = new LinkedHashMap<>();
            Set<String> seenUrls = new HashSet<>();

            // 1. Yerel / JSON Yükle
            if (statusCallback != null) mainHandler.post(() -> statusCallback.onStatus("Yerel liste yukleniyor..."));
            String json = fetchRemoteJson();
            if (json == null || json.trim().isEmpty()) {
                json = loadAssetJson(context);
            }

            List<SportChannel> baseChannels = parseJson(json);
            for (SportChannel ch : baseChannels) {
                String cleanKey = cleanChannelName(ch.name);
                if (cleanKey.isEmpty()) cleanKey = md5(ch.name);
                aggregated.put(cleanKey, ch);
                seenUrls.addAll(ch.urls);
            }

            // 2. Github Tarama
            if (statusCallback != null) mainHandler.post(() -> statusCallback.onStatus("IPTV listeleri taraniyor..."));
            List<String> m3uUrls = GithubScanner.scanPlaylists(msg -> {
                if (statusCallback != null) mainHandler.post(() -> statusCallback.onStatus(msg));
            });

            // 3. M3U İndirme ve Birleştirme
            for (String m3uUrl : m3uUrls) {
                String shortName = m3uUrl.substring(m3uUrl.lastIndexOf('/') + 1);
                if (statusCallback != null) mainHandler.post(() -> statusCallback.onStatus("Liste ayrıştırılıyor: " + shortName));
                
                parseM3uToAggregator(m3uUrl, aggregated, seenUrls);
            }

            List<SportChannel> channels = new ArrayList<>(aggregated.values());
            if (statusCallback != null) mainHandler.post(() -> statusCallback.onStatus("Toplam " + channels.size() + " tekil kanal yuklendi"));

            if (!channels.isEmpty()) {
                synchronized (SportsManager.class) {
                    channelMap.clear();
                    for (SportChannel ch : channels) {
                        for (String u : ch.urls) {
                            channelMap.put(u, ch);
                        }
                        channelMap.put(ch.id, ch);
                    }
                    cachedList = channels;
                }
            }

            List<SportChannel> result = channels.isEmpty() && cachedList != null ? cachedList : channels;
            mainHandler.post(() -> callback.onLoaded(result));
        });
    }

    public static SportChannel getChannel(String urlOrId) {
        synchronized (SportsManager.class) {
            return channelMap.get(urlOrId);
        }
    }

    private static String fetchRemoteJson() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(REMOTE_SPORTS_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setUseCaches(false);
            conn.setRequestProperty("User-Agent", "SeyirTV-Sports");
            if (conn.getResponseCode() == 200) {
                try (InputStream in = conn.getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line).append('\n');
                    return sb.toString();
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private static String loadAssetJson(Context context) {
        try (InputStream in = context.getAssets().open("sports.json");
             BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static List<SportChannel> parseJson(String json) {
        List<SportChannel> list = new ArrayList<>();
        if (json == null) return list;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String id = obj.optString("id", "ch_" + i);
                String name = obj.optString("name", "Kanal " + (i + 1));
                String logo = obj.optString("logo", "");
                String category = obj.optString("category", "Spor");

                List<String> urls = new ArrayList<>();
                JSONArray uArr = obj.optJSONArray("urls");
                if (uArr != null) {
                    for (int j = 0; j < uArr.length(); j++) {
                        String u = uArr.optString(j);
                        if (!u.isEmpty()) urls.add(u);
                    }
                } else if (obj.has("url")) {
                    String u = obj.optString("url");
                    if (!u.isEmpty()) urls.add(u);
                }

                Map<String, String> headers = new HashMap<>();
                JSONObject hObj = obj.optJSONObject("headers");
                if (hObj != null) {
                    Iterator<String> it = hObj.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        headers.put(k, hObj.optString(k));
                    }
                }

                if (!urls.isEmpty()) {
                    list.add(new SportChannel(id, name, logo, category, urls, headers));
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    private static void parseM3uToAggregator(String m3uUrl, Map<String, SportChannel> aggregated, Set<String> seenUrls) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(m3uUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            if (conn.getResponseCode() == 200) {
                try (InputStream in = conn.getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                    String line;
                    String currentName = null;
                    String currentLogo = "";
                    String currentGroup = "Diger";
                    Map<String, String> currentHeaders = new HashMap<>();
                    
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty()) continue;

                        if (line.startsWith("#EXTINF:")) {
                            currentHeaders.clear();
                            int comma = line.indexOf(',');
                            if (comma != -1) currentName = line.substring(comma + 1).trim();

                            
                            int logoStart = line.indexOf("tvg-logo=\"");
                            if(logoStart != -1){
                                int logoEnd = line.indexOf("\"", logoStart + 10);
                                if(logoEnd > logoStart) currentLogo = line.substring(logoStart + 10, logoEnd);
                            }

                            int groupStart = line.indexOf("group-title=\"");
                            if(groupStart != -1){
                                int groupEnd = line.indexOf("\"", groupStart + 13);
                                if(groupEnd > groupStart) currentGroup = line.substring(groupStart + 13, groupEnd);
                            }

                        } else if (line.startsWith("#EXTVLCOPT:")) {
                            if (line.contains("http-user-agent=")) {
                                currentHeaders.put("User-Agent", line.substring(line.indexOf("=") + 1).trim());
                            } else if (line.contains("http-referrer=")) {
                                currentHeaders.put("Referer", line.substring(line.indexOf("=") + 1).trim());
                            }
                        } else if (!line.startsWith("#")) {
                            if (currentName != null && (line.startsWith("http://") || line.startsWith("https://"))) {
                                String streamUrl = line;
                                
                                // Pipe syntax handling
                                if (streamUrl.contains("|")) {
                                    String[] parts = streamUrl.split("\\\\|", 2);
                                    streamUrl = parts[0];
                                    String[] params = parts[1].split("&");
                                    for(String p : params){
                                        if(p.toLowerCase().startsWith("user-agent=")){
                                            currentHeaders.put("User-Agent", p.substring(11));
                                        } else if(p.toLowerCase().startsWith("referer=")){
                                            currentHeaders.put("Referer", p.substring(8));
                                        }
                                    }
                                }

                                if (!seenUrls.contains(streamUrl)) {
                                    seenUrls.add(streamUrl);
                                    
                                    // Adult/XXX filtresi
                                    String lGroup = currentGroup.toLowerCase(Locale.ROOT);
                                    String lName = currentName.toLowerCase(Locale.ROOT);
                                    
                                    // Kategori Normalizasyonu
                                    if (lName.contains("bein") && !lName.contains("gurme") && !lName.contains("movies") && !lName.contains("series") && !lName.contains("iz") && !lName.contains("h&e")) {
                                        currentGroup = "beIN Sports";
                                    } else if (lName.contains("s sport") || lName.contains("ssport")) {
                                        currentGroup = "S Sport";
                                    } else if (lName.contains("trt spor") || lName.contains("trtspor")) {
                                        currentGroup = "TRT Spor";
                                    } else if (lName.contains("tivibu spor") || lName.contains("tivibuspor")) {
                                        currentGroup = "Tivibu Spor";
                                    } else if (lName.contains("smart spor") || lName.contains("smartspor")) {
                                        currentGroup = "Smart Spor";
                                    } else if (lName.contains("a spor") || lName.contains("aspor") || lName.contains("a sport")) {
                                        currentGroup = "A Spor";
                                    } else if (lName.contains("exxen")) {
                                        currentGroup = "Exxen";
                                    } else if (lName.contains("bein movies") || lName.contains("bein series")) {
                                        currentGroup = "beIN Sinema & Dizi";
                                    } else if (lGroup.contains("spor") || lGroup.contains("sport")) {
                                        currentGroup = "Spor";
                                    }

                                    if(!lGroup.contains("adult") && !lGroup.contains("xxx") && !lGroup.contains("+18") && !lName.contains("xxx")) {
                                        
                                        String cleanKey = cleanChannelName(currentName);
                                        if(cleanKey.isEmpty()) cleanKey = md5(currentName);

                                        if (aggregated.containsKey(cleanKey)) {
                                            SportChannel existing = aggregated.get(cleanKey);
                                            existing.urls.add(streamUrl);
                                            if (existing.logo.isEmpty() && !currentLogo.isEmpty()) {
                                                // cannot modify final logo, that's fine
                                            }
                                        } else {
                                            String id = "gh_" + md5(cleanKey + streamUrl).substring(0, 8);
                                            List<String> urls = new ArrayList<>();
                                            urls.add(streamUrl);
                                            aggregated.put(cleanKey, new SportChannel(id, currentName, currentLogo, currentGroup, urls, new HashMap<>(currentHeaders)));
                                        }
                                    }
                                }
                            }
                            currentName = null;
                            currentLogo = "";
                            currentGroup = "Diger";
                            currentHeaders.clear();
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}