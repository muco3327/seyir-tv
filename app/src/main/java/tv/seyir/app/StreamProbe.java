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

    static class Result {
        final String resolvedUrl;
        final String mime;

        Result(String resolvedUrl, String mime) {
            this.resolvedUrl = resolvedUrl;
            this.mime = mime;
        }
    }

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

    static Result inspect(StreamRequest request, Map<String, String> headers) throws IOException {
        return inspectUrl(request.url, headers, 0, true);
    }

    private static Result inspectUrl(String targetUrl, Map<String, String> headers, int depth, boolean allowProxyFallback) throws IOException {
        String currentUrl = targetUrl;
        for (int redirects = 0; redirects < 6; redirects++) {
            HttpURLConnection connection = null;
            try {
                URL u = new URL(currentUrl);
                connection = (HttpURLConnection) u.openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
                if (headers != null) {
                    headers.forEach(connection::setRequestProperty);
                }

                int status = connection.getResponseCode();
                // Follow cross-protocol or cross-port redirects
                if (status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_SEE_OTHER ||
                    status == 307 || status == 308) {
                    String loc = connection.getHeaderField("Location");
                    if (loc == null || loc.trim().isEmpty()) {
                        throw new IOException("Yayın sunucusu HTTP " + status + " döndürdü.");
                    }
                    currentUrl = new URL(u, loc).toString();
                    continue;
                }

                if (status < 200 || status >= 300) {
                    if (allowProxyFallback && !currentUrl.contains("150.230.29.27:3000/proxy")) {
                        try {
                            String proxied = SportsManager.toOracleProxy(targetUrl);
                            return inspectUrl(proxied, headers, depth + 1, false);
                        } catch (Exception ignored) {
                        }
                    }
                    throw new IOException("Yayın sunucusu HTTP " + status + " döndürdü.");
                }

                try (InputStream input = connection.getInputStream()) {
                    byte[] bytes = new byte[1024];
                    int total = 0;
                    while (total < bytes.length) {
                        int count = input.read(bytes, total, bytes.length - total);
                        if (count < 0) break;
                        total += count;
                    }
                    if (total == 0) throw new IOException("Yayın sunucusu boş yanıt döndürdü.");
                    String mime = detect(connection.getContentType(), new String(bytes, 0, total, StandardCharsets.UTF_8));
                    return new Result(currentUrl, mime);
                }
            } catch (IOException e) {
                if (allowProxyFallback && !currentUrl.contains("150.230.29.27:3000/proxy") && depth == 0) {
                    try {
                        String proxied = SportsManager.toOracleProxy(targetUrl);
                        return inspectUrl(proxied, headers, depth + 1, false);
                    } catch (Exception ignored) {
                    }
                }
                throw e;
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        throw new IOException("Yayın sunucusunda çok fazla yönlendirme döngüsü.");
    }
}
