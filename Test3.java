import java.util.Locale;
public class Test3 {
    public static void main(String[] args) {
        String n1 = "TR | beIN SPORT 3 HD";
        String n2 = "TR | beIN SPORT 3 FHD**";
        System.out.println("n1=" + cleanChannelName(n1));
        System.out.println("n2=" + cleanChannelName(n2));
    }
    private static String cleanChannelName(String name) {
        String cleaned = name.replaceAll("(?i)^[A-Z0-9]{1,4}[:|\\-]\\s*", "");
        cleaned = cleaned.replaceAll("\\[.*?\\]|\\(.*?\\)", "");
        cleaned = cleaned.replaceAll("(?i)\\b(FHD|UHD|4K|HD|SD|HEVC|H\\.265|1080p|720p)\\b", "");
        cleaned = cleaned.replaceAll("[^a-zA-Z0-9\\u00C0-\\u017F]+", "");
        return cleaned.toLowerCase(Locale.ROOT).trim();
    }
}