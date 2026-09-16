package tv.newtv.data.models

data class MovieItem(
    val title: String,
    val url: String,
    val posterUrl: String,
    val year: String = "",
    val provider: String = "",
    val languages: List<String> = emptyList(), // Örn: ["TR Dublaj", "TR Altyazı"]
    val rating: String = ""
)

data class MovieDetail(
    val title: String,
    val description: String,
    val posterUrl: String,
    val iframes: List<String>,
    val isSeries: Boolean = false,
    val seasons: List<Season> = emptyList(),
    val provider: String = "",
    val languages: List<String> = emptyList(), // Örn: ["Türkçe Dublaj", "Türkçe Altyazı"]
    val dubbingIframes: List<String> = emptyList(),
    val subtitleIframes: List<String> = emptyList(),
    val hasDub: Boolean = false,
    val hasSubtitle: Boolean = false,
    val sourceUrl: String = "",
    val hasTrailerOnly: Boolean = false
)

data class SeriesItem(
    val title: String,
    val url: String,
    val posterUrl: String,
    val year: String = "",
    val rating: String = "",
    val provider: String = "",
    val languages: List<String> = emptyList()
)

data class SeriesDetail(
    val title: String,
    val description: String,
    val posterUrl: String,
    val seasons: List<Season>,
    val provider: String = "",
    val languages: List<String> = emptyList(),
    val hasDub: Boolean = false,
    val hasSubtitle: Boolean = false,
    val sourceUrl: String = ""
)

data class Season(
    val seasonNumber: Int,
    val name: String,
    val episodes: List<Episode>
)

data class Episode(
    val episodeNumber: Int,
    val name: String,
    val url: String,
    val languages: List<String> = emptyList(),
    val dubbingIframes: List<String> = emptyList(),
    val subtitleIframes: List<String> = emptyList(),
    val hasDub: Boolean = false,
    val hasSubtitle: Boolean = false
)

data class EpisodeSources(
    val iframes: List<String> = emptyList(),
    val dubbingIframes: List<String> = emptyList(),
    val subtitleIframes: List<String> = emptyList(),
    val hasDub: Boolean = false,
    val hasSubtitle: Boolean = false
)

data class VideoSource(
    val url: String,
    val quality: String = "Auto",
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<Subtitle> = emptyList(), // Harici VTT/SRT linklerini tutar
    val mimeType: String? = null,
    val serverName: String = "",
    val language: String = "", // Örn: "Türkçe Dublaj", "Türkçe Altyazı", "Orijinal"
    val timestamp: Long = System.currentTimeMillis() // Her oynatma tetiklendiğinde StateFlow emisyonunu garanti eder
)

data class Subtitle(
    val language: String,
    val url: String,
    val isDefault: Boolean = false
)
