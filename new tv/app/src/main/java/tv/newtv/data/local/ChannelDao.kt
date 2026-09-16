package tv.newtv.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Query("SELECT * FROM channels")
    fun getAllChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE sourceListUrl = :sourceUrl")
    fun getChannelsBySource(sourceUrl: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE groupTitle = :category")
    fun getChannelsByCategory(category: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE groupTitle = 'Spor Kanalları' OR name LIKE '%bein%' OR name LIKE '%spor%'")
    fun getAllSportsChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT DISTINCT sourceListUrl FROM channels WHERE sourceListUrl IS NOT NULL ORDER BY sourceListUrl ASC")
    fun getSources(): Flow<List<String>>

    @Query("SELECT DISTINCT groupTitle FROM channels WHERE sourceListUrl = :sourceUrl AND groupTitle IS NOT NULL ORDER BY groupTitle ASC")
    fun getCategoriesBySource(sourceUrl: String): Flow<List<String>>

    @Query("SELECT * FROM channels WHERE sourceListUrl = :sourceUrl AND groupTitle = :category")
    fun getChannelsBySourceAndCategory(sourceUrl: String, category: String): Flow<List<ChannelEntity>>

    @Query("SELECT DISTINCT groupTitle FROM channels WHERE groupTitle IS NOT NULL ORDER BY groupTitle ASC")
    fun getCategories(): Flow<List<String>>

    @Query("SELECT * FROM channels WHERE isFavorite = 1")
    fun getFavoriteChannels(): Flow<List<ChannelEntity>>

    @Query("UPDATE channels SET isFavorite = :isFavorite WHERE id = :channelId")
    suspend fun updateFavoriteStatus(channelId: Int, isFavorite: Boolean)

    @Query("UPDATE channels SET status = :status WHERE id = :channelId")
    suspend fun updateChannelStatus(channelId: Int, status: ChannelStatus)

    @Query("UPDATE channels SET streamUrl = :streamUrl WHERE id = :channelId")
    suspend fun updateStreamUrl(channelId: Int, streamUrl: String)

    @Query("SELECT canonicalKey FROM channels WHERE isFavorite = 1 AND canonicalKey != ''")
    suspend fun getFavoriteCanonicalKeys(): List<String>

    @Query("SELECT * FROM channels WHERE (name LIKE '%trt%' OR groupTitle = 'Ulusal Kanallar' OR groupTitle = 'Ulusal' OR (groupTitle != 'Spor Kanalları' AND name NOT LIKE '%bein%' AND name NOT LIKE '%spor%')) AND name NOT LIKE '%kurd%' AND name NOT LIKE '%kürt%' AND name NOT LIKE '%kurt%'")
    fun getAllNationalChannels(): Flow<List<ChannelEntity>>

    @Query("DELETE FROM channels WHERE name LIKE '%kurd%' OR name LIKE '%kürt%' OR name LIKE '%kurt%' OR groupTitle LIKE '%kurd%' OR groupTitle LIKE '%kürt%'")
    suspend fun deleteKurdiChannels()

    @Query("DELETE FROM channels WHERE sourceListUrl = :sourceUrl")
    suspend fun deleteChannelsBySource(sourceUrl: String)

    @Query("DELETE FROM channels")
    suspend fun deleteAllChannels()

    @Transaction
    suspend fun replaceAllChannels(channels: List<ChannelEntity>) {
        deleteAllChannels()
        channels.chunked(400).forEach { insertChannels(it) }
    }
}

@Dao
interface WatchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WatchHistoryEntity)

    @Query("SELECT * FROM watch_history ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 30): Flow<List<WatchHistoryEntity>>

    @Query("DELETE FROM watch_history WHERE contentKey = :contentKey")
    suspend fun delete(contentKey: String)

    @Query("DELETE FROM watch_history")
    suspend fun clear()
}
