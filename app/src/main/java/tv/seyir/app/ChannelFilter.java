package tv.seyir.app;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Deliberately conservative: a generic category such as General is not an allow rule. */
final class ChannelFilter {
    private static final Pattern FOREIGN = Pattern.compile("(^|[^a-z])(fr|france|french|francais|en|eng|english|uk|usa|us|de|german|germany|arabic|ar|arab|es|esp|spanish|it|italian|pt|portugal|pl|poland|nl|netherlands|ru|russian)([^a-z]|$)");
    private static final Pattern NATIONAL = Pattern.compile("(?:trt(?:1|2|3|4k|haber|spor|sporyildiz|cocuk|belgesel|muzik|turk|avaz|diyanetcocuk)|startv|star|kanald|showtv|show|atv|atvavrupa|ahaber|apara|anews|a2|now|nowtv|fox|foxtv|tv8|tv85|kanal7|ulketv|kanal24|24tv|tv24|ntv|cnnturk|haberturk|haberglobal|halktv|tele1|szctv|sozcutv|tgrthaber|tv100|beyaztv|360|tv360|teve2|tv2|dmax|tlc|cnbce|e2|ekoltv|fb tv|fbtv|gstv|tabiitv|tabitv)");

    static String normalized(String value) {
        String s = (value == null ? "" : value).toLowerCase(Locale.ROOT).replace('ı','i');
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    static String canonicalCategory(String name, String group) {
        String n = normalized(name);
        String g = normalized(group);

        // 1. beIN Sports: strictly sports (not movies/entertainment)
        if (n.contains("bein") || g.contains("bein")) {
            if (n.contains("movie") || n.contains("sinema") || n.contains("series") || n.contains("dizi") || n.contains("iz") || n.contains("box")) {
                return "Sinema & Dizi";
            }
            return "beIN Sports";
        }

        // 2. Belgesel
        if (n.contains("belgesel") || g.contains("belgesel") || n.contains("dmax") || n.contains("tlc") || n.contains("nat geo") || n.contains("natgeo") || n.contains("discovery") || n.contains("history") || n.contains("animal")) {
            return "Belgesel";
        }

        // 3. Haber
        if (n.contains("haber") || g.contains("haber") || n.contains("ntv") || n.contains("cnnturk") || n.contains("cnn turk") || n.contains("tele1") || n.contains("szc") || n.contains("sozcu") || n.contains("tv100") || n.contains("ekol tv") || n.contains("ekoltv") || n.contains("tgrt") || n.contains("bloomberg") || n.contains("apara") || n.contains("24 tv") || n.contains("kanal 24") || n.contains("ulke tv") || n.contains("ulketv")) {
            return "Haber";
        }

        // 4. Çocuk
        if (n.contains("cocuk") || g.contains("cocuk") || n.contains("minika") || n.contains("cartoon") || n.contains("disney") || n.contains("nickelodeon") || n.contains("boomerang")) {
            return "Çocuk";
        }

        // 5. Müzik
        if (n.contains("muzik") || g.contains("muzik") || n.contains("kral") || n.contains("power") || n.contains("number1") || n.contains("number 1") || n.contains("dream") || n.contains("nr1")) {
            return "Müzik";
        }

        // 6. Sinema & Dizi
        if (n.contains("sinema") || g.contains("sinema") || n.contains("film") || g.contains("film") || n.contains("dizi") || g.contains("dizi") || n.contains("movie") || n.contains("salon") || n.contains("filmbox") || n.contains("yesilcam")) {
            return "Sinema & Dizi";
        }

        // 7. Spor (all non-beIN sports)
        if (n.contains("spor") || g.contains("spor") || n.contains("sport") || g.contains("sport") || n.contains("exxen") || n.contains("euro") || n.contains("ssport") || n.contains("s sport") || n.contains("tivibu") || n.contains("smart") || n.contains("aspor") || n.contains("htspor") || n.contains("fbtv") || n.contains("gstv") || n.contains("tv8.5") || n.contains("tv85") || n.contains("tabii") || n.contains("nba") || n.contains("fight") || n.contains("racing") || n.contains("motorsport")) {
            return "Spor";
        }

        // 8. Ulusal (default)
        return "Ulusal";
    }

    static boolean allows(String name, String group) {
        String n = normalized(name), g = normalized(group);
        if (FOREIGN.matcher(n + " " + g).find() || n.matches("^(fr|uk|us|en|ar|es|de|it|pt|pl|ru|nl)bein.*")) return false;
        if ((n + " " + g).matches(".*\\b(adult|xxx|nsfw)\\b.*")) return false;
        if ((n + " " + g).matches(".*\\b(mobil|mobile)\\b.*")) return false;
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
        if (clean.matches("(?:aspor|htspor|trtspor|trtsporyildiz|tabiispor|tabispor|tabiitv|tabitv|tabii|tabi|ssport|ssportplus|tivibuspor|smartspor|spor smart|sporsmart|exxen|ekolsports|tjktv)[0-9]*")) return true;
        if (clean.equals("beinsportshaber")) return true;
        if (clean.matches("bein(?:sports?|connect)(?:max)?[0-9]*(?:hd|sd|fhd|uhd|hq|4k)?")) return turkish;
        if (clean.matches("(?:sinematv|sinema|sinemayerli|sinemaaile|sinemaaksiyon|sinemakomedi|sinemayuzbir|sinemafantastik|sinemakorku|yesilcam|filmbox|tivibusinema)[0-9]*(?:tv|hd)?")) return true;
        return turkish && clean.matches("(?:beinmovies|beinsinema|moviesmart|filmbox|cinemax)(?:premiere|premier|action|stars|family|fest|turk|comedy|classic|gold|platin|premium|extra|plus)*[0-9]*(?:hd|uhd)?");
    }

    private static boolean allowsBeinVariant(String n, String g) {
        if (!n.matches(".*bein\\s*sports?.*")) return false;
        boolean country = n.matches(".*(?:\\bturkey\\b|\\btr\\b).*")
            || g.matches(".*\\b(tr|turkey|turkiye|turkish)\\b.*");
        boolean localGroup = g.equals("spor") || g.startsWith("spor-") || g.equals("atom spor") || g.equals("tr spor") || g.equals("bein-tv247") || g.equals("tr-spor") || g.startsWith("tr-") || g.contains("derbi") || g.contains("super lig");
        String clean = n.replaceAll("\\[[^]]*]", " ")
            .replaceAll("\\b(turkey|tr|hd|fhd|uhd|hq|sd|1080p|720p)\\b", " ")
            .replaceAll("(?:[- /]+(?:atom|zeus|cdn|mahsun|forestgump|tv247|ace|tal|talip|tul|mobil)(?:\\s+1)?)$", "")
            .replaceAll("[^a-z0-9]", "");
        return (country || localGroup) && clean.matches("beinsports?(?:[1-5]|max[12]|haber)");
    }
}
