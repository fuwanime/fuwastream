package com.fuwanime.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import kotlinx.coroutines.flow.Flow

enum class MangaDownloadState { QUEUED, RUNNING, DONE, FAILED }

/** One downloaded (or queued) chapter; its images live in [com.fuwanime.data.manga.chapterDir]. */
@Entity(tableName = "manga_downloads", indices = [Index("mangaId")])
data class MangaDownloadEntity(
    @PrimaryKey val chapterId: String,
    val mangaId: String,
    val mangaTitle: String,
    val coverUrl: String?,
    val chapterNumber: String?,
    val chapterLabel: String,
    val language: String,
    val pageCount: Int,
    val pagesDone: Int,
    val bytes: Long,
    /** [MangaDownloadState] name. */
    val state: String,
    val createdAt: Long,
)

@Dao
interface MangaDownloadDao {
    @Query("SELECT * FROM manga_downloads ORDER BY createdAt")
    fun observeAll(): Flow<List<MangaDownloadEntity>>

    @Query("SELECT * FROM manga_downloads WHERE mangaId = :mangaId")
    fun observeFor(mangaId: String): Flow<List<MangaDownloadEntity>>

    @Query("SELECT * FROM manga_downloads WHERE chapterId = :chapterId")
    suspend fun get(chapterId: String): MangaDownloadEntity?

    @Query("SELECT * FROM manga_downloads WHERE mangaId = :mangaId AND state = 'DONE'")
    suspend fun doneFor(mangaId: String): List<MangaDownloadEntity>

    @Query("SELECT * FROM manga_downloads WHERE mangaId = :mangaId")
    suspend fun allFor(mangaId: String): List<MangaDownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: MangaDownloadEntity)

    @Query("UPDATE manga_downloads SET pageCount = :pageCount, pagesDone = :pagesDone, bytes = :bytes WHERE chapterId = :chapterId")
    suspend fun progress(chapterId: String, pageCount: Int, pagesDone: Int, bytes: Long)

    @Query("UPDATE manga_downloads SET state = :state WHERE chapterId = :chapterId")
    suspend fun setState(chapterId: String, state: String)

    @Query("DELETE FROM manga_downloads WHERE chapterId = :chapterId")
    suspend fun delete(chapterId: String)

    @Query("DELETE FROM manga_downloads WHERE mangaId = :mangaId")
    suspend fun deleteFor(mangaId: String)
}
