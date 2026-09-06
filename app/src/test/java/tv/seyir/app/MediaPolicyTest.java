package tv.seyir.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class MediaPolicyTest {
    @Test public void onlyRealMediaPathsAreAccepted() {
        assertTrue(MediaPolicy.isVideo("https://cdn.example.org/master.m3u8?token=x"));
        assertTrue(MediaPolicy.isVideo("https://cdn.example.org/MOVIE.MP4"));
        assertFalse(MediaPolicy.isVideo("https://cdn.example.org/ad?next=movie.mp4"));
        assertFalse(MediaPolicy.isVideo("file:///movie.mp4"));
        assertFalse(MediaPolicy.isVideo("https://127.0.0.1/movie.mp4"));
        assertFalse(MediaPolicy.isVideo("https://localhost/movie.mp4"));
        assertFalse(MediaPolicy.isVideo("https://u:p@cdn.example.org/movie.mp4"));
    }
    @Test public void providerCannotEscapeItsOrigin() {
        assertTrue(Source.DIZILLA.owns("https://dizilla.now/dizi/test"));
        assertTrue(Source.FULLHD.owns("https://fullhdfilmizlesene.now/film/test/"));
        assertFalse(Source.DIZILLA.owns("https://dizilla.now.attacker.org/"));
        assertFalse(Source.DIZILLA.owns("https://attacker.org/?url=dizilla.now"));
        assertFalse(Source.DIZILLA.owns("javascript:alert(1)"));
    }
    @Test public void resumeKeyDoesNotDependOnExpiringTokens() {
        assertEquals(MediaPolicy.key("DIZILLA","https://dizilla.now/episode?token=a"),
            MediaPolicy.key("DIZILLA","https://dizilla.now/episode?token=b#player"));
    }
}
