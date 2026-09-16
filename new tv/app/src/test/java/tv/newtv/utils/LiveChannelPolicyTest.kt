package tv.newtv.utils

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LiveChannelPolicyTest {
    @Test fun acceptsSportsAndStandardNationalNewsOnly() {
        listOf("beIN Sports 1", "beIN Sports 5", "beIN Sports MAX 2", "S Sport 2", "TRT Spor Yıldız", "Tivibu Spor 4",
            "ATV", "Kanal D", "NOW", "Haber Global", "CNN Türk", "Sözcü TV").forEach {
            assertNotNull(it, LiveChannelPolicy.resolve(it))
        }
        listOf("beIN Movies 1", "beIN Series", "Random Spor", "ESPN", "Movie Channel HD", "TRT Çocuk",
            "TRT Müzik", "Yabancı TV", "Yerel Haber", "beIN Sports 99", "ATV Avrupa", "CNN International").forEach {
            assertNull(it, LiveChannelPolicy.resolve(it))
        }
        assertNull(LiveChannelPolicy.resolve("beIN Sports 1", "fr"))
    }

    @Test fun aliasesAndQualityVariantsHaveOneIdentity() {
        assertEquals(LiveChannelPolicy.resolve("beIN Sports 1"), LiveChannelPolicy.resolve("[TR] beIN Sport 1 FHD (50fps)"))
        assertEquals(LiveChannelPolicy.resolve("beIN Sports 1"), LiveChannelPolicy.resolve("beinsport1"))
        assertEquals("beIN Sports 1 UHD", LiveChannelPolicy.resolve("beIN Sports 1 2160p")?.name)
        assertEquals("beIN Sports 1 UHD", LiveChannelPolicy.resolve("beIN SPORTS 4K")?.name)
        assertNotEquals(LiveChannelPolicy.resolve("beIN Sports 1")?.key, LiveChannelPolicy.resolve("beIN Sports 1 UHD")?.key)
        assertEquals(LiveChannelPolicy.resolve("NOW"), LiveChannelPolicy.resolve("FOX TV HD"))
        assertEquals(LiveChannelPolicy.resolve("CNN Türk"), LiveChannelPolicy.resolve("CNN TURK HD"))
    }

    @Test fun githubPlaylistCannotBypassAllowlist() = runBlocking {
        val fixture = """
            #EXTM3U
            #EXTINF:-1 group-title="Spor",beIN Sport 1 FHD
            https://example.com/bein.m3u8
            #EXTINF:-1 group-title="Ulusal Kanallar",Movie Channel HD
            https://example.com/movie.m3u8
            #EXTINF:-1 group-title="Spor",Random Spor
            https://example.com/random.m3u8
            #EXTINF:-1 group-title="Haber",Haber Global HD
            https://example.com/news.m3u8
        """.trimIndent()
        val channels = M3uParser.parse(fixture.byteInputStream(), "https://raw.githubusercontent.com/test/list.m3u").toList()
        assertEquals(listOf("beIN Sports 1", "Haber Global"), channels.map { it.name })
    }
}
