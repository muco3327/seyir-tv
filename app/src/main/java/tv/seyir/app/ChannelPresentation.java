package tv.seyir.app;

import java.util.Locale;

/** Display grouping only. Never changes a channel's URLs or playback fallback list. */
final class ChannelPresentation {
    static String name(String raw) {
        String n = raw == null ? "" : raw.trim();
        n = n.replaceFirst("(?i)^(?:TR(?=BEIN)|(?:TR|TURKEY|TURKIYE|TURKISH)(?:\\s*[:|_-]\\s*|\\s+))", "");
        n = n.replaceAll("\\[[^]]*]|\\([^)]*\\)", " ");
        // TRT 4K is a separate service, not the UHD version of TRT 1.
        boolean trt4k = n.matches("(?i)TRT\\s*4K(?:\\s.*)?");
        n = n.replaceAll("(?i)\\b(?:FULL\\s*HD|FHD|UHD|HD|SD|HQ|2160[PI]|1080[PI]|720[PI]|576[PI]|480[PI]|HEVC|H[.]?265|50FPS|60FPS|VIP|PREMIUM)\\b", " ");
        if (!trt4k) n = n.replaceAll("(?i)\\b4K\\b", " ");
        n = n.replaceAll("(?i)bein\\s*sports?", "beIN Sports ")
            .replaceAll("(?i)bein\\s*connect", "beIN Connect ")
            .replaceAll("(?i)bein\\s*movies", "beIN Movies ");
        n = n.replaceAll("\\s+", " ").replaceAll("^[ |:_-]+|[ |:_-]+$", "").trim();
        return n.isEmpty() ? (raw == null ? "Kanal" : raw.trim()) : n;
    }

    static String key(String raw) {
        return ChannelFilter.normalized(name(raw)).replaceAll("[^a-z0-9]", "");
    }

    static String quality(String raw) {
        String tier = ChannelQuality.tier(raw);
        if (tier.equals("2160p")) return "UHD";
        if (tier.equals("unknown")) return "Standart";
        return tier;
    }

    static int rank(String raw) {
        switch (ChannelQuality.tier(raw)) {
            case "2160p": return 0;
            case "1080p": return 1;
            case "720p": return 2;
            case "HD": return 3;
            case "HQ": return 4;
            case "SD": return 5;
            default: return 6;
        }
    }
}
