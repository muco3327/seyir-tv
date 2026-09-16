package tv.newtv.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import tv.newtv.data.local.VodCatalogDao
import tv.newtv.data.local.VodCatalogEntity
import tv.newtv.data.models.MovieItem
import tv.newtv.data.models.SeriesItem
import tv.newtv.scraper.MovieScraper
import tv.newtv.scraper.SeriesScraper
import tv.newtv.scraper.VodProviderManager
import java.text.Normalizer
import java.util.Locale

class VodCatalogEngine(
    private val dao: VodCatalogDao,
    private val providers: VodProviderManager
) {
    private val providerConcurrency = Semaphore(2)

    suspend fun scanMovies(quickSync: Boolean = true, onProgress: suspend (String, Int) -> Unit = { _, _ -> }) = supervisorScope {
        providers.movieScrapers.map { scraper ->
            async(Dispatchers.IO) { providerConcurrency.withPermit { scanMovieProvider(scraper, quickSync, onProgress) } }
        }.awaitAll()
    }

    suspend fun scanSeries(quickSync: Boolean = true, onProgress: suspend (String, Int) -> Unit = { _, _ -> }) = supervisorScope {
        providers.seriesScrapers.map { scraper ->
            async(Dispatchers.IO) { providerConcurrency.withPermit { scanSeriesProvider(scraper, quickSync, onProgress) } }
        }.awaitAll()
    }

    suspend fun movies(): List<MovieItem> = withContext(Dispatchers.IO) {
        dao.deleteLegacyMovieUrls()
        deduplicate(dao.getCatalog(true)).mapNotNull {
            if (it.url.contains(".now/", ignoreCase = true)) null
            else {
                val yr = if (it.year.isNotBlank()) it.year else Regex("""\b(19\d\d|20\d\d)\b""").find(it.url)?.value ?: ""
                MovieItem(it.title, it.url, it.posterUrl, year = yr, rating = it.rating, provider = it.provider, languages = decodeLanguages(it.languagesJson))
            }
        }
    }

    suspend fun saveMovies(items: List<MovieItem>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val entities = items.mapIndexed { idx, it -> it.toEntity(it.provider.ifBlank { "HDFilmcehennemi" }, now - idx) }
        dao.upsert(entities)
    }

    suspend fun series(): List<SeriesItem> = withContext(Dispatchers.IO) {
        deduplicate(dao.getCatalog(false)).map {
            val yr = if (it.year.isNotBlank()) it.year else Regex("""\b(19\d\d|20\d\d)\b""").find(it.url)?.value ?: ""
            SeriesItem(it.title, it.url, it.posterUrl, year = yr, rating = it.rating, provider = it.provider, languages = decodeLanguages(it.languagesJson))
        }
    }

    suspend fun movieCandidates(): List<MovieItem> = withContext(Dispatchers.IO) {
        dao.getCatalog(true).map {
            val yr = if (it.year.isNotBlank()) it.year else Regex("""\b(19\d\d|20\d\d)\b""").find(it.url)?.value ?: ""
            MovieItem(it.title, it.url, it.posterUrl, year = yr, rating = it.rating, provider = it.provider, languages = decodeLanguages(it.languagesJson))
        }
    }

    suspend fun searchMovies(query: String): List<MovieItem> = withContext(Dispatchers.IO) {
        deduplicate(dao.getCatalog(true).filter { matchesQuery(it.title, query) }).map {
            val yr = if (it.year.isNotBlank()) it.year else Regex("""\b(19\d\d|20\d\d)\b""").find(it.url)?.value ?: ""
            MovieItem(it.title, it.url, it.posterUrl, year = yr, rating = it.rating, provider = it.provider, languages = decodeLanguages(it.languagesJson))
        }.sortedBy { relevanceRank(it.title, query) }
    }

    suspend fun searchSeries(query: String): List<SeriesItem> = withContext(Dispatchers.IO) {
        deduplicate(dao.getCatalog(false).filter { matchesQuery(it.title, query) }).map {
            val yr = if (it.year.isNotBlank()) it.year else Regex("""\b(19\d\d|20\d\d)\b""").find(it.url)?.value ?: ""
            SeriesItem(it.title, it.url, it.posterUrl, year = yr, rating = it.rating, provider = it.provider, languages = decodeLanguages(it.languagesJson))
        }.sortedBy { relevanceRank(it.title, query) }
    }

    suspend fun movieSources(title: String): List<MovieItem> = movieCandidates().filter {
        MovieSourceResolver.sameMovie(MovieItem(title, "", ""), it)
    }

    suspend fun seriesSources(title: String): List<SeriesItem> = withContext(Dispatchers.IO) {
        dao.getSourcesForTitle(false, normalizeTitle(title)).map {
            val yr = if (it.year.isNotBlank()) it.year else Regex("""\b(19\d\d|20\d\d)\b""").find(it.url)?.value ?: ""
            SeriesItem(it.title, it.url, it.posterUrl, year = yr, rating = it.rating, provider = it.provider, languages = decodeLanguages(it.languagesJson))
        }
    }

    private suspend fun scanMovieProvider(scraper: MovieScraper, quickSync: Boolean, onProgress: suspend (String, Int) -> Unit) {
        val scanStartedAt = System.currentTimeMillis()
        dao.deleteLegacyMovieUrls()
        val seenUrls = mutableSetOf<String>()
        var saved = 0
        var emptyOrRepeatedPages = 0
        val maxPages = if (quickSync) 5 else 20
        for (page in 1..maxPages) {
            val items = runCatching { scraper.getCategories(page).flatMap { it.items } }.getOrDefault(emptyList())
            val fresh = items.filter { it.url.isNotBlank() && seenUrls.add(it.url) }
            if (fresh.isEmpty()) {
                emptyOrRepeatedPages++
                if (emptyOrRepeatedPages >= END_CONFIRMATION_PAGES) {
                    break
                }
                delay(PAGE_DELAY_MS)
                continue
            }
            emptyOrRepeatedPages = 0
            val pageBaseTime = scanStartedAt - (page * 10000L)
            dao.upsert(fresh.mapIndexed { idx, it -> it.toEntity(scraper.name, pageBaseTime - idx) })
            saved += fresh.size
            onProgress(scraper.name, saved)
            if (quickSync && page >= 3 && saved >= 60) {
                break
            }
            delay(PAGE_DELAY_MS)
        }
    }

    private suspend fun scanSeriesProvider(scraper: SeriesScraper, quickSync: Boolean, onProgress: suspend (String, Int) -> Unit) {
        val scanStartedAt = System.currentTimeMillis()
        val seenUrls = mutableSetOf<String>()
        var saved = 0
        var emptyOrRepeatedPages = 0
        val maxPages = if (quickSync) 15 else 35
        for (page in 1..maxPages) {
            val items = runCatching { scraper.getCategories(page).flatMap { it.items } }.getOrDefault(emptyList())
            val fresh = items.filter { it.url.isNotBlank() && seenUrls.add(it.url) }
            if (fresh.isEmpty()) {
                emptyOrRepeatedPages++
                if (emptyOrRepeatedPages >= END_CONFIRMATION_PAGES) {
                    break
                }
                delay(PAGE_DELAY_MS)
                continue
            }
            emptyOrRepeatedPages = 0
            val pageBaseTime = scanStartedAt - (page * 10000L)
            dao.upsert(fresh.mapIndexed { idx, it -> it.toEntity(scraper.name, pageBaseTime - idx) })
            saved += fresh.size
            onProgress(scraper.name, saved)
            if (quickSync && page >= 10 && saved >= 350) {
                break
            }
            delay(PAGE_DELAY_MS)
        }
    }

    private fun MovieItem.toEntity(fallbackProvider: String, timestamp: Long): VodCatalogEntity {
        val sourceProvider = provider.ifBlank { fallbackProvider }
        return VodCatalogEntity(
            catalogKey = "M|$sourceProvider|$url",
            title = title.trim(),
            normalizedTitle = normalizeTitle(title),
            url = url,
            posterUrl = posterUrl,
            provider = sourceProvider,
            isMovie = true,
            year = year,
            rating = rating,
            languagesJson = JSONArray(languages).toString(),
            updatedAt = timestamp
        )
    }

    private fun SeriesItem.toEntity(fallbackProvider: String, timestamp: Long): VodCatalogEntity {
        val sourceProvider = provider.ifBlank { fallbackProvider }
        return VodCatalogEntity(
            catalogKey = "S|$sourceProvider|$url",
            title = title.trim(),
            normalizedTitle = normalizeTitle(title),
            url = url,
            posterUrl = posterUrl,
            provider = sourceProvider,
            isMovie = false,
            year = year,
            rating = rating,
            languagesJson = JSONArray(languages).toString(),
            updatedAt = timestamp
        )
    }

    private fun deduplicate(items: List<VodCatalogEntity>): List<VodCatalogEntity> = items
        .groupBy { it.normalizedTitle.ifBlank { it.url.lowercase(Locale.ROOT) } }
        .values
        .map { matches -> matches.maxWith(compareBy<VodCatalogEntity> { it.posterUrl.isNotBlank() }.thenBy { it.updatedAt }) }
        .sortedByDescending { it.updatedAt }

    private fun decodeLanguages(raw: String): List<String> = runCatching {
        val array = JSONArray(raw)
        List(array.length()) { array.optString(it) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    companion object {
        const val PAGE_SIZE = 50
        // Diziwatch arşivi sayfa başına 10 kayıtla 300'den fazla sayfa sunuyor.
        // Tarama boş/tekrar eden ilk sayfada zaten otomatik durur.
        private const val MAX_SOURCE_PAGES = 900
        private const val END_CONFIRMATION_PAGES = 2
        private const val PAGE_DELAY_MS = 180L

        fun normalizeTitle(value: String): String {
            val decomposed = Normalizer.normalize(value.lowercase(Locale("tr", "TR")), Normalizer.Form.NFD)
            return decomposed.replace(Regex("\\p{M}+"), "")
                .replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ")
                .replace(Regex("\\b(izle|turkce|dublaj|altyazili|full|hd|film|dizi)\\b"), " ")
                .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
                .trim()
        }

        fun matchesQuery(title: String, query: String): Boolean {
            val normalizedTitle = normalizeTitle(title)
            val normalizedQuery = normalizeTitle(query)
            if (normalizedQuery.isBlank()) return false
            return normalizedQuery.split(' ').filter { it.isNotBlank() }.all { token ->
                normalizedTitle.split(' ').any { word -> word == token || word.startsWith(token) } ||
                    normalizedTitle.contains(token)
            }
        }

        fun relevanceRank(title: String, query: String): String {
            val normalizedTitle = normalizeTitle(title)
            val normalizedQuery = normalizeTitle(query)
            val bucket = when {
                normalizedTitle == normalizedQuery -> 0
                normalizedTitle.startsWith("$normalizedQuery ") -> 1
                normalizedTitle.contains(" $normalizedQuery ") || normalizedTitle.endsWith(" $normalizedQuery") -> 2
                else -> 3
            }
            return "$bucket|${normalizedTitle.length.toString().padStart(5, '0')}|$normalizedTitle"
        }
    }
}
