package com.fuwanime.data

import androidx.room.withTransaction
import com.fuwanime.data.backup.BackupFile
import com.fuwanime.data.backup.mergeDaily
import com.fuwanime.data.backup.mergeHistory
import com.fuwanime.data.backup.mergeMangaHistory
import com.fuwanime.data.backup.newMangaFavorites
import com.fuwanime.data.backup.mergeSearches
import com.fuwanime.data.backup.newFavorites
import com.fuwanime.data.local.FavoriteEntity
import com.fuwanime.data.local.FuwaDatabase
import com.fuwanime.data.local.MangaFavoriteEntity
import com.fuwanime.data.local.MangaHistoryEntity
import com.fuwanime.data.local.SearchHistoryEntity
import com.fuwanime.data.local.WatchHistoryEntity
import com.fuwanime.data.local.WatchDailyEntity
import com.fuwanime.util.epochDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Everything the user owns locally: favourites, watch progress and recent searches. */
class LibraryRepository(private val db: FuwaDatabase) {
    private val favorites = db.favoriteDao()
    private val history = db.watchHistoryDao()
    private val searches = db.searchHistoryDao()
    private val daily = db.watchDailyDao()
    private val links = db.animeLinkDao()
    private val mangaFavorites = db.mangaFavoriteDao()
    private val mangaHistory = db.mangaHistoryDao()

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
            questHooks?.invoke(QuestKind.FAVORITE)
        } else {
            favorites.delete(anime.id)
        }
    }

    /** Optional hook into the quest board; set by AppContainer. */
    var questHooks: ((QuestKind) -> Unit)? = null
    var profileBumper: ((String) -> Unit)? = null

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
        if (q.length >= 2) {
            searches.upsert(SearchHistoryEntity(q, System.currentTimeMillis()))
            questHooks?.invoke(QuestKind.SEARCH)
        }
    }

    suspend fun removeSearch(query: String) = searches.delete(query)
    suspend fun clearSearches() = searches.clear()

    /** Deletes every locally stored personal row; used by "Hapus semua data". */
    suspend fun clearAll() = db.withTransaction {
        favorites.clear()
        history.clear()
        searches.clear()
        daily.clear()
        links.clear()
        mangaFavorites.clear()
        mangaHistory.clear()
    }

    data class Snapshot(
        val favorites: List<FavoriteEntity>,
        val history: List<WatchHistoryEntity>,
        val watchDaily: List<WatchDailyEntity>,
        val searches: List<SearchHistoryEntity>,
        val mangaFavorites: List<MangaFavoriteEntity>,
        val mangaHistory: List<MangaHistoryEntity>,
    )

    /** Everything that goes into a backup file, read in one transaction so the parts agree. */
    suspend fun snapshot(): Snapshot = db.withTransaction {
        Snapshot(favorites.allFavorites(), history.all(), daily.all(), searches.all(), mangaFavorites.all(), mangaHistory.all())
    }

    /** No favourites and no history: a fresh install (or a wiped one). */
    suspend fun isEmpty(): Boolean = favorites.allFavorites().isEmpty() && history.all().isEmpty() &&
        mangaFavorites.all().isEmpty() && mangaHistory.all().isEmpty()

    /** Merges a backup into the local data; see BackupMerge for which side wins. */
    suspend fun restore(file: BackupFile) = db.withTransaction {
        favorites.upsertAll(newFavorites(favorites.allFavorites(), file.favorites))
        history.upsertAll(mergeHistory(history.all(), file.history))
        daily.upsertAll(mergeDaily(daily.all(), file.watchDaily))
        searches.upsertAll(mergeSearches(searches.all(), file.searches))
        mangaFavorites.upsertAll(newMangaFavorites(mangaFavorites.all(), file.mangaFavorites))
        mangaHistory.upsertAll(mergeMangaHistory(mangaHistory.all(), file.mangaHistory))
    }
}
