import java.util.Locale;
public class Test6 {
    public static void main(String[] args) {
        String[] names = {
            "beIN SPORTS 1 Turkey-TV247",
            "beIN SPORTS 1-CDN",
            "BeIN Sport 1-ZEUS",
            "TR | beIN SPORT 1 FHD",
            "beIN SPORTS 1-ATOM",
            "beIN SPORTS 1",
            "S Sport 1 - VIP"
        };
        for (String n : names) {
            System.out.println(n + " -> " + cleanChannelName(n));
        }
    }
    private static String cleanChannelName(String name) {
        if (name == null) return "";
        String cleaned = name;
        
        // Strip out any trailing -SERVERNAME or -VIP suffix
        int dashIndex = cleaned.lastIndexOf('-');
        if (dashIndex > 5) { 
            // only if dash is reasonably far in, to not break "S-Sport"
            // actually, "S Sport" doesn't have dash. "S-Sport" might.
            cleaned = cleaned.substring(0, dashIndex);
        }
        
        cleaned = cleaned.replaceAll("(?i)^[A-Z0-9]{1,4}\\s*[:|\\-]\\s*", "");
        cleaned = cleaned.replaceAll("\\[.*?\\]|\\(.*?\\)", "");
        cleaned = cleaned.replaceAll("(?i)\\b(FHD|UHD|4K|HD|SD|HEVC|H\\.265|1080p|720p|Turkey|TR|VIP|Premium)\\b", "");
        cleaned = cleaned.replaceAll("(?i)bein\\s*sports?", "beinsports");
        cleaned = cleaned.replaceAll("(?i)s\\s*sports?", "ssport");
        cleaned = cleaned.replaceAll("(?i)trt\\s*sports?", "trtspor");
        cleaned = cleaned.replaceAll("(?i)tivibu\\s*sports?", "tivibuspor");
        cleaned = cleaned.replaceAll("(?i)smart\\s*sports?", "smartspor");
        cleaned = cleaned.replaceAll("(?i)a\\s*sports?", "aspor");
        cleaned = cleaned.replaceAll("[^a-zA-Z0-9]+", ""); // only letters and numbers
        return cleaned.toLowerCase(Locale.ROOT).trim();
    }
}