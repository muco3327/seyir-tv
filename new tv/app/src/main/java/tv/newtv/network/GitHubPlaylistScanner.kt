package tv.newtv.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import tv.newtv.data.local.IptvSettingsManager
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

data class DiscoveredPlaylist(
    val repo: String,
    val fileName: String,
    val url: String,
    val lastUpdated: String,
    val estimatedChannels: Int
)

class GitHubPlaylistScanner(
    private val client: OkHttpClient = OkHttpClientProvider.getUnsafeOkHttpClient()
) {
    companion object {
        private val SEARCH_QUERIES = listOf(
            "iptv turkey m3u",
            "turkish iptv m3u",
            "turkiye iptv m3u",
            "iptv spor m3u",
            "iptv bein m3u"
        )

        private val RELEVANT_KEYWORDS = listOf(
            "tr", "turk", "turkey", "turkiye", "ulusal", "kanallar",
            "playlist", "channels", "tv", "live", "spor", "sport", "bein"
        )

        private val TURKISH_CHANNEL_MARKERS = listOf(
            "trt", "atv", "kanal d", "show tv", "star tv", "tv8",
            "haber", "spor", "sinema", "belgesel", "turk", "tr:",
            "bein", "ssport", "exxen", "tivibu"
        )

        private val EXCLUDE_KEYWORDS = listOf("adult", "xxx", "nsfw")
    }

    private val fastCheckClient by lazy {
        client.newBuilder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    /**
     * GitHub Search API'sini tarayarak son 180 gün içinde güncellenmiş
     * çalışan Türk IPTV m3u listelerini keşfeder.
     */
    suspend fun scanPlaylists(
        onProgress: (current: Int, total: Int, message: String) -> Unit = { _, _, _ -> }
    ): List<DiscoveredPlaylist> = withContext(Dispatchers.IO) {
        val discovered = mutableListOf<DiscoveredPlaylist>()
        val seenRepos = mutableSetOf<String>()
        val seenUrls = mutableSetOf<String>()

        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -180)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sinceDate = dateFormat.format(cal.time)

        val totalQueries = SEARCH_QUERIES.size

        for ((qIndex, q) in SEARCH_QUERIES.withIndex()) {
            val encodedQuery = URLEncoder.encode("$q pushed:>$sinceDate", "UTF-8")
            val searchUrl = "https://api.github.com/search/repositories?q=$encodedQuery&sort=updated&order=desc&per_page=5"

            onProgress(qIndex + 1, totalQueries, "GitHub taranıyor: $q...")

            val repos = fetchGithubSearchRepos(searchUrl)
            for (repo in repos) {
                val repoFullName = repo.optString("full_name")
                if (repoFullName.isBlank() || seenRepos.contains(repoFullName)) continue
                seenRepos.add(repoFullName)

                val branch = repo.optString("default_branch").ifBlank { "main" }
                val pushedAt = repo.optString("pushed_at").take(10)

                // Git tree'sini recursive olarak tara
                val treeUrl = "https://api.github.com/repos/$repoFullName/git/trees/$branch?recursive=1"
                val treeFiles = fetchGithubTree(treeUrl)

                for (fileItem in treeFiles) {
                    val path = fileItem.optString("path", "")
                    val pathLower = path.lowercase()
                    val size = fileItem.optLong("size", 0L)

                    if ((pathLower.endsWith(".m3u") || pathLower.endsWith(".m3u8")) && size > 1024L) {
                        if (EXCLUDE_KEYWORDS.any { pathLower.contains(it) }) continue
                        if (!RELEVANT_KEYWORDS.any { pathLower.contains(it) }) continue

                        val rawUrl = "https://raw.githubusercontent.com/$repoFullName/$branch/$path"
                        if (seenUrls.contains(rawUrl)) continue
                        seenUrls.add(rawUrl)

                        val fileName = path.split("/").lastOrNull() ?: path

                        // Canlı doğrulama (ilk 8KB veriyi çekip test et)
                        val validation = validateM3uUrl(rawUrl)
                        if (validation.first) {
                            val item = DiscoveredPlaylist(
                                repo = repoFullName,
                                fileName = fileName,
                                url = rawUrl,
                                lastUpdated = pushedAt,
                                estimatedChannels = validation.second
                            )
                            discovered.add(item)
                        }
                    }
                }
            }
        }

        discovered
    }

    private fun fetchGithubSearchRepos(url: String): List<JSONObject> {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val bodyStr = response.body?.string() ?: return emptyList()
                val json = JSONObject(bodyStr)
                val items = json.optJSONArray("items") ?: return emptyList()
                val list = mutableListOf<JSONObject>()
                for (i in 0 until items.length()) {
                    items.optJSONObject(i)?.let { list.add(it) }
                }
                list
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun fetchGithubTree(url: String): List<JSONObject> {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val bodyStr = response.body?.string() ?: return emptyList()
                val json = JSONObject(bodyStr)
                val tree = json.optJSONArray("tree") ?: return emptyList()
                val list = mutableListOf<JSONObject>()
                for (i in 0 until tree.length()) {
                    tree.optJSONObject(i)?.let { list.add(it) }
                }
                list
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun validateM3uUrl(rawUrl: String): Pair<Boolean, Int> {
        return try {
            val request = Request.Builder()
                .url(rawUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Range", "bytes=0-8192")
                .build()

            fastCheckClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) return Pair(false, 0)
                val chunk = response.body?.string()?.lowercase() ?: return Pair(false, 0)

                if (!chunk.contains("#extm3u") && !chunk.contains("#extinf")) {
                    return Pair(false, 0)
                }

                val hasTr = TURKISH_CHANNEL_MARKERS.any { chunk.contains(it) }
                if (!hasTr && !rawUrl.lowercase().contains("tr")) {
                    return Pair(false, 0)
                }

                val extinfCount = chunk.split("#extinf").size - 1
                val estimatedCount = (extinfCount * 8).coerceAtLeast(10)
                Pair(true, estimatedCount)
            }
        } catch (_: Throwable) {
            Pair(false, 0)
        }
    }
}
