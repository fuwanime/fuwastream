package com.fuwanime.data.local

import com.squareup.moshi.JsonClass
import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@JsonClass(generateAdapter = true)
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val animeId: String,
    val title: String,
    val poster: String,
    val cover: String,
    val type: String,
    val status: String,
    val year: String,
    val genres: String,
    val addedAt: Long,
    @ColumnInfo(defaultValue = "'PLANNED'") val collectionStatus: String = CollectionStatus.PLANNED.name,
)

/** One row per episode the user has started; the newest row per anime drives "Lanjutkan Nonton". */
@JsonClass(generateAdapter = true)
@Entity(tableName = "watch_history", indices = [Index("animeId"), Index("updatedAt")])
data class WatchHistoryEntity(
    @PrimaryKey val episodeId: String,
    val animeId: String,
    val animeTitle: String,
    val animePoster: String,
    val animeCover: String,
    val episodeNumber: String,
    val episodeTitle: String,
    val episodeThumbnail: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val isFinished: Boolean get() = durationMs > 0 && positionMs >= durationMs * FINISHED_THRESHOLD

    companion object {
        const val FINISHED_THRESHOLD = 0.92
    }
}

@JsonClass(generateAdapter = true)
@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long,
)

/** An episode saved for offline viewing via the system DownloadManager. */
@Entity(tableName = "downloads", indices = [Index("animeId")])
data class DownloadEntity(
    @PrimaryKey val episodeId: String,
    val animeId: String,
    val animeTitle: String,
    val animePoster: String,
    val episodeNumber: String,
    val episodeTitle: String,
    val episodeThumbnail: String,
    val quality: String,
    val systemId: Long,
    val filePath: String,
    val createdAt: Long,
)

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    suspend fun allFavorites(): List<FavoriteEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE animeId = :animeId)")
    fun observeIsFavorite(animeId: String): Flow<Boolean>

    @Upsert
    suspend fun upsert(entity: FavoriteEntity)

    @Upsert
    suspend fun upsertAll(entities: List<FavoriteEntity>)

    @Query("DELETE FROM favorites WHERE animeId = :animeId")
    suspend fun delete(animeId: String)

    @Query("DELETE FROM favorites")
    suspend fun clear()
}

@Dao
interface WatchHistoryDao {
    @Query(
        """
        SELECT * FROM watch_history h
        WHERE updatedAt = (SELECT MAX(updatedAt) FROM watch_history WHERE animeId = h.animeId)
        ORDER BY updatedAt DESC
        LIMIT :limit
        """,
    )
    fun observeLatestPerAnime(limit: Int): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history")
    fun observeAll(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE animeId = :animeId")
    fun observeForAnime(animeId: String): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE episodeId = :episodeId")
    suspend fun get(episodeId: String): WatchHistoryEntity?

    @Query("DELETE FROM watch_history WHERE episodeId = :episodeId")
    suspend fun deleteEpisode(episodeId: String)

    @Upsert
    suspend fun upsert(entity: WatchHistoryEntity)

    @Upsert
    suspend fun upsertAll(entities: List<WatchHistoryEntity>)

    @Query("SELECT * FROM watch_history")
    suspend fun all(): List<WatchHistoryEntity>

    @Query("DELETE FROM watch_history WHERE animeId = :animeId")
    suspend fun deleteAnime(animeId: String)

    @Query("DELETE FROM watch_history")
    suspend fun clear()
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 12")
    fun observeRecent(): Flow<List<SearchHistoryEntity>>

    @Upsert
    suspend fun upsert(entity: SearchHistoryEntity)

    @Upsert
    suspend fun upsertAll(entities: List<SearchHistoryEntity>)

    @Query("SELECT * FROM search_history")
    suspend fun all(): List<SearchHistoryEntity>

    @Query("DELETE FROM search_history WHERE `query` = :query")
    suspend fun delete(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clear()
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE episodeId = :episodeId")
    suspend fun get(episodeId: String): DownloadEntity?

    @Upsert
    suspend fun upsert(entity: DownloadEntity)

    @Query("DELETE FROM downloads WHERE episodeId = :episodeId")
    suspend fun delete(episodeId: String)

    @Query("SELECT * FROM downloads")
    suspend fun all(): List<DownloadEntity>

    @Query("DELETE FROM downloads")
    suspend fun clear()
}

@Database(
    entities = [FavoriteEntity::class, WatchHistoryEntity::class, SearchHistoryEntity::class, DownloadEntity::class, WatchDailyEntity::class, AnimeLinkEntity::class, MangaFavoriteEntity::class, MangaHistoryEntity::class, MangaDownloadEntity::class, ContentPreferenceEntity::class, SourceStatEntity::class, ProfileEntity::class, QuestEntity::class, CountedEntity::class, BorderEntity::class],
    version = 11,
    exportSchema = false,
)
abstract class FuwaDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun watchDailyDao(): WatchDailyDao
    abstract fun animeLinkDao(): AnimeLinkDao
    abstract fun mangaFavoriteDao(): MangaFavoriteDao
    abstract fun mangaHistoryDao(): MangaHistoryDao
    abstract fun mangaDownloadDao(): MangaDownloadDao
    abstract fun contentPreferenceDao(): ContentPreferenceDao
    abstract fun sourceStatDao(): SourceStatDao
    abstract fun profileDao(): ProfileDao

    companion object {
        fun create(context: Context): FuwaDatabase =
            Room.databaseBuilder(context, FuwaDatabase::class.java, "fuwanime.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                .build()

        /** v2 adds offline downloads; existing favourites and history are kept. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `downloads` (`episodeId` TEXT NOT NULL, `animeId` TEXT NOT NULL, " +
                        "`animeTitle` TEXT NOT NULL, `animePoster` TEXT NOT NULL, `episodeNumber` TEXT NOT NULL, " +
                        "`episodeTitle` TEXT NOT NULL, `episodeThumbnail` TEXT NOT NULL, `quality` TEXT NOT NULL, " +
                        "`systemId` INTEGER NOT NULL, `filePath` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`episodeId`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_animeId` ON `downloads` (`animeId`)")
            }
        }

        /** v3 adds daily watch time for statistics; nothing existing changes. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `watch_daily` (`day` INTEGER NOT NULL, `watchedMs` INTEGER NOT NULL, PRIMARY KEY(`day`))",
                )
            }
        }

        /** v4 adds the AniList/MAL link cache; nothing existing changes. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `anime_link` (`animeId` TEXT NOT NULL, `state` TEXT NOT NULL, `anilistId` INTEGER, " +
                        "`malId` INTEGER, `score` INTEGER, `tags` TEXT NOT NULL, `nextEpisode` INTEGER, `nextAiringAt` INTEGER, " +
                        "`fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`animeId`))",
                )
            }
        }

        /** v6 adds downloaded manga chapters; nothing existing changes. */
        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `manga_downloads` (`chapterId` TEXT NOT NULL, `mangaId` TEXT NOT NULL, `mangaTitle` TEXT NOT NULL, `coverUrl` TEXT, " +
                        "`chapterNumber` TEXT, `chapterLabel` TEXT NOT NULL, `language` TEXT NOT NULL, `pageCount` INTEGER NOT NULL, `pagesDone` INTEGER NOT NULL, " +
                        "`bytes` INTEGER NOT NULL, `state` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`chapterId`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_manga_downloads_mangaId` ON `manga_downloads` (`mangaId`)")
            }
        }

        /** v7: external CBZ storage for downloads, collection status/preferences, and source stats. */
        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (tableExists(db, "manga_downloads") && !columnExists(db, "manga_downloads", "fileUri")) {
                    db.execSQL("ALTER TABLE `manga_downloads` ADD COLUMN `fileUri` TEXT")
                }
                if (tableExists(db, "favorites") && !columnExists(db, "favorites", "collectionStatus")) {
                    db.execSQL("ALTER TABLE `favorites` ADD COLUMN `collectionStatus` TEXT NOT NULL DEFAULT 'PLANNED'")
                    if (tableExists(db, "watch_history")) {
                        db.execSQL("UPDATE `favorites` SET `collectionStatus` = 'IN_PROGRESS' WHERE animeId IN (SELECT DISTINCT animeId FROM watch_history)")
                    }
                }
                if (tableExists(db, "manga_favorite") && !columnExists(db, "manga_favorite", "collectionStatus")) {
                    db.execSQL("ALTER TABLE `manga_favorite` ADD COLUMN `collectionStatus` TEXT NOT NULL DEFAULT 'PLANNED'")
                    if (tableExists(db, "manga_history")) {
                        db.execSQL("UPDATE `manga_favorite` SET `collectionStatus` = 'IN_PROGRESS' WHERE mangaId IN (SELECT DISTINCT mangaId FROM manga_history)")
                    }
                }
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `content_preferences` (`key` TEXT NOT NULL, `mediaType` TEXT NOT NULL, `contentId` TEXT NOT NULL, " +
                        "`notifyMode` TEXT NOT NULL, `notifyLanguage` TEXT NOT NULL, `autoDownload` INTEGER NOT NULL, `autoDownloadLimit` INTEGER NOT NULL, " +
                        "`readerMode` TEXT, `hiddenFromContinue` INTEGER NOT NULL, `reminderMinutes` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`key`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `source_stats` (`sourceKey` TEXT NOT NULL, `successes` INTEGER NOT NULL, `failures` INTEGER NOT NULL, " +
                        "`startupTotalMs` INTEGER NOT NULL, `lastUsedAt` INTEGER NOT NULL, PRIMARY KEY(`sourceKey`))",
                )
            }
        }

        /**
         * v9 caches the manga an anime adapts (AniList SOURCE relation). It also re-runs the v7 steps, which are
         * idempotent: the fork's v2.2.0 reached v8 through a v7 that only added `fileUri`, so those installs lack
         * the collection columns and tables that upstream's v7 creates.
         */
        /** v10 caches the cast (characters and voice actors) with the AniList link; old rows get it on their next refresh. */
        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!columnExists(db, "anime_link", "cast")) {
                    db.execSQL("ALTER TABLE `anime_link` ADD COLUMN `cast` TEXT NOT NULL DEFAULT ''")
                }
            }
        }

        /** v11 adds the public AniList stats and per-episode thumbnails; old rows fill in on their next refresh. */
        internal val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!columnExists(db, "anime_link", "popularity")) db.execSQL("ALTER TABLE `anime_link` ADD COLUMN `popularity` INTEGER")
                if (!columnExists(db, "anime_link", "favourites")) db.execSQL("ALTER TABLE `anime_link` ADD COLUMN `favourites` INTEGER")
                if (!columnExists(db, "anime_link", "totalEpisodes")) db.execSQL("ALTER TABLE `anime_link` ADD COLUMN `totalEpisodes` INTEGER")
                if (!columnExists(db, "anime_link", "durationMinutes")) db.execSQL("ALTER TABLE `anime_link` ADD COLUMN `durationMinutes` INTEGER")
                if (!columnExists(db, "anime_link", "studio")) db.execSQL("ALTER TABLE `anime_link` ADD COLUMN `studio` TEXT")
                if (!columnExists(db, "anime_link", "year")) db.execSQL("ALTER TABLE `anime_link` ADD COLUMN `year` INTEGER")
                if (!columnExists(db, "anime_link", "episodeThumbnails")) {
                    db.execSQL("ALTER TABLE `anime_link` ADD COLUMN `episodeThumbnails` TEXT NOT NULL DEFAULT ''")
                }
                // Old rows have no stats/thumbnails yet; mark them stale so the first detail visit refreshes.
                db.execSQL("UPDATE `anime_link` SET `fetchedAt` = 0 WHERE `state` = 'LINKED'")
            }
        }

        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_6_7.migrate(db)
                if (!columnExists(db, "anime_link", "sourceTitles")) {
                    db.execSQL("ALTER TABLE `anime_link` ADD COLUMN `sourceTitles` TEXT NOT NULL DEFAULT ''")
                }
            }
        }

        /** v8 adds the local profile, quest progress, once-only counters and unlocked borders; nothing existing changes. */
        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `user_profile` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `xp` INTEGER NOT NULL, `equippedBorder` TEXT, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `quest_progress` (`id` TEXT NOT NULL, `progress` INTEGER NOT NULL, `claimed` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `counted_items` (`key` TEXT NOT NULL, PRIMARY KEY(`key`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `unlocked_borders` (`id` TEXT NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean =
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'").use { it.moveToFirst() }

        private fun columnExists(db: SupportSQLiteDatabase, table: String, column: String): Boolean =
            db.query("PRAGMA table_info(`$table`)").use { c ->
                while (c.moveToNext()) if (c.getString(1) == column) return true
                false
            }

        /** v5 adds manga favourites and reading history; nothing existing changes. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `manga_favorite` (`mangaId` TEXT NOT NULL, `title` TEXT NOT NULL, `coverUrl` TEXT, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`mangaId`))")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `manga_history` (`chapterId` TEXT NOT NULL, `mangaId` TEXT NOT NULL, `mangaTitle` TEXT NOT NULL, `coverUrl` TEXT, " +
                        "`chapterLabel` TEXT NOT NULL, `page` INTEGER NOT NULL, `pageCount` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`chapterId`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_manga_history_mangaId` ON `manga_history` (`mangaId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_manga_history_updatedAt` ON `manga_history` (`updatedAt`)")
            }
        }
    }
}
