package tv.seyir.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class ChannelQualityTest {
    @Test public void separatesUserChannelsFromLowerQualityMirrors() {
        for(int channel:new int[]{1,3}) {
            String key="beinsports"+channel;
            assertNotEquals(ChannelQuality.groupKey(key,"TR:BEINSPORTS "+channel+" UHD"),ChannelQuality.groupKey(key,"TR:BEINSPORTS "+channel+" HD (YEDEK)"));
            assertNotEquals(ChannelQuality.groupKey(key,"TR:BEINSPORTS "+channel+" UHD"),ChannelQuality.groupKey(key,"TR:BEINSPORTS "+channel+" SD (DUSUK KALITE)"));
        }
    }
    @Test public void preservesExplicitResolutionAndUnknownQuality() {
        assertEquals("2160p",ChannelQuality.tier("TRT 4K"));
        assertEquals("1080p",ChannelQuality.tier("STAR FHD"));
        assertEquals("720p",ChannelQuality.tier("STAR (720p)"));
        assertNotEquals(ChannelQuality.groupKey("star","STAR HD"),ChannelQuality.groupKey("star","STAR (720p)"));
        assertEquals("unknown",ChannelQuality.tier("STAR"));
    }
}
