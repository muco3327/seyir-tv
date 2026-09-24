package tv.seyir.app;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Deliberately conservative: a generic category such as General is not an allow rule. */
final class ChannelFilter {
    private static final Pattern FOREIGN = Pattern.compile("(^|[^a-z])(fr|france|french|francais|en|eng|english|uk|usa|us|de|german|germany|arabic|ar|arab|es|esp|spanish|it|italian|pt|portugal|pl|poland|nl|netherlands|ru|russian)([^a-z]|$)");
    private static final Pattern NATIONAL = Pattern.compile("(?:trt(?:1|2|3|4k|haber|spor|sporyildiz|cocuk|belgesel|muzik|turk|avaz|diyanetcocuk)|startv|star|kanald|showtv|show|atv|atvavrupa|ahaber|apara|anews|a2|now|nowtv|fox|foxtv|tv8|tv85|kanal7|ulketv|kanal24|24tv|tv24|ntv|cnnturk|haberturk|haberglobal|halktv|tele1|szctv|sozcutv|tgrthaber|tv100|beyaztv|360|tv360|teve2|tv2|dmax|tlc|cnbce|e2|ekoltv|fb tv|fbtv|gstv)");

    static String normalized(String value) {
        String s = (value == null ? "" : value).toLowerCase(Locale.ROOT).replace('ı','i');
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    static boolean allows(String name, String group) {
        String n = normalized(name), g = normalized(group);
        if (FOREIGN.matcher(n + " " + g).find() || n.matches("^(fr|uk|us|en|ar|es|de|it|pt|pl|ru|nl)bein.*")) return false;
        if ((n + " " + g).matches(".*\\b(adult|xxx|nsfw)\\b.*")) return false;
        if (allowsBeinVariant(n, g)) return true;
        // Explicitly requested provider variants use Spor rather than a country tag.
        if (n.matches("bein\\s*sports?\\s*[1-5]\\s*[- ]\\s*(atom|zeus)")
                && g.matches("spor(?:-neon)?")) return true;
        boolean turkish = Pattern.compile("(^|[^a-z])(tr|turk|turkish|turkiye|turkey)([^a-z]|$)").matcher(n + " " + g).find() || n.startsWith("trbein");
        String clean = n.replaceFirst("^(?:tr(?=bein)|(?:tr|turkiye|turkey|turkish)(?:\\s*[:|_-]\\s*|\\s+))", "")
            .replaceAll("\\[[^]]*]|\\([^)]*\\)", "")
            .replaceAll("\\b(1080p|720p|2160p|480p|50fps|60fps|fhd|uhd|hd|sd|hq|hevc|h265|vip|premium)\\b", "")
            .replaceAll("[^a-z0-9]", "");
        if (clean.equals("tv4")) return false;
        if (NATIONAL.matcher(clean).matches()) return true;
        if (clean.matches("(?:aspor|htspor|trtspor|trtsporyildiz|ssport|ssportplus|tivibuspor|smartspor|spor smart|sporsmart|exxen|ekolsports|tjktv)[0-9]*")) return true;
        if (clean.equals("beinsportshaber")) return true;
        if (clean.matches("bein(?:sports?|connect)(?:max)?[0-9]*(?:hd|sd|fhd|uhd|hq|4k)?")) return turkish;
        if (clean.matches("(?:sinematv|sinema|sinemayerli|sinemaaile|sinemaaksiyon|sinemakomedi|sinemayuzbir|sinemafantastik|sinemakorku|yesilcam|filmbox|tivibusinema)[0-9]*(?:tv|hd)?")) return true;
        return turkish && clean.matches("(?:beinmovies|beinsinema|moviesmart|filmbox|cinemax)(?:premiere|premier|action|stars|family|fest|turk|comedy|classic|gold|platin|premium|extra|plus)*[0-9]*(?:hd|uhd)?");
    }

    private static boolean allowsBeinVariant(String n, String g) {
        if (!n.matches(".*bein\\s*sports?.*")) return false;
        boolean country = n.matches(".*(?:\\bturkey\\b|\\btr\\b).*")
            || g.matches(".*\\b(tr|turkey|turkiye|turkish)\\b.*");
        boolean localGroup = g.equals("spor") || g.startsWith("spor-") || g.equals("atom spor") || g.equals("tr spor") || g.equals("bein-tv247");
        String clean = n.replaceAll("\\[[^]]*]", " ")
            .replaceAll("\\b(turkey|tr|hd|fhd|uhd|hq|sd|1080p|720p)\\b", " ")
            .replaceAll("(?:[- ]+(?:atom|zeus|cdn|mahsun|forestgump|tv247|ace|tal|talip|tul)(?:\\s+1)?)$", "")
            .replaceAll("[^a-z0-9]", "");
        return (country || localGroup) && clean.matches("beinsports?(?:[1-5]|max[12]|haber)");
    }
}
