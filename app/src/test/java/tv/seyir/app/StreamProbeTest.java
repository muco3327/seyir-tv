package tv.seyir.app;

import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public class StreamProbeTest {
    @Test public void recognizesM3uResponseFromExtensionlessEndpoint() throws Exception {
        assertEquals(StreamProbe.HLS, StreamProbe.detect("application/octet-stream", "#EXTM3U\n#EXT-X-TARGETDURATION:6\n"));
    }
    @Test public void recognizesHlsContentTypesAndBom() throws Exception {
        assertEquals(StreamProbe.HLS, StreamProbe.detect("application/vnd.apple.mpegurl", ""));
        assertEquals(StreamProbe.HLS, StreamProbe.detect(null, "\uFEFF#EXTM3U\n"));
    }
    @Test public void leavesTransportStreamAndMp4ToExtractors() throws Exception {
        assertNull(StreamProbe.detect("video/mp2t", "G"));
        assertNull(StreamProbe.detect("video/mp4", "ftyp"));
    }
    @Test(expected = IOException.class) public void rejectsHtmlEvenWhenServerLabelsItHls() throws Exception {
        StreamProbe.detect("application/x-mpegurl", "<!DOCTYPE html><html>Blocked</html>");
    }
    @Test(expected = IOException.class) public void reportsUnsupportedDash() throws Exception {
        StreamProbe.detect("application/dash+xml", "<MPD>");
    }
    @Test(expected = IOException.class) public void rejectsJsonError() throws Exception {
        StreamProbe.detect("application/json", "{\"error\":\"offline\"}");
    }
}
