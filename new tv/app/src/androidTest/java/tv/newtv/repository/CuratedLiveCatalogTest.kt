package tv.newtv.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import tv.newtv.data.local.AppDatabase
import tv.newtv.data.local.ChannelEntity

@RunWith(AndroidJUnit4::class)
class CuratedLiveCatalogTest {
    @Test fun prunesLegacyRowsAndPersistsAllowedGithubAdditionsWithoutLosingFavorites() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        try {
            val dao = database.channelDao()
            dao.insertChannels(listOf(
                ChannelEntity(41, "beIN Sport 1 HD", null, "Spor Kanalları", "https://example.com/original", "old", isFavorite = true),
                ChannelEntity(42, "Random TV", null, "Ulusal Kanallar", "https://example.com/random", "old")
            ))
            val playlist = """
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
                    .code(200).message("OK").body(playlist.toResponseBody()).build()
            }.build()
            val repository = IptvRepository(dao, client)
            repository.pruneCatalog()
            assertEquals(listOf(41), dao.getAllChannels().first().map { it.id })
            repository.syncInitialPlaylists(listOf("https://example.com/github.m3u"))
            repository.syncInitialPlaylists(listOf("https://example.com/github.m3u"))
            val rows = dao.getAllChannels().first()
            assertEquals(2, rows.size)
            val bein = rows.single { it.name == "beIN Sports 1" }
            assertEquals(41, bein.id)
            assertTrue(bein.isFavorite)
            assertEquals(setOf("https://example.com/original", "https://example.com/backup"), bein.getStreamUrls().toSet())
            assertTrue(rows.any { it.name == "Haber Global" })
        } finally {
            database.close()
        }
    }
}
