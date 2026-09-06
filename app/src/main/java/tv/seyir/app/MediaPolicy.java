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
    public static boolean isAd(String url) {
        if (url == null) return false;
        String u = url.toLowerCase(Locale.ROOT);
        return u.contains("doubleclick") || u.contains("googlesyndication") ||
               u.contains("googleadservices") || u.contains("googleads") ||
               u.contains("adservice") || u.contains("adsystem") ||
               u.contains("adsterra") || u.contains("propellerads") ||
               u.contains("popcash") || u.contains("popads") ||
               u.contains("exoclick") || u.contains("trafficjunky") ||
               u.contains("trafficfactory") || u.contains("adnxs") ||
               u.contains("yadro.ru") || u.contains("histats") ||
               u.contains("criteo") || u.contains("smartadserver") ||
               u.contains("taboola") || u.contains("outbrain") ||
               u.contains("/ads/") || u.contains("/ad/") ||
               u.contains("preroll") || u.contains("pre-roll") ||
               u.contains("midroll") || u.contains("postroll") ||
               u.contains("vast") || u.contains("vpaid") ||
               u.contains("sponsor") || u.contains("banner") ||
               u.contains("popup") || u.contains("popunder") ||
               u.contains("adurl") || u.contains("video_ad") ||
               u.contains("betwinner") || u.contains("1xbet") ||
               u.contains("adtrue") || u.contains("richaudience") ||
               u.contains("mgid") || u.contains("revcontent") ||
               u.contains("ad-delivery") || u.contains("a-delivery") ||
               u.contains("creativecdn") || u.contains("rubiconproject") ||
               u.contains("openx") || u.contains("pubmatic") ||
               u.contains("monetag") || u.contains("hilltopads") ||
               u.contains("clickadu");
    }
    public static boolean isVideo(String url) {
        if (!isHttps(url)) return false;
        if (isAd(url)) return false;
        try {
            String p = URI.create(url).getPath();
            if (p == null) return false;
            p = p.toLowerCase(Locale.ROOT);
            return p.endsWith(".m3u8") || p.endsWith(".mp4");
        } catch (RuntimeException e) { return false; }
    }
    public static boolean isEpisode(String url) {
        if (url == null) return false;
        return url.matches(".*(?:[0-9]+-sezon-[0-9]+-bolum|/bolum/|[0-9]+-sezon|/dizi/[^/]+/[^/]+).*");
    }
    public static String key(String source, String page) {
        return source + ":" + page.replaceAll("[?#].*$", "");
    }
}
