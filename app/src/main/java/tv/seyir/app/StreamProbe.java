package tv.seyir.app;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/** Reads only a small response prefix. Never assumes HTTP 200 means playable video. */
final class StreamProbe {
    static final String HLS = "application/x-mpegURL";
    static String detect(String contentType, String prefix) throws IOException {
        String body = prefix.replace("\uFEFF", "").trim();
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String lower = body.toLowerCase(Locale.ROOT);
        if (lower.startsWith("<!doctype html") || lower.startsWith("<html") || type.contains("text/html"))
            throw new IOException("Sunucu video yerine web sayfası döndürdü.");
        if (body.startsWith("#EXTM3U") || type.contains("mpegurl")) return HLS;
        if (type.contains("dash+xml") || lower.contains("<mpd"))
            throw new IOException("Bu kaynak DASH biçiminde; mevcut oynatıcı bu biçimi desteklemiyor.");
        if (type.contains("application/json") || lower.startsWith("{\""))
            throw new IOException("Sunucu video yerine hata yanıtı döndürdü.");
        return null; // Leave TS/MP4 and other progressive formats to Media3 extractors.
    }

    static String inspect(StreamRequest request, Map<String, String> headers) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(request.url).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        headers.forEach(connection::setRequestProperty);
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("Yayın sunucusu HTTP " + status + " döndürdü.");
            try (InputStream input = connection.getInputStream()) {
                byte[] bytes = new byte[512];
                int total = 0;
                while (total < bytes.length) {
                    int count = input.read(bytes, total, bytes.length - total);
                    if (count < 0) break;
                    total += count;
                }
                if (total == 0) throw new IOException("Yayın sunucusu boş yanıt döndürdü.");
                return detect(connection.getContentType(), new String(bytes, 0, total, StandardCharsets.UTF_8));
            }
        } finally { connection.disconnect(); }
    }
}
