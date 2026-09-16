package tv.newtv.data.local

object InitialData {
    // A. IPTV Canlı Yayın Kaynakları (Doğrulanmış ve Öncelikli Kaynaklar)
    val iptvSources = listOf(
        "https://raw.githubusercontent.com/kadirsener1/mahsun/main/playlist.m3u",
        "https://raw.githubusercontent.com/omerdenizhan/IPTV-M3U/refs/heads/main/m3u/turkiye.m3u",
        "https://raw.githubusercontent.com/omerdenizhan/IPTV-M3U/refs/heads/main/m3u/turkiye-iptv-org.m3u",
        "https://raw.githubusercontent.com/myiptv2/iptv-playlist/main/kanallar.m3u",
        "https://iptv-org.github.io/iptv/countries/tr.m3u",
        "https://raw.githubusercontent.com/Tahir2020/TR_Avrupa/main/playlist/playerlist.m3u"
    )

    // B. Film ve Dizi Kaynakları (Scraper Hedefleri)
    val scraperTargets = listOf(
        ScraperTarget("Diziwatch", "https://diziwatch.ac/", ScraperType.SERIES),
        ScraperTarget("Dizigom", "https://dizigomv1.com/", ScraperType.SERIES),
        ScraperTarget("SezonlukDizi", "https://sezonlukdizi2.com/", ScraperType.SERIES),
        ScraperTarget("Dizilla", "https://dizilla.club/", ScraperType.SERIES),
        ScraperTarget("FullHDFilmİzlesene", "https://fullhdfilmizlesene.co/", ScraperType.MOVIE),
        ScraperTarget("HDFilmCehennemi", "https://www.hdfilmcehennemi.nl/", ScraperType.MOVIE),
        ScraperTarget("FilmModu", "https://filmmodu.cc/", ScraperType.MOVIE),
        ScraperTarget("JetFilmizle", "https://jetfilmizle.top/", ScraperType.MOVIE)
    )
}

data class ScraperTarget(
    val name: String,
    val baseUrl: String,
    val type: ScraperType
)

enum class ScraperType {
    MOVIE, SERIES, MIXED
}
