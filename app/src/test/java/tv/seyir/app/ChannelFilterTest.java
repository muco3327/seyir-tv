package tv.seyir.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class ChannelFilterTest {
    @Test public void preservesRequestedProviderVariantsWithoutOpeningForeignFilter() {
        assertTrue(ChannelFilter.allows("BEIN SPORTS 1-ATOM", "Spor"));
        assertTrue(ChannelFilter.allows("BeIN Sport 1-ZEUS", "Spor"));
        assertTrue(ChannelFilter.allows("BeIN Sport 2 zeus", "Spor-Neon"));
        assertFalse(ChannelFilter.allows("FR: BEIN SPORTS 1-ATOM", "Spor"));
        assertFalse(ChannelFilter.allows("BEIN SPORTS 1-ATOM", "France"));
        assertFalse(ChannelFilter.allows("BEIN SPORTS 1-OTHER", "Spor"));
        assertNotEquals(ChannelQuality.groupKey("beinsports1", "BEIN SPORTS 1-ATOM"),
            ChannelQuality.groupKey("beinsports1", "BeIN Sport 1-ZEUS"));
        assertNotEquals(ChannelQuality.groupKey("beinsports1", "BEIN SPORTS 1-ATOM"),
            ChannelQuality.groupKey("beinsports1", "BEIN SPORTS 1"));
    }
    @Test public void rejectsUserExamples() {
        for(String name:new String[]{"TV4","Afroturk TV (1080p)","Aksu TV (720p)","Alanya Posta TV (1080p)","Altas TV (1080p)","Anadolu Net TV"}) assertFalse(name,ChannelFilter.allows(name,"General"));
    }
    @Test public void preservesNationalChannels() {
        for(String name:new String[]{"TRT 1 HD","TRT Spor Yıldız","Star TV (1080p)","Kanal D HD","TR:TV8.5 FHD","NOW TV HD","Show TV","ATV"}) assertTrue(name,ChannelFilter.allows(name,"General"));
    }
    @Test public void requiresTurkishEvidenceForBein() {
        assertTrue(ChannelFilter.allows("trbeinsports1","Sports"));
        assertTrue(ChannelFilter.allows("TR:BEINSPORTS 1 HQ (VODAFONE-TURKCELL-TURKIYE)","TR | BEIN SPORTS"));
        assertTrue(ChannelFilter.allows("beIN Sports 2","Turkey"));
        for(String name:new String[]{"frbeinsportsmax7","FR: beIN Sports 1","beIN Sports 1 English","beIN Sports 1"}) assertFalse(name,ChannelFilter.allows(name,"Sports"));
        assertFalse(ChannelFilter.allows("TR: beIN Sports 1","France"));
    }
    @Test public void cinemaAllowlistDoesNotAdmitUnrelatedGeneralChannels() {
        assertTrue(ChannelFilter.allows("Sinema TV HD","Movies"));
        assertTrue(ChannelFilter.allows("TR: beIN Movies Premiere 1 HD","Cinema"));
        assertFalse(ChannelFilter.allows("Random Cinema","General"));
        assertFalse(ChannelFilter.allows("FR: FilmBox HD","Movies"));
    }
}
