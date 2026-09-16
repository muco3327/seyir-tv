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

    // Reliable community-maintained IPTV lists (always available, no API limits)
    private static final String[] CURATED_LISTS = {
            "https://raw.githubusercontent.com/kadirsener1/mahsun/main/playlist.m3u",
            "https://raw.githubusercontent.com/omerdenizhan/IPTV-M3U/refs/heads/main/m3u/turkiye.m3u",
            "https://raw.githubusercontent.com/omerdenizhan/IPTV-M3U/refs/heads/main/m3u/turkiye-iptv-org.m3u",
            "https://raw.githubusercontent.com/myiptv2/iptv-playlist/main/kanallar.m3u",
            "https://iptv-org.github.io/iptv/countries/tr.m3u"
    };

    // GitHub search queries for discovering additional playlists
    private static final String[] SEARCH_QUERIES = {
            "iptv turkey m3u"
    };

    private static final String[] EXCLUDE_KEYWORDS = {"adult", "xxx", "nsfw"};

    private static List<String> cachedUrls = null;
    private static long lastScanTime = 0;

    public interface ProgressCallback {
        void onProgress(String message);
    }

    public static List<String> scanPlaylists(ProgressCallback callback) {
        if (cachedUrls != null && (System.currentTimeMillis() - lastScanTime < 15 * 60 * 1000)) {
            if (callback != null) callback.onProgress("Onceki tarama sonuclari kullaniliyor...");
            return new ArrayList<>(cachedUrls);
        }

        List<String> discoveredUrls = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();

        // Phase 1: Add curated community lists (these are always up-to-date)
        if (callback != null) callback.onProgress("Topluluk IPTV listeleri kontrol ediliyor...");
        for (String listUrl : CURATED_LISTS) {
            if (isUrlAccessible(listUrl)) {
                discoveredUrls.add(listUrl);
                seenUrls.add(listUrl);
                if (callback != null) callback.onProgress("Liste bulundu: " + listUrl.substring(listUrl.lastIndexOf('/') + 1));
            }
        }

        // Phase 2: GitHub search for additional playlists
        Set<String> seenRepos = new HashSet<>();
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -180);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String sinceDate = dateFormat.format(cal.getTime());

        for (String q : SEARCH_QUERIES) {
            try {
                if (callback != null) callback.onProgress("GitHub taranyor: " + q);
                String encodedQuery = URLEncoder.encode(q + " pushed:>" + sinceDate, "UTF-8");
                String searchUrl = "https://api.github.com/search/repositories?q=" + encodedQuery + "&sort=updated&order=desc&per_page=5";

                List<JSONObject> repos = fetchGithubSearchRepos(searchUrl);
                if (repos == null) {
                    if (callback != null) callback.onProgress("GitHub API limiti, atlanyor...");
                    continue;
                }

                for (JSONObject repo : repos) {
                    String repoFullName = repo.optString("full_name");
                    if (repoFullName.isEmpty() || seenRepos.contains(repoFullName)) continue;
                    seenRepos.add(repoFullName);

                    String branch = repo.optString("default_branch");
                    if (branch.isEmpty()) branch = "main";

                    String treeUrl = "https://api.github.com/repos/" + repoFullName + "/git/trees/" + branch + "?recursive=1";
                    List<JSONObject> treeFiles = fetchGithubTree(treeUrl);
                    if (treeFiles == null) continue;

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

                            // Only include files that look Turkish-related
                            boolean relevant = pathLower.contains("tr") || pathLower.contains("turk")
                                    || pathLower.contains("turkey") || pathLower.contains("turkiye")
                                    || pathLower.contains("playlist") || pathLower.contains("channels")
                                    || pathLower.contains("live");
                            if (!relevant) continue;

                            String rawUrl = "https://raw.githubusercontent.com/" + repoFullName + "/" + branch + "/" + path;
                            if (seenUrls.contains(rawUrl)) continue;
                            seenUrls.add(rawUrl);

                            if (isUrlAccessible(rawUrl)) {
                                discoveredUrls.add(rawUrl);
                            }
                        }
                    }
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                }
            } catch (Exception ignored) {}
        }

        cachedUrls = discoveredUrls;
        lastScanTime = System.currentTimeMillis();

        if (callback != null) callback.onProgress("Tarama tamamlandi: " + discoveredUrls.size() + " liste bulundu");
        return new ArrayList<>(discoveredUrls);
    }

    public static void clearCache() {
        cachedUrls = null;
        lastScanTime = 0;
    }

    private static List<JSONObject> fetchGithubSearchRepos(String urlStr) {
        try {
            String bodyStr = fetch(urlStr);
            if (bodyStr == null) return null;
            JSONObject json = new JSONObject(bodyStr);
            JSONArray items = json.optJSONArray("items");
            List<JSONObject> list = new ArrayList<>();
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject it = items.optJSONObject(i);
                    if (it != null) list.add(it);
                }
            }
            return list;
        } catch (Exception ignored) {}
        return null;
    }

    private static List<JSONObject> fetchGithubTree(String urlStr) {
        try {
            String bodyStr = fetch(urlStr);
            if (bodyStr == null) return null;
            JSONObject json = new JSONObject(bodyStr);
            JSONArray tree = json.optJSONArray("tree");
            List<JSONObject> list = new ArrayList<>();
            if (tree != null) {
                for (int i = 0; i < tree.length(); i++) {
                    JSONObject it = tree.optJSONObject(i);
                    if (it != null) list.add(it);
                }
            }
            return list;
        } catch (Exception ignored) {}
        return null;
    }

    private static String fetch(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
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

    private static boolean isUrlAccessible(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("HEAD");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            int code = conn.getResponseCode();
            return code == 200;
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return false;
    }
}
