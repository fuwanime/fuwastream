package com.fuwanime.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.flow.Flow

@JsonClass(generateAdapter = true)
@Entity(tableName = "manga_favorite")
data class MangaFavoriteEntity(
    @PrimaryKey val mangaId: String,
    val title: String,
    val coverUrl: String?,
    val addedAt: Long,
)

/** One row per chapter the user has opened; the newest row per manga drives "Lanjut baca". */
@JsonClass(generateAdapter = true)
@Entity(tableName = "manga_history", indices = [Index("mangaId"), Index("updatedAt")])
data class MangaHistoryEntity(
    @PrimaryKey val chapterId: String,
    val mangaId: String,
    val mangaTitle: String,
    val coverUrl: String?,
    val chapterLabel: String,
    val page: Int,
    val pageCount: Int,
    val updatedAt: Long,
)

@Dao
interface MangaFavoriteDao {
    @Query("SELECT * FROM manga_favorite ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<MangaFavoriteEntity>>

    @Query("SELECT * FROM manga_favorite ORDER BY addedAt DESC")
    suspend fun all(): List<MangaFavoriteEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM manga_favorite WHERE mangaId = :mangaId)")
    fun observeIsFavorite(mangaId: String): Flow<Boolean>

    @Upsert
    suspend fun upsert(entity: MangaFavoriteEntity)

    @Upsert
    suspend fun upsertAll(entities: List<MangaFavoriteEntity>)

    @Query("DELETE FROM manga_favorite WHERE mangaId = :mangaId")
    suspend fun delete(mangaId: String)

    @Query("DELETE FROM manga_favorite")
    suspend fun clear()
}

@Dao
interface MangaHistoryDao {
    @Query("SELECT * FROM manga_history")
    fun observeAll(): Flow<List<MangaHistoryEntity>>

    @Query("SELECT * FROM manga_history WHERE mangaId = :mangaId ORDER BY updatedAt DESC")
    fun observeFor(mangaId: String): Flow<List<MangaHistoryEntity>>

    @Query("SELECT * FROM manga_history")
    suspend fun all(): List<MangaHistoryEntity>

    @Upsert
    suspend fun upsert(entity: MangaHistoryEntity)

    @Upsert
    suspend fun upsertAll(entities: List<MangaHistoryEntity>)

    @Query("DELETE FROM manga_history WHERE mangaId = :mangaId")
    suspend fun deleteManga(mangaId: String)

    @Query("DELETE FROM manga_history")
    suspend fun clear()
}
