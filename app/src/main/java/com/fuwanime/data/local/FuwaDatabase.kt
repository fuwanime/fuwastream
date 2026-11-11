package com.fuwanime.data.local

import com.squareup.moshi.JsonClass
import android.content.Context
import com.fuwanime.data.QuestEntity
import com.fuwanime.data.QuestDao
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
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
    entities = [FavoriteEntity::class, WatchHistoryEntity::class, SearchHistoryEntity::class, DownloadEntity::class, WatchDailyEntity::class, AnimeLinkEntity::class, MangaFavoriteEntity::class, MangaHistoryEntity::class, MangaDownloadEntity::class, ProfileEntity::class, AccountEntity::class, QuestEntity::class],
    version = 10,
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
    abstract fun profileDao(): ProfileDao
    abstract fun accountDao(): AccountDao
    abstract fun questDao(): QuestDao

    companion object {
        fun create(context: Context): FuwaDatabase =
            Room.databaseBuilder(context, FuwaDatabase::class.java, "fuwanime.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .build()

        /** v8 adds the local profile (name, bio, avatar, banner, XP and rank tag). */
        internal val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `profile` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `bio` TEXT NOT NULL, " +
                        "`avatarPath` TEXT NOT NULL, `bannerPath` TEXT NOT NULL, `totalXp` INTEGER NOT NULL, " +
                        "`highestTagIndex` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }

        /** v10 adds borders + quest counters to profile and the quest board table. */
        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `profile` ADD COLUMN `ownedBorders` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `profile` ADD COLUMN `equippedBorder` TEXT NOT NULL DEFAULT 'none'")
                db.execSQL("ALTER TABLE `profile` ADD COLUMN `qEpisodes` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `profile` ADD COLUMN `qChapters` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `profile` ADD COLUMN `qFavorites` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `profile` ADD COLUMN `qSearches` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `profile` ADD COLUMN `qDownloads` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `profile` ADD COLUMN `qMinutes` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `quests` (`id` TEXT NOT NULL, `templateId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                        "`target` INTEGER NOT NULL, `progress` INTEGER NOT NULL, `xpReward` INTEGER NOT NULL, `borderReward` TEXT NOT NULL, " +
                        "`cycleStart` INTEGER NOT NULL, `done` INTEGER NOT NULL, `claimed` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }

        /** v9 adds the device-local account (login/register without a server). */
        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `account` (`id` INTEGER NOT NULL, `email` TEXT NOT NULL, `displayName` TEXT NOT NULL, " +
                        "`passwordHash` TEXT NOT NULL, `salt` TEXT NOT NULL, `provider` TEXT NOT NULL, `loggedIn` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }

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

        /** v7 lets a downloaded chapter live as a CBZ in the user's folder. */
        internal val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `manga_downloads` ADD COLUMN `fileUri` TEXT")
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
