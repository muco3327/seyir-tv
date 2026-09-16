package tv.newtv.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import tv.newtv.data.local.ChannelDao
import tv.newtv.data.local.ChannelEntity
import java.lang.reflect.Proxy

class CuratedCatalogTest {
    @Test fun legacyCleanupAndRepeatedImportsPreserveIdentityAndFavorites() = runBlocking {
        val rows = MutableStateFlow(listOf(
            ChannelEntity(41, "beIN Sport 1 HD", null, "Spor Kanalları", "https://example.com/original", "old", isFavorite = true),
            ChannelEntity(42, "Random TV", null, "Ulusal Kanallar", "https://example.com/random", "old")
        ))
        var nextId = 43
        val dao = Proxy.newProxyInstance(ChannelDao::class.java.classLoader, arrayOf(ChannelDao::class.java)) { _, method, args ->
            when (method.name) {
                "getAllChannels" -> rows
                "replaceAllChannels" -> {
                    @Suppress("UNCHECKED_CAST")
                    val incoming = args[0] as List<ChannelEntity>
                    rows.value = incoming.map { if (it.id == 0) it.copy(id = nextId++) else it }
                    Unit
                }
                else -> error("Unexpected DAO call: ${method.name}")
            }
        } as ChannelDao
        val fixture = """
            #EXTM3U
            #EXTINF:-1,beIN Sports 1 FHD
            https://example.com/backup
            #EXTINF:-1,Haber Global
            https://example.com/news
            #EXTINF:-1,Movie Channel
            https://example.com/movie
        """.trimIndent()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(fixture.toResponseBody()).build()
        }.build()
        val repository = IptvRepository(dao, client)
        repository.pruneCatalog()
        assertEquals(listOf(41), rows.value.map { it.id })
        repeat(2) { repository.syncInitialPlaylists(listOf("https://example.com/github.m3u")) }
        assertEquals(2, rows.value.size)
        val bein = rows.value.single { it.name == "beIN Sports 1" }
        assertEquals(41, bein.id)
        assertTrue(bein.isFavorite)
        assertEquals(setOf("https://example.com/original", "https://example.com/backup"), bein.getStreamUrls().toSet())
        assertTrue(rows.value.any { it.name == "Haber Global" })
    }
}
