package com.fuwanime.data.local

import android.content.Context
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

    @Query("SELECT * FROM watch_history WHERE animeId = :animeId")
    fun observeForAnime(animeId: String): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE episodeId = :episodeId")
    suspend fun get(episodeId: String): WatchHistoryEntity?

    @Upsert
    suspend fun upsert(entity: WatchHistoryEntity)

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
    entities = [FavoriteEntity::class, WatchHistoryEntity::class, SearchHistoryEntity::class, DownloadEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class FuwaDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        fun create(context: Context): FuwaDatabase =
            Room.databaseBuilder(context, FuwaDatabase::class.java, "fuwanime.db")
                .addMigrations(MIGRATION_1_2)
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
    }
}
