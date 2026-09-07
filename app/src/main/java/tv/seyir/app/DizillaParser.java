package tv.seyir.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class DizillaParser {
    private static final byte[] KEY = "9bYMCNQiWsXIYFWYAu7EkdsSbmGBTyUI".getBytes(StandardCharsets.UTF_8);
    private static final byte[] IV = new byte[16];
    private static final Pattern IFRAME_SRC = Pattern.compile("src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    private DizillaParser() { }

    public static JSONObject decrypt(String secureData) {
        if (secureData == null || secureData.isEmpty()) return null;
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY, "AES"), new IvParameterSpec(IV));
            byte[] raw = cipher.doFinal(Base64.getDecoder().decode(secureData));
            return new JSONObject(new String(raw, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void enrich(JSONObject data) {
        if (data == null) return;
        String secureData = data.optString("secureData", "");
        if (secureData.isEmpty()) return;
        JSONObject decrypted = decrypt(secureData);
        if (decrypted == null) return;

        JSONObject rel = decrypted.optJSONObject("RelatedResults");
        if (rel == null) return;

        // 1. Extract episode sources (when opening an episode)
        JSONObject epSources = rel.optJSONObject("getEpisodeSources");
        if (epSources != null) {
            JSONArray res = epSources.optJSONArray("result");
            if (res != null && res.length() > 0) {
                JSONArray frames = data.optJSONArray("frames");
                if (frames == null) {
                    frames = new JSONArray();
                    try { data.put("frames", frames); } catch (Exception ignored) { }
                }
                JSONArray actions = data.optJSONArray("actions");
                if (actions == null) {
                    actions = new JSONArray();
                    try { data.put("actions", actions); } catch (Exception ignored) { }
                }

                for (int i = 0; i < res.length(); i++) {
                    JSONObject item = res.optJSONObject(i);
                    if (item == null) continue;
                    String content = item.optString("source_content", "");
                    Matcher m = IFRAME_SRC.matcher(content);
                    if (m.find()) {
                        String src = m.group(1);
                        if (src.startsWith("//")) src = "https:" + src;
                        else if (!src.startsWith("http")) src = "https://" + src;
                        frames.put(src);

                        String lang = item.optString("language_name", "Oynat");
                        String name = item.optString("source_name", "");
                        String label = lang + (!name.isEmpty() ? " · " + name : "");

                        JSONObject action = new JSONObject();
                        try {
                            action.put("id", "dizilla_" + i);
                            action.put("label", label);
                            action.put("frame", src);
                            actions.put(action);
                        } catch (Exception ignored) { }
                    }
                }
            }
        }

        // 2. Extract series seasons and episodes (when opening series overview)
        JSONObject seasonAndEps = rel.optJSONObject("getSerieSeasonAndEpisodes");
        if (seasonAndEps != null) {
            JSONArray seasonList = seasonAndEps.optJSONArray("result");
            if (seasonList != null && seasonList.length() > 0) {
                JSONArray episodes = data.optJSONArray("episodes");
                if (episodes == null || episodes.length() == 0) {
                    episodes = new JSONArray();
                    try { data.put("episodes", episodes); } catch (Exception ignored) { }
                    for (int s = 0; s < seasonList.length(); s++) {
                        JSONObject seasonObj = seasonList.optJSONObject(s);
                        if (seasonObj == null) continue;
                        int seasonNo = seasonObj.optInt("season_no", s + 1);
                        JSONArray epList = seasonObj.optJSONArray("episodes");
                        if (epList != null) {
                            for (int e = 0; e < epList.length(); e++) {
                                JSONObject epObj = epList.optJSONObject(e);
                                if (epObj == null) continue;
                                String slug = epObj.optString("used_slug", "");
                                String epText = epObj.optString("episode_text", (e + 1) + ". Bölüm");
                                String epTitle = seasonNo + ". Sezon · " + epText;
                                JSONObject epEntry = new JSONObject();
                                try {
                                    epEntry.put("title", epTitle);
                                    epEntry.put("url", "https://dizilla.now/" + slug);
                                    epEntry.put("season", seasonNo);
                                    epEntry.put("info", "Bölüm");
                                    epEntry.put("image", "");
                                    episodes.put(epEntry);
                                } catch (Exception ignored) { }
                            }
                        }
                    }
                }
            }
        }
    }
}
