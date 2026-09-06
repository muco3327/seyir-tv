package tv.seyir.app;

import java.net.URI;
import java.util.Locale;

/** Only network media; never file:, intent:, javascript:, local or credential-bearing URLs. */
public final class MediaPolicy {
    private MediaPolicy() { }
    public static boolean isHttps(String url) {
        try {
            URI uri = URI.create(url);
            String h = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || h == null || uri.getUserInfo() != null) return false;
            h = h.toLowerCase(Locale.ROOT);
            if (h.equals("localhost") || h.endsWith(".local") || h.endsWith(".localhost") || h.startsWith("[")) return false;
            if (h.matches("[0-9.]+")) return false;
            return true;
        } catch (RuntimeException e) { return false; }
    }
    public static boolean isVideo(String url) {
        if (!isHttps(url)) return false;
        String p = URI.create(url).getPath().toLowerCase(Locale.ROOT);
        return p.endsWith(".m3u8") || p.endsWith(".mp4");
    }
    public static String key(String source, String page) {
        return source + ":" + page.replaceAll("[?#].*$", "");
    }
}
