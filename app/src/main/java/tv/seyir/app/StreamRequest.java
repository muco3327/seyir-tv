package tv.seyir.app;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.TreeMap;
import java.util.Locale;
import java.util.Map;

/** A stream and its own request headers; headers must never leak between sources. */
final class StreamRequest {
    final String url;
    final Map<String, String> headers = new TreeMap<>();

    StreamRequest(String value, Map<String, String> defaults) {
        String[] parts = value.split("\\|", 2);
        url = parts[0];
        if (defaults != null) defaults.forEach(this::put);
        if (parts.length == 2) for (String pair : parts[1].split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0) put(pair.substring(0, equals), decode(pair.substring(equals + 1)));
        }
    }

    private void put(String key, String value) {
        if (key.equalsIgnoreCase("referer")) headers.put("Referer", value);
        else if (key.equalsIgnoreCase("origin")) headers.put("Origin", value);
        else if (key.equalsIgnoreCase("user-agent")) headers.put("User-Agent", value);
    }

    private static String decode(String value) {
        try { return URLDecoder.decode(value, StandardCharsets.UTF_8.name()); }
        catch (Exception ignored) { return value; }
    }

    static String encode(String url, Map<String, String> headers) {
        StreamRequest request = new StreamRequest(url, headers);
        StringBuilder result = new StringBuilder(request.url);
        request.headers.forEach((key, value) -> {
            try {
                result.append(result.indexOf("|") < 0 ? '|' : '&').append(key).append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8.name()));
            } catch (Exception ignored) { }
        });
        return result.toString();
    }

    static boolean isHls(String url) {
        try {
            String path = URI.create(url).getPath().toLowerCase(Locale.ROOT);
            return path.endsWith(".m3u8") || path.endsWith(".m3u");
        } catch (Exception ignored) { return false; }
    }
}
