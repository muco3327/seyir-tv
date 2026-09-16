package tv.newtv.scraper

import tv.newtv.data.models.EpisodeSources
import tv.newtv.data.models.MovieItem
import tv.newtv.data.models.MovieDetail
import tv.newtv.data.models.SeriesItem
import tv.newtv.data.models.SeriesDetail
import tv.newtv.data.models.VodCategory

interface MovieScraper {
    val name: String
    suspend fun search(query: String, page: Int = 1): List<MovieItem>
    suspend fun getRecent(page: Int = 1): List<MovieItem> = emptyList()
    suspend fun getCategories(page: Int = 1): List<VodCategory<MovieItem>>
    suspend fun getMovieDetail(movieUrl: String): MovieDetail
    suspend fun getEpisodeIframes(episodeUrl: String): List<String> = emptyList()
    suspend fun getEpisodeSources(episodeUrl: String): EpisodeSources {
        val list = getEpisodeIframes(episodeUrl)
        return EpisodeSources(iframes = list, dubbingIframes = list, subtitleIframes = list)
    }
}

interface SeriesScraper {
    val name: String
    suspend fun search(query: String, page: Int = 1): List<SeriesItem>
    suspend fun getRecent(page: Int = 1): List<SeriesItem> = emptyList()
    suspend fun getCategories(page: Int = 1): List<VodCategory<SeriesItem>>
    suspend fun getSeriesDetail(seriesUrl: String): SeriesDetail
    suspend fun getEpisodeIframes(episodeUrl: String): List<String> = emptyList()
    suspend fun getEpisodeSources(episodeUrl: String): EpisodeSources {
        val list = getEpisodeIframes(episodeUrl)
        return EpisodeSources(iframes = list, dubbingIframes = list, subtitleIframes = list)
    }
}
