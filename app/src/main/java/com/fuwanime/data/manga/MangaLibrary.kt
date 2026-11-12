package com.fuwanime.data.manga

import com.fuwanime.data.local.FuwaDatabase
import com.fuwanime.data.local.MangaFavoriteEntity
import com.fuwanime.data.local.MangaHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The newest history row per manga, most recently read first. */
fun continueReading(history: List<MangaHistoryEntity>): List<MangaHistoryEntity> =
    history.groupBy { it.mangaId }.values.map { rows -> rows.maxBy { it.updatedAt } }.sortedByDescending { it.updatedAt }

/** Local manga favourites and reading progress. */
class MangaLibrary(db: FuwaDatabase) {
    private val favoriteDao = db.mangaFavoriteDao()
    private val historyDao = db.mangaHistoryDao()

    val favorites: Flow<List<MangaFavoriteEntity>> = favoriteDao.observeAll()

    fun isFavorite(id: String): Flow<Boolean> = favoriteDao.observeIsFavorite(id)

    suspend fun setFavorite(manga: Manga, favorite: Boolean) {
        if (favorite) favoriteDao.upsert(MangaFavoriteEntity(manga.id, manga.title, manga.coverUrl, System.currentTimeMillis()))
        else favoriteDao.delete(manga.id)
    }

    val continueReading: Flow<List<MangaHistoryEntity>> = historyDao.observeAll().map { continueReading(it) }

    fun historyFor(mangaId: String): Flow<List<MangaHistoryEntity>> = historyDao.observeFor(mangaId)

    suspend fun saveProgress(manga: Manga, chapter: MangaChapter, chapterLabel: String, page: Int, pageCount: Int) {
        historyDao.upsert(
            MangaHistoryEntity(chapter.id, manga.id, manga.title, manga.coverUrl, chapterLabel, page, pageCount, System.currentTimeMillis()),
        )
    }

    suspend fun removeManga(mangaId: String) = historyDao.deleteManga(mangaId)
}
