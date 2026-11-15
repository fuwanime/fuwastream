package com.fuwanime.data.manga

import com.fuwanime.data.local.FuwaDatabase
import com.fuwanime.data.local.MangaFavoriteEntity
import com.fuwanime.data.local.MangaHistoryEntity
import com.fuwanime.data.local.CollectionStatus
import com.fuwanime.data.local.ContentPreferenceEntity
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The newest history row per manga, most recently read first. */
fun continueReading(history: List<MangaHistoryEntity>): List<MangaHistoryEntity> =
    history.groupBy { it.mangaId }.values.map { rows -> rows.maxBy { it.updatedAt } }.sortedByDescending { it.updatedAt }

/** Local manga favourites and reading progress. */
class MangaLibrary(private val db: FuwaDatabase) {
    private val favoriteDao = db.mangaFavoriteDao()
    private val historyDao = db.mangaHistoryDao()
    private val preferences = db.contentPreferenceDao()

    val favorites: Flow<List<MangaFavoriteEntity>> = favoriteDao.observeAll()

    fun isFavorite(id: String): Flow<Boolean> = favoriteDao.observeIsFavorite(id)

    suspend fun setFavorite(manga: Manga, favorite: Boolean) {
        if (favorite) {
            val active = historyDao.observeFor(manga.id).first().isNotEmpty()
            favoriteDao.upsert(MangaFavoriteEntity(manga.id, manga.title, manga.coverUrl, System.currentTimeMillis(), if (active) CollectionStatus.IN_PROGRESS.name else CollectionStatus.PLANNED.name))
        }
        else favoriteDao.delete(manga.id)
    }

    fun collectionStatus(id: String): Flow<CollectionStatus?> = favorites.map { list ->
        list.firstOrNull { it.mangaId == id }?.collectionStatus?.let { raw -> CollectionStatus.entries.firstOrNull { it.name == raw } }
    }

    suspend fun setCollectionStatus(manga: Manga, status: CollectionStatus?) {
        if (status == null) return favoriteDao.delete(manga.id)
        val old = favoriteDao.all().firstOrNull { it.mangaId == manga.id }
        favoriteDao.upsert(MangaFavoriteEntity(manga.id, manga.title, manga.coverUrl, old?.addedAt ?: System.currentTimeMillis(), status.name))
    }

    val continueReading: Flow<List<MangaHistoryEntity>> = historyDao.observeAll().map { continueReading(it) }

    fun historyFor(mangaId: String): Flow<List<MangaHistoryEntity>> = historyDao.observeFor(mangaId)

    suspend fun saveProgress(manga: Manga, chapter: MangaChapter, chapterLabel: String, page: Int, pageCount: Int) {
        historyDao.upsert(
            MangaHistoryEntity(chapter.id, manga.id, manga.title, manga.coverUrl, chapterLabel, page, pageCount, System.currentTimeMillis()),
        )
    }

    suspend fun removeManga(mangaId: String) = historyDao.deleteManga(mangaId)

    suspend fun markChapter(manga: Manga, chapter: MangaChapter, label: String, read: Boolean) {
        if (!read) return historyDao.deleteChapter(chapter.id)
        saveProgress(manga, chapter, label, page = (chapter.pages - 1).coerceAtLeast(0), pageCount = chapter.pages.coerceAtLeast(1))
    }

    suspend fun markChapters(manga: Manga, chapters: List<MangaChapter>, label: (MangaChapter) -> String, read: Boolean) {
        if (!read) {
            chapters.forEach { historyDao.deleteChapter(it.id) }
            return
        }
        // The highest chapter in this batch must be the newest history row, otherwise
        // "Lanjut baca" can point at an older chapter after a bulk mark.
        val ordered = chapters.sortedBy { numberKey(it)?.toDoubleOrNull() ?: Double.MAX_VALUE }
        val now = System.currentTimeMillis()
        db.withTransaction {
            ordered.forEachIndexed { index, chapter ->
                historyDao.upsert(
                    MangaHistoryEntity(
                        chapter.id, manga.id, manga.title, manga.coverUrl, label(chapter),
                        page = (chapter.pages - 1).coerceAtLeast(0),
                        pageCount = chapter.pages.coerceAtLeast(1),
                        updatedAt = now - (ordered.size - 1 - index),
                    ),
                )
            }
        }
    }

    fun preference(id: String): Flow<ContentPreferenceEntity?> = preferences.observe(ContentPreferenceEntity.manga(id))

    suspend fun updatePreference(id: String, change: (ContentPreferenceEntity) -> ContentPreferenceEntity) {
        val key = ContentPreferenceEntity.manga(id)
        val current = preferences.get(key) ?: ContentPreferenceEntity(key, "MANGA", id)
        preferences.upsert(
            change(current).copy(key = key, mediaType = "MANGA", contentId = id, updatedAt = System.currentTimeMillis()),
        )
    }
}
