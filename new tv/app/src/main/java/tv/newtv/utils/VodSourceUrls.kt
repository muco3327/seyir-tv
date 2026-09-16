package tv.newtv.utils

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object VodSourceUrls {
    private val trailerHosts = listOf("youtube.com", "youtube-nocookie.com", "youtu.be")

    fun isTrailer(url: String): Boolean {
        val host = normalize(url)?.toHttpUrlOrNull()?.host ?: return false
        return trailerHosts.any { host == it || host.endsWith(".$it") }
    }

    fun normalize(url: String): String? {
        val trimmed = url.trim()
        val absolute = if (trimmed.startsWith("//")) "https:$trimmed" else trimmed
        return absolute.toHttpUrlOrNull()?.toString()
    }

    fun playable(urls: List<String>): List<String> = urls.mapNotNull(::normalize)
        .filterNot { isTrailer(it) || it.toHttpUrlOrNull()?.queryParameter("vr_set") != null }
        .distinct()
}
