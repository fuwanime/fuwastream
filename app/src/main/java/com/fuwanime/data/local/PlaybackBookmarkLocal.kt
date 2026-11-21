package com.fuwanime.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.flow.Flow

/**
 * A "momen menarik" the user bookmarked while watching: the exact playback position, an optional
 * short label, and enough artwork to render the moment offline in a list.
 */
@JsonClass(generateAdapter = true)
@Entity(tableName = "playback_bookmarks", indices = [Index("contentId"), Index("episodeId")])
data class PlaybackBookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "ANIME" (player) or "MANGA", same vocabulary as [ContentPreferenceEntity]. */
    val mediaType: String,
    val contentId: String,
    val episodeId: String,
    val positionMs: Long,
    val label: String = "",
    val coverUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface PlaybackBookmarkDao {
    @Query("SELECT * FROM playback_bookmarks WHERE episodeId = :episodeId ORDER BY positionMs ASC")
    fun observeForEpisode(episodeId: String): Flow<List<PlaybackBookmarkEntity>>

    @Query("SELECT * FROM playback_bookmarks WHERE mediaType = :mediaType AND contentId = :contentId ORDER BY createdAt DESC")
    fun observeForTitle(mediaType: String, contentId: String): Flow<List<PlaybackBookmarkEntity>>

    @Query("SELECT * FROM playback_bookmarks")
    suspend fun all(): List<PlaybackBookmarkEntity>

    @Upsert
    suspend fun upsert(entity: PlaybackBookmarkEntity)

    @Query("DELETE FROM playback_bookmarks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM playback_bookmarks")
    suspend fun clear()
}
