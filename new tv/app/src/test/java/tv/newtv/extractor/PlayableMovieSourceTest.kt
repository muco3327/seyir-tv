package tv.newtv.extractor

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class PlayableMovieSourceTest {
    @Test fun rejectsHtmlDisguisedAsVideoAndTriesNextUrl() = runBlocking {
        val visited = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val path = chain.request().url.encodedPath
            visited.add(path)
            val body = if (path == "/bad.mp4") "<html>Video unavailable</html>" else "0000ftypisom"
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body.toResponseBody()).build()
        }.build()
        val result = PlayableMovieSource(client).resolve(listOf(
            "https://example.com/bad.mp4", "https://example.com/good.mp4",
            "https://example.com/unneeded.mp4"
        ))
        assertEquals("https://example.com/good.mp4", result?.url)
        assertEquals(listOf("/bad.mp4", "/good.mp4"), visited)
    }
}
