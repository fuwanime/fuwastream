package com.fuwanime.data

import androidx.room.withTransaction
import com.fuwanime.data.backup.BackupFile
import com.fuwanime.data.backup.mergeDaily
import com.fuwanime.data.backup.mergeHistory
import com.fuwanime.data.backup.mergeMangaHistory
import com.fuwanime.data.backup.mergePreferences
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
import com.fuwanime.data.local.CollectionStatus
import com.fuwanime.data.local.ContentPreferenceEntity
import com.fuwanime.data.local.SourceStatEntity
import com.fuwanime.util.epochDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    private val preferences = db.contentPreferenceDao()
    private val profile = db.profileDao()

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
                    collectionStatus = if (history.observeForAnime(anime.id).first().isEmpty()) CollectionStatus.PLANNED.name else CollectionStatus.IN_PROGRESS.name,
                ),
            )
        } else {
            favorites.delete(anime.id)
        }
    }

    fun collectionStatus(animeId: String): Flow<CollectionStatus?> = favoriteList.map { list ->
        list.firstOrNull { it.animeId == animeId }?.collectionStatus?.let { raw -> CollectionStatus.entries.firstOrNull { it.name == raw } }
    }

    suspend fun setCollectionStatus(anime: Anime, status: CollectionStatus?) {
        if (status == null) return favorites.delete(anime.id)
        val old = favorites.allFavorites().firstOrNull { it.animeId == anime.id }
        favorites.upsert(
            FavoriteEntity(
                anime.id, anime.title, anime.poster, anime.cover, anime.type, anime.status, anime.displayYear,
                anime.genres.joinToString(","), old?.addedAt ?: System.currentTimeMillis(), status.name,
            ),
        )
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

    /** Manual progress uses a stable synthetic duration; playback later replaces it with the real duration. */
    suspend fun markEpisode(anime: Anime, episode: Episode, watched: Boolean) {
        if (!watched) return history.deleteEpisode(episode.id)
        saveProgress(anime, episode, positionMs = 1_000, durationMs = 1_000)
    }

    suspend fun markEpisodes(anime: Anime, episodes: List<Episode>, watched: Boolean) = db.withTransaction {
        if (watched) {
            // Distinct timestamps: Lanjutkan picks the newest row per anime, and a shared
            // timestamp would surface several episodes of the same title at once.
            val ordered = episodes.sortedBy { it.sortKey }
            val now = System.currentTimeMillis()
            history.upsertAll(ordered.mapIndexed { index, episode ->
                WatchHistoryEntity(
                    episode.id, anime.id, anime.title, anime.poster, anime.backdrop,
                    episode.number, episode.title, episode.thumbnail, 1_000, 1_000,
                    now - (ordered.size - 1 - index),
                )
            })
        } else episodes.forEach { history.deleteEpisode(it.id) }
    }

    suspend fun restartEpisode(episodeId: String) {
        history.get(episodeId)?.let { history.upsert(it.copy(positionMs = 0, updatedAt = System.currentTimeMillis())) }
    }

    fun titlePreference(mediaType: String, contentId: String): Flow<ContentPreferenceEntity?> =
        preferences.observe(ContentPreferenceEntity.key(mediaType, contentId))

    fun titlePreferences(): Flow<List<ContentPreferenceEntity>> = preferences.observeAll()

    suspend fun titlePreferenceValue(mediaType: String, contentId: String): ContentPreferenceEntity? =
        preferences.get(ContentPreferenceEntity.key(mediaType, contentId))

    suspend fun updateTitlePreference(mediaType: String, contentId: String, change: (ContentPreferenceEntity) -> ContentPreferenceEntity) {
        val key = ContentPreferenceEntity.key(mediaType, contentId)
        val current = preferences.get(key) ?: ContentPreferenceEntity(key, mediaType, contentId)
        preferences.upsert(change(current).copy(key = key, mediaType = mediaType, contentId = contentId, updatedAt = System.currentTimeMillis()))
    }

    suspend fun hideAnimeFromContinue(animeId: String, hidden: Boolean = true) =
        updateTitlePreference("ANIME", animeId) { it.copy(hiddenFromContinue = hidden) }

    suspend fun hideMangaFromContinue(mangaId: String, hidden: Boolean = true) =
        updateTitlePreference("MANGA", mangaId) { it.copy(hiddenFromContinue = hidden) }

    /** Local-only source reliability used by Smart Source; never uploaded. */
    suspend fun sourceStats(): List<SourceStatEntity> = db.sourceStatDao().all()

    suspend fun recordSourceOutcome(sourceKey: String, success: Boolean, startupMs: Long = 0) {
        if (sourceKey.isBlank()) return
        val dao = db.sourceStatDao()
        val old = dao.get(sourceKey) ?: SourceStatEntity(sourceKey)
        dao.upsert(
            if (success) old.copy(
                successes = old.successes + 1,
                startupTotalMs = old.startupTotalMs + startupMs.coerceAtLeast(0),
                lastUsedAt = System.currentTimeMillis(),
            ) else old.copy(
                failures = old.failures + 1,
                lastUsedAt = System.currentTimeMillis(),
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
    suspend fun clearAll() = db.withTransaction {
        favorites.clear()
        history.clear()
        searches.clear()
        daily.clear()
        links.clear()
        mangaFavorites.clear()
        mangaHistory.clear()
        preferences.clear()
        db.sourceStatDao().clear()
        profile.clearProfile()
        profile.clearQuests()
        profile.clearCounted()
        profile.clearBorders()
    }

    data class Snapshot(
        val favorites: List<FavoriteEntity>,
        val history: List<WatchHistoryEntity>,
        val watchDaily: List<WatchDailyEntity>,
        val searches: List<SearchHistoryEntity>,
        val mangaFavorites: List<MangaFavoriteEntity>,
        val mangaHistory: List<MangaHistoryEntity>,
        val contentPreferences: List<ContentPreferenceEntity>,
    )

    /** Everything that goes into a backup file, read in one transaction so the parts agree. */
    suspend fun snapshot(): Snapshot = db.withTransaction {
        Snapshot(favorites.allFavorites(), history.all(), daily.all(), searches.all(), mangaFavorites.all(), mangaHistory.all(), preferences.all())
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
        preferences.upsertAll(mergePreferences(preferences.all(), file.contentPreferences))
    }
}
