package tv.seyir.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class ChannelFilterTest {
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
