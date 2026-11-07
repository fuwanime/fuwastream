package com.fuwanime.data.backup

import com.fuwanime.data.local.FavoriteEntity
import com.fuwanime.data.local.MangaFavoriteEntity
import com.fuwanime.data.local.MangaHistoryEntity
import com.fuwanime.data.local.SearchHistoryEntity
import com.fuwanime.data.local.WatchDailyEntity
import com.fuwanime.data.local.WatchHistoryEntity

// Restoring merges into what is on the phone: each function returns only the backup rows to upsert.

/** Per episode, the more recent progress wins. */
fun mergeHistory(local: List<WatchHistoryEntity>, backup: List<WatchHistoryEntity>): List<WatchHistoryEntity> {
    val mine = local.associateBy { it.episodeId }
    return backup.filter { b -> mine[b.episodeId].let { it == null || b.updatedAt > it.updatedAt } }
}

/** Per day, the larger total wins; taking the max (not the sum) makes restoring twice harmless. */
fun mergeDaily(local: List<WatchDailyEntity>, backup: List<WatchDailyEntity>): List<WatchDailyEntity> {
    val mine = local.associateBy { it.day }
    return backup.filter { b -> mine[b.day].let { it == null || b.watchedMs > it.watchedMs } }
}

fun mergeSearches(local: List<SearchHistoryEntity>, backup: List<SearchHistoryEntity>): List<SearchHistoryEntity> {
    val mine = local.associateBy { it.query }
    return backup.filter { b -> mine[b.query].let { it == null || b.searchedAt > it.searchedAt } }
}

/** Favourites already on the phone are left as they are. */
fun newFavorites(local: List<FavoriteEntity>, backup: List<FavoriteEntity>): List<FavoriteEntity> {
    val mine = local.mapTo(HashSet()) { it.animeId }
    return backup.filter { it.animeId !in mine }
}

/** Per chapter, the more recent progress wins. */
fun mergeMangaHistory(local: List<MangaHistoryEntity>, backup: List<MangaHistoryEntity>): List<MangaHistoryEntity> {
    val mine = local.associateBy { it.chapterId }
    return backup.filter { b -> mine[b.chapterId].let { it == null || b.updatedAt > it.updatedAt } }
}

fun newMangaFavorites(local: List<MangaFavoriteEntity>, backup: List<MangaFavoriteEntity>): List<MangaFavoriteEntity> {
    val mine = local.mapTo(HashSet()) { it.mangaId }
    return backup.filter { it.mangaId !in mine }
}
