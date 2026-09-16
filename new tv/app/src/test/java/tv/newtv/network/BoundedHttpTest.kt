package tv.newtv.network

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class BoundedHttpTest {
    @Test fun deadlineCancelsAnUnresponsiveServer() = runBlocking {
        // Initialise TLS and OkHttp before measuring the request deadline.
        val client = OkHttpClient()
        ServerSocket(0).use { server ->
            val closed = CountDownLatch(1)
            val serving = thread(isDaemon = true) {
                server.accept().use { socket ->
                    socket.soTimeout = 3000
                    try { while (socket.getInputStream().read() >= 0) { } } finally { closed.countDown() }
                }
            }
            val start = System.nanoTime()
            val result = withTimeoutOrNull(1000) {
                client.readBounded(Request.Builder().url("http://127.0.0.1:${server.localPort}/").build(), 512)
            }
            assertNull(result)
            assertTrue("Deadline waited on the network", (System.nanoTime() - start) / 1_000_000 < 2500)
            assertTrue("Cancellation did not close the connection", closed.await(2, TimeUnit.SECONDS))
            serving.join(1000)
        }
    }

    @Test fun responseSizeIsBoundedEvenForLiveStreams() = runBlocking {
        ServerSocket(0).use { server ->
            val serving = thread(isDaemon = true) {
                server.accept().use { socket ->
                    socket.soTimeout = 3000
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: 4096\r\n\r\n" + "a".repeat(4096)).toByteArray())
                    socket.getOutputStream().flush()
                }
            }
            val result = withTimeoutOrNull(2000) {
                OkHttpClient().readBounded(Request.Builder().url("http://127.0.0.1:${server.localPort}/").build(), 512)
            }
            assertNotNull(result)
            assertEquals(512, result!!.bytes.size)
            serving.join(1000)
        }
    }
}
