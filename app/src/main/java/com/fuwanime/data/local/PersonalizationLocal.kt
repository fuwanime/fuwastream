package com.fuwanime.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.flow.Flow

/** User-controlled state. The remote airing/publication status remains stored separately. */
enum class CollectionStatus {
    PLANNED,
    IN_PROGRESS,
    WAITING,
    PAUSED,
    COMPLETED,
    DROPPED,
}

enum class TitleNotifyMode { DEFAULT, ALL, FINAL_ONLY, OFF }
enum class ReaderMode { WEBTOON, SINGLE_PAGE, RIGHT_TO_LEFT }

/**
 * Preferences that belong to one title, including titles not currently in the collection.
 * A shared table keeps notification, download, reader and continue behavior consistent.
 */
@JsonClass(generateAdapter = true)
@Entity(tableName = "content_preferences")
data class ContentPreferenceEntity(
    @PrimaryKey val key: String,
    val mediaType: String,
    val contentId: String,
    val notifyMode: String = TitleNotifyMode.DEFAULT.name,
    val notifyLanguage: String = "APP",
    val autoDownload: Boolean = false,
    val autoDownloadLimit: Int = 3,
    val readerMode: String? = null,
    val hiddenFromContinue: Boolean = false,
    /** -1 = no pre-release reminder, 0 = at release, positive = minutes before release. */
    val reminderMinutes: Int = -1,
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        fun key(mediaType: String, contentId: String) = "$mediaType:$contentId"
        fun anime(id: String) = key("ANIME", id)
        fun manga(id: String) = key("MANGA", id)
    }
}

/** Local-only source reliability. No viewing telemetry leaves the device. */
@Entity(tableName = "source_stats")
data class SourceStatEntity(
    @PrimaryKey val sourceKey: String,
    val successes: Int = 0,
    val failures: Int = 0,
    val startupTotalMs: Long = 0,
    val lastUsedAt: Long = 0,
)

@Dao
interface ContentPreferenceDao {
    @Query("SELECT * FROM content_preferences")
    fun observeAll(): Flow<List<ContentPreferenceEntity>>

    @Query("SELECT * FROM content_preferences WHERE `key` = :key")
    fun observe(key: String): Flow<ContentPreferenceEntity?>

    @Query("SELECT * FROM content_preferences WHERE `key` = :key")
    suspend fun get(key: String): ContentPreferenceEntity?

    @Query("SELECT * FROM content_preferences")
    suspend fun all(): List<ContentPreferenceEntity>

    @Upsert
    suspend fun upsert(entity: ContentPreferenceEntity)

    @Upsert
    suspend fun upsertAll(entities: List<ContentPreferenceEntity>)

    @Query("DELETE FROM content_preferences")
    suspend fun clear()
}

@Dao
interface SourceStatDao {
    @Query("SELECT * FROM source_stats")
    fun observeAll(): Flow<List<SourceStatEntity>>

    @Query("SELECT * FROM source_stats WHERE sourceKey = :key")
    suspend fun get(key: String): SourceStatEntity?

    @Query("SELECT * FROM source_stats")
    suspend fun all(): List<SourceStatEntity>

    @Upsert
    suspend fun upsert(entity: SourceStatEntity)

    @Query("DELETE FROM source_stats")
    suspend fun clear()
}
