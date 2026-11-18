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
    /** Source manga names separated by newlines; empty when there is none (or the row predates v9). */
    val sourceTitles: String = "",
    /** Cast as encoded by [com.fuwanime.data.meta.encodeCast]; empty when unknown (or the row predates v10). */
    val cast: String = "",
    /** AniList users with this title on a list; null when unknown (or the row predates v11). */
    val popularity: Int? = null,
    val favourites: Int? = null,
    val totalEpisodes: Int? = null,
    /** Typical episode length in minutes. */
    val durationMinutes: Int? = null,
    val studio: String? = null,
    val year: Int? = null,
    /** Episode thumbnails as encoded by [com.fuwanime.data.meta.encodeEpisodeThumbnails]; empty when unknown. */
    val episodeThumbnails: String = "",
    /** Watchable trailer URL from AniList; empty when there is none (or the row predates v12). */
    val trailerUrl: String = "",
    /** Trailer poster frame from AniList; empty when unknown (or the row predates v12). */
    val trailerThumbnail: String = "",
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
