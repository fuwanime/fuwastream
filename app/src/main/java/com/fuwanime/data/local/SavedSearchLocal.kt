package com.fuwanime.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.flow.Flow

/**
 * A named search: the query plus the Explore filter state serialised as JSON ([filterJson]).
 * Applying one restores both, so a favourite genre/type combination is one tap away.
 */
@JsonClass(generateAdapter = true)
@Entity(tableName = "saved_search_filters")
data class SavedSearchFilterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val query: String,
    val filterJson: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface SavedSearchFilterDao {
    @Query("SELECT * FROM saved_search_filters ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavedSearchFilterEntity>>

    @Query("SELECT * FROM saved_search_filters")
    suspend fun all(): List<SavedSearchFilterEntity>

    @Upsert
    suspend fun upsert(entity: SavedSearchFilterEntity)

    @Query("DELETE FROM saved_search_filters WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM saved_search_filters")
    suspend fun clear()
}
