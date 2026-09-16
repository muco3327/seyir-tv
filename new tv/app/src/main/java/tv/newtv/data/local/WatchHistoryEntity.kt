package tv.newtv.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import tv.newtv.ui.viewmodel.PlayerMediaType

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val contentKey: String,
    val title: String,
    val posterUrl: String,
    val iframeUrlsJson: String,
    val isMovie: Boolean,
    val selectedLanguage: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
) {
    fun iframeUrls(): List<String> = runCatching {
        val array = JSONArray(iframeUrlsJson)
        List(array.length()) { index -> array.optString(index) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    companion object {
        fun contentKey(title: String, mediaType: PlayerMediaType): String =
            "${mediaType.name}:${title.trim().lowercase()}"
    }
}
