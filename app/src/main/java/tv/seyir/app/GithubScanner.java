package tv.seyir.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;

public class GithubScanner {

    private static final String[] SEARCH_QUERIES = {
            "iptv spor m3u",
            "iptv turkey m3u"
    };

    private static final String[] RELEVANT_KEYWORDS = {
            "tr", "turk", "turkey", "turkiye", "ulusal", "kanallar",
            "playlist", "channels", "tv", "live", "spor", "sport", "bein"
    };

    private static final String[] EXCLUDE_KEYWORDS = {"adult", "xxx", "nsfw"};
    
    private static List<String> cachedUrls = null;
    private static long lastScanTime = 0;

    public interface ProgressCallback {
        void onProgress(String message);
    }

    public static List<String> scanPlaylists(ProgressCallback callback) {
        if (cachedUrls != null && (System.currentTimeMillis() - lastScanTime < 15 * 60 * 1000)) {
            if (callback != null) callback.onProgress("GitHub nceki tarama sonular kullanlyor...");
            return cachedUrls;
        }

        List<String> discoveredUrls = new ArrayList<>();
        Set<String> seenRepos = new HashSet<>();
        Set<String> seenUrls = new HashSet<>();

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -180);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String sinceDate = dateFormat.format(cal.getTime());

        for (int qIndex = 0; qIndex < SEARCH_QUERIES.length; qIndex++) {
            String q = SEARCH_QUERIES[qIndex];
            try {
                String encodedQuery = URLEncoder.encode(q + " pushed:>" + sinceDate, "UTF-8");
                String searchUrl = "https://api.github.com/search/repositories?q=" + encodedQuery + "&sort=updated&order=desc&per_page=5";

                if (callback != null) callback.onProgress("GitHub Taranyor: " + q);

                List<JSONObject> repos = fetchGithubSearchRepos(searchUrl);
                if (repos == null) {
                    if (callback != null) callback.onProgress("GitHub Hata (API Limiti Ald). Bekleniyor...");
                    Thread.sleep(2000);
                    continue; // 403 yedik, devam et
                }
                
                for (JSONObject repo : repos) {
                    String repoFullName = repo.optString("full_name");
                    if (repoFullName.isEmpty() || seenRepos.contains(repoFullName)) continue;
                    seenRepos.add(repoFullName);

                    String branch = repo.optString("default_branch");
                    if (branch.isEmpty()) branch = "main";

                    String treeUrl = "https://api.github.com/repos/" + repoFullName + "/git/trees/" + branch + "?recursive=1";
                    List<JSONObject> treeFiles = fetchGithubTree(treeUrl);
                    if (treeFiles == null) {
                        Thread.sleep(1000);
                        continue;
                    }

                    for (JSONObject fileItem : treeFiles) {
                        String path = fileItem.optString("path", "");
                        String pathLower = path.toLowerCase(Locale.ROOT);
                        long size = fileItem.optLong("size", 0L);

                        if ((pathLower.endsWith(".m3u") || pathLower.endsWith(".m3u8")) && size > 1024L) {
                            boolean exclude = false;
                            for (String kw : EXCLUDE_KEYWORDS) {
                                if (pathLower.contains(kw)) { exclude = true; break; }
                            }
                            if (exclude) continue;

                            boolean relevant = false;
                            for (String kw : RELEVANT_KEYWORDS) {
                                if (pathLower.contains(kw)) { relevant = true; break; }
                            }
                            if (!relevant) continue;

                            String rawUrl = "https://raw.githubusercontent.com/" + repoFullName + "/" + branch + "/" + path;
                            if (seenUrls.contains(rawUrl)) continue;
                            seenUrls.add(rawUrl);

                            if (validateM3uUrl(rawUrl)) {
                                discoveredUrls.add(rawUrl);
                            }
                        }
                    }
                    Thread.sleep(1000); // 1 saniye bekle
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        
        cachedUrls = discoveredUrls;
        lastScanTime = System.currentTimeMillis();
        return discoveredUrls;
    }

    private static List<JSONObject> fetchGithubSearchRepos(String urlStr) {
        List<JSONObject> list = new ArrayList<>();
        try {
            String bodyStr = fetch(urlStr);
            if (bodyStr == null) return null;
            JSONObject json = new JSONObject(bodyStr);
            JSONArray items = json.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject it = items.optJSONObject(i);
                    if (it != null) list.add(it);
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    private static List<JSONObject> fetchGithubTree(String urlStr) {
        List<JSONObject> list = new ArrayList<>();
        try {
            String bodyStr = fetch(urlStr);
            if (bodyStr == null) return null;
            JSONObject json = new JSONObject(bodyStr);
            JSONArray tree = json.optJSONArray("tree");
            if (tree != null) {
                for (int i = 0; i < tree.length(); i++) {
                    JSONObject it = tree.optJSONObject(i);
                    if (it != null) list.add(it);
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    private static String fetch(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124.0.0.0");
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
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

    private static boolean validateM3uUrl(String rawUrl) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(rawUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            conn.setRequestProperty("Range", "bytes=0-8192");
            int code = conn.getResponseCode();
            if (code == 200 || code == 206) {
                try (InputStream in = conn.getInputStream()) {
                    byte[] buffer = new byte[8192];
                    int read = in.read(buffer);
                    if (read > 0) {
                        String chunk = new String(buffer, 0, read).toLowerCase(Locale.ROOT);
                        if (!chunk.contains("#extm3u") && !chunk.contains("#extinf")) return false;
                        if (chunk.contains("trt") || chunk.contains("atv") || chunk.contains("bein") || chunk.contains("spor") || rawUrl.toLowerCase(Locale.ROOT).contains("tr")) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return false;
    }
}
