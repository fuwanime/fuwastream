package com.fuwanime.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/** A catalog anime's AniList/MAL link plus the metadata fetched with it. [state] holds a [com.fuwanime.data.meta.LinkState] name. */
@Entity(tableName = "anime_link")
data class AnimeLinkEntity(
    @PrimaryKey val animeId: String,
    val state: String,
    val anilistId: Int?,
    val malId: Int?,
    val score: Int?,
    /** Non-spoiler tag names separated by newlines. */
    val tags: String,
    val nextEpisode: Int?,
    /** Epoch milliseconds. */
    val nextAiringAt: Long?,
    val fetchedAt: Long,
)

@Dao
interface AnimeLinkDao {
    @Query("SELECT * FROM anime_link WHERE animeId = :id")
    suspend fun get(id: String): AnimeLinkEntity?

    @Upsert
    suspend fun put(e: AnimeLinkEntity)

    @Query("DELETE FROM anime_link")
    suspend fun clear()
}
