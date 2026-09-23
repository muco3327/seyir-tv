package tv.seyir.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlaylistRouteTest {
    @Test public void refreshesEveryKnownRootSessionAtEntry() {
        PlaylistRoute route = new PlaylistRoute("https://example/channel");
        route.opened("https://example/channel", "https://cdn/session1/index.m3u8");
        assertEquals("https://example/channel", route.resolve("https://cdn/session1/index.m3u8"));
        route.opened("https://cdn/session1/index.m3u8", "https://cdn/session2/index.m3u8");
        assertEquals("https://example/channel", route.resolve("https://cdn/session2/index.m3u8"));
        assertEquals("https://example/channel", route.resolve("https://cdn/session1/index.m3u8"));
    }
    @Test public void preservesVariantsSegmentsAndKeysExactly() {
        PlaylistRoute route = new PlaylistRoute("https://example/master.m3u8");
        for (String url : new String[]{"https://cdn/720.m3u8", "https://cdn/part.ts?t=1", "https://cdn/key"}) {
            route.opened(url, "https://cdn/redirected");
            assertEquals(url, route.resolve(url));
            assertEquals("https://cdn/redirected", route.resolve("https://cdn/redirected"));
        }
    }
    @Test public void newChannelDoesNotInheritPreviousRedirects() {
        PlaylistRoute first = new PlaylistRoute("https://example/one");
        first.opened("https://example/one", "https://cdn/old.m3u8");
        PlaylistRoute second = new PlaylistRoute("https://example/two");
        assertEquals("https://cdn/old.m3u8", second.resolve("https://cdn/old.m3u8"));
    }
}
