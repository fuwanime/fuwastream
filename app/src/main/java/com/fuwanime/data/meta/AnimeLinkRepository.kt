package com.fuwanime.data.meta

import com.fuwanime.data.Anime
import com.fuwanime.data.local.AnimeLinkDao

/** Links catalog anime to AniList/MAL on demand (one Detail at a time) and caches the result in Room. */
class AnimeLinkRepository(
    private val dao: AnimeLinkDao,
    private val api: AniListApi,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Metadata for [anime], or null when it is not linked or AniList could not be reached. Never throws. */
    suspend fun link(anime: Anime): AnimeMeta? = TODO()

    /** "Bukan anime ini?": hide enrichment for good and never search again. */
    suspend fun unlink(animeId: String): Unit = TODO()

    /** The MAL id for AniSkip; null unless linked. Reads the cache only. */
    suspend fun malIdFor(animeId: String): Int? = TODO()
}
