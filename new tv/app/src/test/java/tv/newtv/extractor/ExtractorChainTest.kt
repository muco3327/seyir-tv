package tv.newtv.extractor

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ExtractorChainTest {
    @Test
    fun legacyTrailerHistoryDoesNotTriggerAnExtractionRequest() = runBlocking {
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor {
            requests++
            error("Trailer should not be requested")
        }.build()
        val source = ExtractorChain(client).resolveSource(listOf(
            "https://www.youtube.com/embed/trailer", "//youtu.be/trailer"
        ))
        assertNull(source)
        assertEquals(0, requests)
    }

    @Test
    fun cancelledResolutionDoesNotTryTheNextSource() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            started.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body("<html></html>".toResponseBody()).build()
        }.build()
        val job = launch(kotlinx.coroutines.Dispatchers.IO) {
            ExtractorChain(client).resolveSource(listOf(
                "https://embed.example/one", "https://embed.example/two"
            ))
            fail("Cancelled resolution must not return a result")
        }
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            job.cancel()
        } finally {
            release.countDown()
        }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(1, requests)
    }

    @Test
    fun firstResolvedSourceDoesNotRequestRemainingEmbeds() = runBlocking {
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor {
            requests++
            error("Remaining embed should not be requested")
        }.build()
        val source = ExtractorChain(client).resolveSource(
            listOf("https://media.example/first.mp4", "https://embed.example/second")
        )
        assertEquals("https://media.example/first.mp4", source?.url)
        assertEquals(0, requests)
    }

    @Test
    fun laterSourcesOnSameHostAreNotDiscarded() = runBlocking {
        val visited = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            visited.add(chain.request().url.encodedPath)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body("<html></html>".toResponseBody()).build()
        }.build()
        val source = ExtractorChain(client).resolveSource(listOf(
            "https://media.example/one", "https://media.example/two",
            "https://media.example/three.mp4"
        ))
        assertEquals(listOf("/one", "/two"), visited)
        assertEquals("https://media.example/three.mp4", source?.url)
    }

    @Test
    fun normalizesAndDeduplicatesWithoutLosingSameHostAlternatives() = runBlocking {
        val sources = ExtractorChain(OkHttpClient()).resolveAllSources(listOf(
            "  //media.example/one.mp4  ", "https://media.example/one.mp4",
            "", "https://media.example/two.mp4", "https://media.example/three.mp4"
        ))
        assertEquals(3, sources.size)
        assertEquals("https://media.example/one.mp4", sources.first().url)
    }
}
