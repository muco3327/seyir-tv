package tv.newtv.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(
    tableName = "vod_catalog",
    indices = [Index("isMovie"), Index("normalizedTitle"), Index(value = ["provider", "isMovie"])]
)
data class VodCatalogEntity(
    @PrimaryKey val catalogKey: String,
    val title: String,
    val normalizedTitle: String,
    val url: String,
    val posterUrl: String,
    val provider: String,
    val isMovie: Boolean,
    val year: String = "",
    val rating: String = "",
    val languagesJson: String = "[]",
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface VodCatalogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<VodCatalogEntity>)

    @Query("SELECT * FROM vod_catalog WHERE isMovie = :isMovie ORDER BY updatedAt DESC")
    suspend fun getCatalog(isMovie: Boolean): List<VodCatalogEntity>

    @Query("SELECT * FROM vod_catalog WHERE isMovie = :isMovie AND normalizedTitle = :normalizedTitle ORDER BY updatedAt DESC")
    suspend fun getSourcesForTitle(isMovie: Boolean, normalizedTitle: String): List<VodCatalogEntity>

    @Query("DELETE FROM vod_catalog WHERE provider = :provider AND isMovie = :isMovie AND updatedAt < :scanStartedAt")
    suspend fun deleteStale(provider: String, isMovie: Boolean, scanStartedAt: Long)

    @Query("DELETE FROM vod_catalog WHERE isMovie = 1 AND url NOT LIKE '%hdfilmcehennemi.nl%'")
    suspend fun deleteLegacyMovieUrls()
}
