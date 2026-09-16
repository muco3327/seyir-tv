package tv.newtv.scraper

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.ByteString.Companion.encodeUtf8
import org.junit.Assert.*
import org.junit.Test

class FilmModuScraperTest {
    private fun scraper(html: String) = FilmModuScraper(
        OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(html.toResponseBody()).build()
        }.build()
    )

    @Test
    fun encodedTrailerIsNotAdvertisedAsFullMovieOrDubbedSource() = runBlocking {
        // 28 Yıl Sonra sayfasında gözlenen ilkpartkod + prt_fragman0 biçimi.
        val encoded = "<iframe rel=\"nofollow external\" src=\"https://www.youtube.com/embed/bf4BCKE4_ww\"></iframe>"
            .encodeUtf8().base64()
        val html = """<h1>28 Yıl Sonra</h1><script>
            var ilkpartkod = '$encoded';
            pdata['prt_fragman0'] = '${encoded.drop(11)}';
            </script>"""
        val detail = scraper(html).getMovieDetail("https://filmmodu.cc/film/28-yil-sonra-hd/")
        assertTrue(detail.hasTrailerOnly)
        assertTrue(detail.iframes.isEmpty())
        assertTrue(detail.dubbingIframes.isEmpty())
        assertTrue(detail.subtitleIframes.isEmpty())
        assertTrue(detail.languages.isEmpty())
        assertFalse(detail.hasDub)
        assertFalse(detail.hasSubtitle)
    }

    @Test
    fun keepsActualMovieEmbedWhenTrailerAlsoExists() = runBlocking {
        val trailer = "<iframe src='https://www.youtube-nocookie.com/embed/trailer'></iframe>".encodeUtf8().base64()
        val movie = "<iframe src='https://video.example/embed/movie'></iframe>".encodeUtf8().base64()
        val detail = scraper("""<script>
            var ilkpartkod = '$trailer';
            pdata['prt_dual'] = '${movie.drop(11)}';
            </script>""").getMovieDetail("https://filmmodu.cc/film/example/")
        assertFalse(detail.hasTrailerOnly)
        assertEquals(listOf("https://video.example/embed/movie"), detail.iframes)
        assertEquals(detail.iframes, detail.dubbingIframes)
    }
}
