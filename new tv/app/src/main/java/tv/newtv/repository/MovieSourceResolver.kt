package tv.newtv.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import tv.newtv.data.models.*
import tv.newtv.scraper.MovieScraper
import tv.newtv.utils.VodSourceUrls
import java.text.Normalizer
import java.util.Locale

/** Sağlayıcıları sırayla açar; ilk çözülen videoda aramayı bitirir. */
class MovieSourceResolver(
    private val providers: List<MovieScraper>,
    private val cachedMovies: suspend () -> List<MovieItem>,
    private val resolveVideo: suspend (List<String>) -> VideoSource?
) {
    data class Result(val detail: MovieDetail, val source: VideoSource)

    suspend fun find(item: MovieItem, preferDubbing: Boolean? = null): Result? {
        val cached = cachedMovies().filter { sameMovie(item, it) }
        val ordered = providers.sortedBy { if (it.name.equals(item.provider, true)) 0 else 1 }
        val visited = mutableSetOf<String>()
        for (provider in ordered) {
            suspend fun tryItems(items: List<MovieItem>): Result? {
                for (candidate in items) {
                    if (!visited.add(candidate.url)) continue
                    val result = attempt {
                        val detail = provider.getMovieDetail(candidate.url)
                        val urls = when (preferDubbing) {
                            true -> detail.dubbingIframes.ifEmpty { if (detail.subtitleIframes.isEmpty()) detail.iframes else emptyList() }
                            false -> detail.subtitleIframes.ifEmpty { if (detail.dubbingIframes.isEmpty()) detail.iframes else emptyList() }
                            null -> detail.iframes + detail.dubbingIframes + detail.subtitleIframes
                        }
                        val source = resolveVideo(VodSourceUrls.playable(urls)) ?: return@attempt null
                        Result(detail.copy(sourceUrl = candidate.url, provider = provider.name), source)
                    }
                    if (result != null) return result
                }
                return null
            }
            val local = (if (provider.name.equals(item.provider, true)) listOf(item) else emptyList()) +
                cached.filter { it.provider.equals(provider.name, true) }
            tryItems(local)?.let { return it }
            // Katalogda eşleşme yoksa veya kayıtlar çalışmıyorsa bu sağlayıcıda canlı ara.
            val found = attempt { provider.search(searchTitle(item.title)) }.orEmpty()
                .filter { sameMovie(item, it) }
            tryItems(found)?.let { return it }
        }
        return null
    }

    private suspend fun <T> attempt(block: suspend () -> T?): T? = try {
        withTimeoutOrNull(60_000) { block() }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    companion object {
        fun searchTitle(title: String): String = title
            .replace(Regex("\\((?:19|20)\\d{2}\\)|\\s+(?:19|20)\\d{2}\\b"), " ")
            .replace(Regex("(?i)\\b(?:izle|türkçe|turkce|dublaj|altyazılı|altyazili|filmi|full|hd|[0-9]{3,4}p)\\b"), " ")
            .replace(Regex("[()\\[\\]]"), " ")
            .replace(Regex("\\s+"), " ").trim()

        private fun key(title: String): String = Normalizer.normalize(searchTitle(title).lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace('ı', 'i')
            .replace(Regex("[^a-z0-9]+"), " ").trim()

        fun sameMovie(a: MovieItem, b: MovieItem): Boolean {
            fun year(item: MovieItem) = item.year.toIntOrNull()
                ?: Regex("\\((19\\d{2}|20\\d{2})\\)").find(item.title)?.groupValues?.get(1)?.toIntOrNull()
            val ay = year(a)
            val by = year(b)
            return key(a.title).isNotBlank() && key(a.title) == key(b.title) && (ay == null || by == null || ay == by)
        }
    }
}
