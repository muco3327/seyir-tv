package tv.seyir.app;

import java.util.Locale;

/** Preserve advertised quality when grouping mirror URLs; never mix UHD and SD. */
final class ChannelQuality {
    static String tier(String name) {
        String n = name == null ? "" : name.toUpperCase(Locale.ROOT);
        if (n.matches(".*(?:2160P|UHD|4K).*")) return "2160p";
        if (n.matches(".*(?:1080[PI]|FHD|FULL\\s*HD).*")) return "1080p";
        if (n.contains("720P")) return "720p";
        if (n.matches(".*(?:576[PI]|480[PI]|\\bSD\\b).*")) return "SD";
        if (n.matches(".*\\bHD\\b.*")) return "HD";
        if (n.matches(".*\\bHQ\\b.*")) return "HQ";
        return "unknown";
    }
    static String groupKey(String channel, String name) { return channel + "|" + tier(name); }
}
