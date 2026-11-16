package com.fuwanime.widget

import com.fuwanime.data.local.MangaHistoryEntity
import com.fuwanime.data.local.WatchHistoryEntity

/** One row of the "Lanjutkan" widget: an anime episode or a manga chapter, whichever was touched last. */
sealed interface ContinueEntry {
    val key: String
    val title: String
    val imageUrl: String
    val progress: Float
    val updatedAt: Long

    data class Anime(val row: WatchHistoryEntity) : ContinueEntry {
        override val key get() = "a:" + row.animeId
        override val title get() = row.animeTitle
        override val imageUrl get() = row.animePoster.ifBlank { row.animeCover }
        override val progress get() = row.progress
        override val updatedAt get() = row.updatedAt
    }

    data class Manga(val row: MangaHistoryEntity) : ContinueEntry {
        override val key get() = "m:" + row.mangaId
        override val title get() = row.mangaTitle
        override val imageUrl get() = row.coverUrl.orEmpty()
        override val progress get() = if (row.pageCount > 0) ((row.page + 1).toFloat() / row.pageCount).coerceIn(0f, 1f) else 0f
        override val updatedAt get() = row.updatedAt
    }
}

/** Newest first, one row per title, at most [limit]; [watching] and [reading] are already one row per title. */
fun mergeContinue(watching: List<WatchHistoryEntity>, reading: List<MangaHistoryEntity>, limit: Int): List<ContinueEntry> =
    (watching.map(ContinueEntry::Anime) + reading.map(ContinueEntry::Manga))
        .sortedByDescending { it.updatedAt }
        .distinctBy { it.key }
        .take(limit)
