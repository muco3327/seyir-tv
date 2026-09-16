package tv.newtv.extractor

import tv.newtv.data.models.VideoSource

interface VideoExtractor {
    val host: String
    fun canHandle(url: String): Boolean = host.split(",").any { url.contains(it.trim(), ignoreCase = true) }
    suspend fun extract(url: String): VideoSource?
}

