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
import java.util.*;
import java.util.concurrent.Executors;

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

    public static void loadChannels(Context context, Callback callback) {
        if (cachedList != null && !cachedList.isEmpty()) {
            callback.onLoaded(new ArrayList<>(cachedList));
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            String json = fetchRemoteJson();
            if (json == null || json.trim().isEmpty()) {
                json = loadAssetJson(context);
            }

            List<SportChannel> channels = parseJson(json);
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
}
