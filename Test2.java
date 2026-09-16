import java.util.Locale;

public class Test2 {
    private static String cleanChannelName(String name) {
        if (name == null) return "";
        String cleaned = name.replaceAll("(?i)^[A-Z0-9]{1,4}[:|\\-]\\s*", "");
        cleaned = cleaned.replaceAll("\\[.*?\\]|\\(.*?\\)", "");
        cleaned = cleaned.replaceAll("(?i)\\b(FHD|UHD|4K|HD|SD|HEVC|H\\.265|1080p|720p)\\b", "");
        cleaned = cleaned.replaceAll("[^a-zA-Z0-9\u00C0-\u017F]+", "");
        return cleaned.toLowerCase(Locale.ROOT).trim();
    }
    public static void main(String[] args) {
        System.out.println(cleanChannelName("TR | be›N SPORT 3 HD"));
        System.out.println(cleanChannelName("TR | beIN SPORT 3 FHD**"));
    }
}
