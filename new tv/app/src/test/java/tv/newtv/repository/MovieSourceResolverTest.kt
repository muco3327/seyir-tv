package tv.newtv.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import tv.newtv.data.models.*
import tv.newtv.scraper.MovieScraper

class MovieSourceResolverTest {
    private class Provider(override val name: String, val results: List<MovieItem>, val embeds: List<String>) : MovieScraper {
        var searches = 0
        var opens = 0
        override suspend fun search(query: String, page: Int): List<MovieItem> { searches++; return results }
        override suspend fun getCategories(page: Int) = emptyList<VodCategory<MovieItem>>()
        override suspend fun getMovieDetail(movieUrl: String): MovieDetail {
            opens++
            return MovieDetail("28 Yıl Sonra", "", "", embeds, provider = name)
        }
    }

    @Test fun searchesNextProviderWhenSelectedHasOnlyTrailerAndStopsOnSuccess() = runBlocking {
        val selected = MovieItem("28 Yıl Sonra (2025) Filmi Türkçe Dublaj 1080p Full HD", "https://a.example/film", "", provider = "A")
        val next = MovieItem("28 Yıl Sonra", "https://b.example/film", "", provider = "B")
        val a = Provider("A", emptyList(), listOf("https://youtube.com/embed/trailer"))
        val b = Provider("B", listOf(next), listOf("https://b.example/video.mp4"))
        val c = Provider("C", emptyList(), emptyList())
        val result = MovieSourceResolver(listOf(a,b,c), { emptyList() }) { urls ->
            urls.firstOrNull()?.let { VideoSource(it) }
        }.find(selected)
        assertEquals("B", result?.detail?.provider)
        assertEquals(1, b.searches)
        assertEquals(0, c.searches)
        assertEquals(0, c.opens)
    }

    @Test fun brokenVideoContinuesToNextProviderUsingOldCatalogTitles() = runBlocking {
        val first = MovieItem("28 Yıl Sonra", "https://a.example/film", "", provider = "A")
        val other = first.copy(title = "28 Yıl Sonra (2025) Full HD izle", url = "https://b.example/film", provider = "B")
        val a = Provider("A", emptyList(), listOf("https://a.example/broken"))
        val b = Provider("B", emptyList(), listOf("https://b.example/video.mp4"))
        val result = MovieSourceResolver(listOf(a,b), { listOf(other) }) { urls ->
            urls.firstOrNull()?.takeIf { it.contains("b.example") }?.let { VideoSource(it) }
        }.find(first)
        assertEquals("B", result?.detail?.provider)
        assertEquals(0, b.searches)
    }

    @Test fun doesNotMatchSequelOrDifferentReleaseYear() {
        val original = MovieItem("28 Yıl Sonra", "", "", year = "2025")
        assertFalse(MovieSourceResolver.sameMovie(original, original.copy(title = "28 Yıl Sonra Kemik Tapınağı")))
        assertFalse(MovieSourceResolver.sameMovie(original, original.copy(year = "2026")))
        assertTrue(MovieSourceResolver.sameMovie(original, original.copy(title = "28 Yıl Sonra (2025) Türkçe Dublaj 1080p Full HD")))
    }
}
