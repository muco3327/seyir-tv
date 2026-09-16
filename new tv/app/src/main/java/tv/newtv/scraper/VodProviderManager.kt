package tv.newtv.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import tv.newtv.data.models.MovieItem
import tv.newtv.data.models.SeriesItem
import tv.newtv.network.OkHttpClientProvider

class VodProviderManager(private val client: OkHttpClient = OkHttpClientProvider.getScraperOkHttpClient()) {

    private val searchSemaphore = Semaphore(1)

    private data class SearchOutcome<T>(
        val provider: String,
        val items: List<T>,
        val failed: Boolean
    )

    val hdfilmcehennemi = HdfilmcehennemiScraper(client)
    val filmModu = FilmModuScraper(client)
    val jetfilm = JetfilmScraper(client)
    val fullHdFilm = FullhdfilmizleseneScraper(client)

    val dizilla = DizillaScraper(client)
    val sezonlukDizi = SezonlukDiziScraper(client)
    val diziwatch = DiziwatchScraper(client)
    val dizigom = DizigomScraper(client)

    val movieScrapers: List<MovieScraper> = listOf(hdfilmcehennemi)
    val seriesScrapers: List<SeriesScraper> = listOf(diziwatch, dizigom, dizilla, sezonlukDizi)

    fun getMovieScraper(name: String?): MovieScraper {
        return hdfilmcehennemi
    }

    fun getSeriesScraper(name: String?): SeriesScraper {
        return seriesScrapers.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: diziwatch
    }

    fun getMovieScraperForUrl(url: String): MovieScraper {
        return hdfilmcehennemi
    }

    fun getSeriesScraperForUrl(url: String): SeriesScraper {
        return when {
            url.contains("sezonlukdizi", ignoreCase = true) -> sezonlukDizi
            url.contains("dizilla", ignoreCase = true) -> dizilla
            url.contains("diziwatch", ignoreCase = true) -> diziwatch
            url.contains("dizigom", ignoreCase = true) -> dizigom
            else -> diziwatch
        }
    }

    /**
     * Cross-Provider Arama: Tüm aktif film kaynaklarında paralel arama yapar ve sonuçları birleştirir.
     */
    data class ProviderSearchResult<T>(
        val items: List<T>,
        val unavailableProviders: List<String>
    )

    suspend fun searchAllMovies(query: String): ProviderSearchResult<MovieItem> = withContext(Dispatchers.IO) {
        coroutineScope {
            val deferreds = movieScrapers.map { scraper ->
                async {
                    try {
                        SearchOutcome(scraper.name, searchSemaphore.withPermit { scraper.search(query) }, failed = false)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.w(TAG, "Movie search failed for ${scraper.name}", error)
                        SearchOutcome(scraper.name, emptyList(), failed = true)
                    }
                }
            }
            val resultsByProvider = deferreds.awaitAll()
            val unavailableProviders = resultsByProvider.filter { it.failed }.map { it.provider }
            val results = resultsByProvider.flatMap { it.items }
            val seen = mutableSetOf<String>()
            val uniqueResults = mutableListOf<MovieItem>()
            for (item in results) {
                // Başlık ve sağlayıcı kombinasyonu ile mükerrer kontrolü
                val key = "${item.title.lowercase()}_${item.provider}"
                if (seen.add(key)) {
                    uniqueResults.add(item)
                }
            }
            ProviderSearchResult(uniqueResults, unavailableProviders)
        }
    }

    /**
     * Cross-Provider Arama: Tüm aktif dizi kaynaklarında paralel arama yapar ve sonuçları birleştirir.
     */
    suspend fun searchAllSeries(query: String): ProviderSearchResult<SeriesItem> = withContext(Dispatchers.IO) {
        coroutineScope {
            val deferreds = seriesScrapers.map { scraper ->
                async {
                    try {
                        SearchOutcome(scraper.name, searchSemaphore.withPermit { scraper.search(query) }, failed = false)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.w(TAG, "Series search failed for ${scraper.name}", error)
                        SearchOutcome(scraper.name, emptyList(), failed = true)
                    }
                }
            }
            val resultsByProvider = deferreds.awaitAll()
            val unavailableProviders = resultsByProvider.filter { it.failed }.map { it.provider }
            val results = resultsByProvider.flatMap { it.items }
            val seen = mutableSetOf<String>()
            val uniqueResults = mutableListOf<SeriesItem>()
            for (item in results) {
                val key = "${item.title.lowercase()}_${item.provider}"
                if (seen.add(key)) {
                    uniqueResults.add(item)
                }
            }
            ProviderSearchResult(uniqueResults, unavailableProviders)
        }
    }
    private companion object {
        const val TAG = "VodProviderManager"
    }
}
