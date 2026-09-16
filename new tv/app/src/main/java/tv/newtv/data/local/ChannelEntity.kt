package tv.newtv.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val logoUrl: String?,
    val groupTitle: String?,
    val streamUrl: String,
    val sourceListUrl: String, // Hangi M3U listesinden geldiğini veya MULTI_SOURCE tutar
    val canonicalKey: String = "",
    val streamUrlsJson: String = "",
    val sourceCount: Int = 1,
    val isFavorite: Boolean = false,
    var status: ChannelStatus = ChannelStatus.UNKNOWN
) {
    fun isSportsChannel(): Boolean = tv.newtv.utils.LiveChannelPolicy.isSports(name, groupTitle)

    fun getStreamUrls(): List<String> {
        if (streamUrlsJson.isBlank()) {
            return if (streamUrl.isNotBlank()) listOf(streamUrl) else emptyList()
        }
        return try {
            val array = JSONArray(streamUrlsJson)
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val url = array.optString(i)?.trim()
                if (!url.isNullOrEmpty() && !list.contains(url)) {
                    list.add(url)
                }
            }
            if (list.isEmpty() && streamUrl.isNotBlank()) listOf(streamUrl) else list
        } catch (e: Exception) {
            if (streamUrl.isNotBlank()) listOf(streamUrl) else emptyList()
        }
    }
}

enum class ChannelStatus {
    ACTIVE, OFFLINE, UNKNOWN
}

