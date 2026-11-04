package com.fuwanime.data

import com.fuwanime.data.local.FavoriteEntity
import com.fuwanime.data.local.FuwaDatabase
import com.fuwanime.data.local.SearchHistoryEntity
import com.fuwanime.data.local.WatchHistoryEntity
import com.fuwanime.data.local.WatchDailyEntity
import com.fuwanime.util.epochDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Everything the user owns locally: favourites, watch progress and recent searches. */
class LibraryRepository(db: FuwaDatabase) {
    private val favorites = db.favoriteDao()
    private val history = db.watchHistoryDao()
    private val searches = db.searchHistoryDao()
    private val daily = db.watchDailyDao()

    val favoriteList: Flow<List<FavoriteEntity>> = favorites.observeAll()

    suspend fun allFavorites(): List<FavoriteEntity> = favorites.allFavorites()

    fun isFavorite(animeId: String): Flow<Boolean> = favorites.observeIsFavorite(animeId)

    suspend fun setFavorite(anime: Anime, favorite: Boolean) {
        if (favorite) {
            favorites.upsert(
                FavoriteEntity(
                    animeId = anime.id,
                    title = anime.title,
                    poster = anime.poster,
                    cover = anime.cover,
                    type = anime.type,
                    status = anime.status,
                    year = anime.displayYear,
                    genres = anime.genres.joinToString(","),
                    addedAt = System.currentTimeMillis(),
                ),
            )
        } else {
            favorites.delete(anime.id)
        }
    }

    val allHistory: Flow<List<WatchHistoryEntity>> = history.observeAll()

    suspend fun historyEntry(episodeId: String): WatchHistoryEntity? = history.get(episodeId)

    fun continueWatching(limit: Int = 20): Flow<List<WatchHistoryEntity>> = history.observeLatestPerAnime(limit)

    /** episodeId -> progress row, for watched badges and progress bars on the episode list. */
    fun progressFor(animeId: String): Flow<Map<String, WatchHistoryEntity>> =
        history.observeForAnime(animeId).map { rows -> rows.associateBy { it.episodeId } }

    suspend fun savedPosition(episodeId: String): Long {
        val row = history.get(episodeId) ?: return 0
        // Restarting a finished episode is more useful than resuming at the credits.
        return if (row.isFinished) 0 else row.positionMs
    }

    suspend fun saveProgress(anime: Anime, episode: Episode, positionMs: Long, durationMs: Long) {
        history.upsert(
            WatchHistoryEntity(
                episodeId = episode.id,
                animeId = anime.id,
                animeTitle = anime.title,
                animePoster = anime.poster,
                animeCover = anime.backdrop,
                episodeNumber = episode.number,
                episodeTitle = episode.title,
                episodeThumbnail = episode.thumbnail,
                positionMs = positionMs.coerceAtLeast(0),
                durationMs = durationMs.coerceAtLeast(0),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    val watchDaily: Flow<List<WatchDailyEntity>> = daily.observeAll()

    /** Adds [ms] of real playing time to today's total. */
    suspend fun addWatchTime(ms: Long) = daily.addWatchTime(epochDay(System.currentTimeMillis()), ms)

    suspend fun removeFromHistory(animeId: String) = history.deleteAnime(animeId)
    suspend fun clearHistory() = history.clear()

    val recentSearches: Flow<List<String>> = searches.observeRecent().map { rows -> rows.map { it.query } }

    suspend fun addSearch(query: String) {
        val q = query.trim()
        if (q.length >= 2) searches.upsert(SearchHistoryEntity(q, System.currentTimeMillis()))
    }

    suspend fun removeSearch(query: String) = searches.delete(query)
    suspend fun clearSearches() = searches.clear()

    /** Deletes every locally stored personal row; used by "Hapus semua data". */
    suspend fun clearAll() {
        favorites.clear()
        history.clear()
        searches.clear()
        daily.clear()
    }
}
