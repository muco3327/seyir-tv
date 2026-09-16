package tv.newtv.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ChannelEntity::class, WatchHistoryEntity::class, VodCatalogEntity::class], version = 7, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun vodCatalogDao(): VodCatalogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tv_database"
                )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS watch_history (" +
                        "contentKey TEXT NOT NULL, title TEXT NOT NULL, iframeUrlsJson TEXT NOT NULL, " +
                        "isMovie INTEGER NOT NULL, selectedLanguage TEXT NOT NULL, positionMs INTEGER NOT NULL, " +
                        "durationMs INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(contentKey))"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE watch_history ADD COLUMN posterUrl TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS vod_catalog (catalogKey TEXT NOT NULL, title TEXT NOT NULL, normalizedTitle TEXT NOT NULL, url TEXT NOT NULL, posterUrl TEXT NOT NULL, provider TEXT NOT NULL, isMovie INTEGER NOT NULL, languagesJson TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(catalogKey))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vod_catalog_isMovie ON vod_catalog(isMovie)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vod_catalog_normalizedTitle ON vod_catalog(normalizedTitle)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_vod_catalog_provider_isMovie ON vod_catalog(provider, isMovie)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vod_catalog ADD COLUMN year TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE vod_catalog ADD COLUMN rating TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
