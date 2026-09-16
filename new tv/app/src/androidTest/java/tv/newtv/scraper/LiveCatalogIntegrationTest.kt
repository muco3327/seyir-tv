package tv.newtv.scraper

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tv.newtv.network.OkHttpClientProvider
import tv.newtv.data.local.AppDatabase
import tv.newtv.repository.VodCatalogEngine
import tv.newtv.extractor.ExtractorChain

@RunWith(AndroidJUnit4::class)
class LiveCatalogIntegrationTest {
    private val client = OkHttpClientProvider.getScraperOkHttpClient()

    @Test
    fun allConfiguredCatalogsReturnItemsOnAndroid() = runBlocking {
        val results = linkedMapOf(
            "FullHDFilmİzlesene" to FullhdfilmizleseneScraper(client).getCategories(),
            "HDFilmCehennemi" to HdfilmcehennemiScraper(client).getCategories(),
            "Diziwatch" to DiziwatchScraper(client).getCategories(),
            "Dizigom" to DizigomScraper(client).getCategories()
        )

        val counts = results.mapValues { (_, categories) -> categories.sumOf { it.items.size } }
        assertTrue("Boş kataloglar: $counts", counts.values.all { it > 0 })
    }

    @Test
    fun catalogEnginePersistsAndReturnsUniqueTitles() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = AppDatabase.getDatabase(context)
        val manager = VodProviderManager(client)
        val engine = VodCatalogEngine(database.vodCatalogDao(), manager)

        engine.scanMovies()
        engine.scanSeries()
        val movies = engine.movies()
        val series = engine.series()

        assertTrue("Film kataloğu boş", movies.isNotEmpty())
        assertTrue("Dizi kataloğu boş", series.isNotEmpty())
        assertTrue(
            "Film kataloğunda tekrar var",
            movies.map { VodCatalogEngine.normalizeTitle(it.title) }.distinct().size == movies.size
        )
        assertTrue(
            "Dizi kataloğunda tekrar var",
            series.map { VodCatalogEngine.normalizeTitle(it.title) }.distinct().size == series.size
        )
    }

    @Test
    fun archivePaginationReturnsDifferentContentOnAndroid() = runBlocking {
        val hdf = HdfilmcehennemiScraper(client)
        val hdfPage1 = hdf.getCategories(1).flatMap { it.items }.map { it.url }.toSet()
        val hdfPage2 = hdf.getCategories(2).flatMap { it.items }.map { it.url }.toSet()
        assertTrue("HDFilmCehennemi arşivinin ilk sayfası boş", hdfPage1.isNotEmpty())
        assertTrue("HDFilmCehennemi AJAX ikinci sayfası boş veya ilk sayfayla aynı", hdfPage2.isNotEmpty() && hdfPage2 != hdfPage1)

        val diziwatch = DiziwatchScraper(client)
        val seriesPage1 = diziwatch.getCategories(1).flatMap { it.items }.map { it.url }.toSet()
        val seriesPage2 = diziwatch.getCategories(2).flatMap { it.items }.map { it.url }.toSet()
        assertTrue("Diziwatch arşivinin ilk sayfası boş", seriesPage1.isNotEmpty())
        assertTrue("Diziwatch ikinci sayfası boş veya ilk sayfayla aynı", seriesPage2.isNotEmpty() && seriesPage2 != seriesPage1)
    }

    @Test
    fun availableMovieProviderResolvesToPlayableMediaOnAndroid() = runBlocking {
        val manager = VodProviderManager(client)
        val extractor = ExtractorChain(client)
        val diagnostics = mutableListOf<String>()
        var playable = false
        for (scraper in manager.movieScrapers) {
            val movies = runCatching { scraper.getCategories().flatMap { it.items }.take(6) }
                .getOrElse { emptyList() }
            for (movie in movies) {
                val detail = runCatching { scraper.getMovieDetail(movie.url) }.getOrNull() ?: continue
                val realEmbeds = (detail.iframes + detail.dubbingIframes + detail.subtitleIframes)
                    .filterNot { it.contains("vr_set=1") }.distinct()
                diagnostics += "${scraper.name}:${realEmbeds.mapNotNull { runCatching { java.net.URI(it).host }.getOrNull() }}"
                if (realEmbeds.isNotEmpty() && extractor.resolveAllSources(realEmbeds).isNotEmpty()) {
                    playable = true
                    break
                }
            }
            if (playable) break
        }
        assertTrue("Hiçbir film sağlayıcısı oynatılabilir medya çözemedi: $diagnostics", playable)
    }
}
