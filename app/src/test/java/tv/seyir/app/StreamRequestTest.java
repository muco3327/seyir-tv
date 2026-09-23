package tv.seyir.app;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class StreamRequestTest {
    @Test public void preservesHeaderQueryAndEncodedCharacters() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://example.org/watch?a=1&b=two+three");
        headers.put("User-Agent", "Example Player/1.0");
        StreamRequest request = new StreamRequest(StreamRequest.encode("https://cdn.example/live", headers), null);
        assertEquals("https://cdn.example/live", request.url);
        assertEquals(headers, request.headers);
    }

    @Test public void fallbackDoesNotInheritPreviousHeaders() {
        StreamRequest first = new StreamRequest("https://one.example/live|Referer=https%3A%2F%2Fone.example", null);
        StreamRequest second = new StreamRequest("https://two.example/live", null);
        assertEquals("https://one.example", first.headers.get("Referer"));
        assertTrue(second.headers.isEmpty());
    }

    @Test public void hlsDetectionUsesPathNotQueryOrChannelCategory() {
        assertTrue(StreamRequest.isHls("https://cdn.example/LIVE.M3U8?token=1"));
        assertTrue(StreamRequest.isHls("https://cdn.example/live.m3u"));
        assertFalse(StreamRequest.isHls("https://cdn.example/live.ts"));
        assertFalse(StreamRequest.isHls("https://cdn.example/live.mp4?next=file.m3u8"));
        assertFalse(StreamRequest.isHls("https://cdn.example/live"));
    }
}
