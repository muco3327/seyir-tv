package tv.newtv.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uParserTest {

    @Test
    fun `canonical key removes country and quality labels`() {
        assertEquals("bein sports 1", M3uParser.getCanonicalKey("[TR] beIN Sport 1 FHD (50fps)"))
    }

    @Test
    fun `channel name retains meaningful title while removing country prefix`() {
        assertEquals("TRT 1 HD", M3uParser.cleanChannelName("TR: TRT 1 HD"))
    }

    @Test
    fun `sports channel is categorised`() {
        assertEquals("Spor Kanalları", M3uParser.determineCategory("S Sport 2 HD", null))
    }

    @Test
    fun `foreign and entertainment channels are excluded`() {
        assertNull(M3uParser.determineCategory("ESPN Sports", "us"))
        assertNull(M3uParser.determineCategory("Movie Channel HD", "Movies"))
    }
}
