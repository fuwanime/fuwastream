package com.fuwanime.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A user-created collection list ("List koleksi"). Independent of [CollectionStatus]: a title keeps
 * its status in favourites while also belonging to any number of custom lists.
 */
@Entity(tableName = "collection_lists")
data class CollectionListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Optional single emoji shown before the name; null = plain text chip. */
    val emoji: String? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * One title inside a list. Title and poster are denormalised (like watch history rows) so the grid
 * renders offline and a title can be listed even when it is not in favourites.
 */
@Entity(
    tableName = "collection_items",
    primaryKeys = ["listId", "mediaType", "contentId"],
    indices = [Index("listId"), Index("mediaType", "contentId")],
)
data class CollectionItemEntity(
    val listId: Long,
    /** "ANIME" or "MANGA", same vocabulary as [ContentPreferenceEntity]. */
    val mediaType: String,
    val contentId: String,
    val title: String,
    val poster: String,
    val addedAt: Long = System.currentTimeMillis(),
)

@Dao
interface CollectionListDao {
    @Query("SELECT * FROM collection_lists ORDER BY sortOrder ASC, createdAt ASC")
    fun observeLists(): Flow<List<CollectionListEntity>>

    @Query("SELECT * FROM collection_lists ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun lists(): List<CollectionListEntity>

    @Insert
    suspend fun insertList(list: CollectionListEntity): Long

    @Upsert
    suspend fun upsertList(list: CollectionListEntity)

    @Query("DELETE FROM collection_lists WHERE id = :id")
    suspend fun deleteList(id: Long)

    @Query("SELECT * FROM collection_items WHERE listId = :listId ORDER BY addedAt DESC")
    fun observeItems(listId: Long): Flow<List<CollectionItemEntity>>

    @Query("SELECT * FROM collection_items ORDER BY addedAt DESC")
    fun observeAllItems(): Flow<List<CollectionItemEntity>>

    @Query("SELECT * FROM collection_items WHERE mediaType = :mediaType AND contentId = :contentId")
    fun observeMemberships(mediaType: String, contentId: String): Flow<List<CollectionItemEntity>>

    @Upsert
    suspend fun upsertItem(item: CollectionItemEntity)

    @Query("DELETE FROM collection_items WHERE listId = :listId AND mediaType = :mediaType AND contentId = :contentId")
    suspend fun deleteItem(listId: Long, mediaType: String, contentId: String)

    @Query("DELETE FROM collection_items WHERE listId = :listId")
    suspend fun deleteItems(listId: Long)

    @Query("DELETE FROM collection_items")
    suspend fun clearItems()

    @Query("DELETE FROM collection_lists")
    suspend fun clearLists()
}
