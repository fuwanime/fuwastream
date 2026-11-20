package com.fuwanime.data

import androidx.room.withTransaction
import com.fuwanime.data.local.CollectionItemEntity
import com.fuwanime.data.local.CollectionListEntity
import com.fuwanime.data.local.FuwaDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** User-created collection lists and the titles inside them; fully local, alongside CollectionStatus. */
class CollectionListRepository(private val db: FuwaDatabase) {
    private val dao = db.collectionListDao()

    val lists: Flow<List<CollectionListEntity>> = dao.observeLists()
    val items: Flow<List<CollectionItemEntity>> = dao.observeAllItems()

    /** Ids of the lists that contain this title, for the add-to-list picker's checkboxes. */
    fun memberships(mediaType: String, contentId: String): Flow<Set<Long>> =
        dao.observeMemberships(mediaType, contentId).map { rows -> rows.mapTo(HashSet()) { it.listId } }

    suspend fun createList(name: String, emoji: String? = null): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1
        val order = (dao.lists().maxOfOrNull { it.sortOrder } ?: -1) + 1
        return dao.insertList(CollectionListEntity(name = trimmed, emoji = emoji?.trim()?.takeIf { it.isNotEmpty() }, sortOrder = order))
    }

    suspend fun renameList(id: Long, name: String, emoji: String?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val old = dao.lists().firstOrNull { it.id == id } ?: return
        dao.upsertList(old.copy(name = trimmed, emoji = emoji?.trim()?.takeIf { it.isNotEmpty() }))
    }

    /** Deleting a list only removes the grouping; favourites and their status are untouched. */
    suspend fun deleteList(id: Long) = db.withTransaction {
        dao.deleteItems(id)
        dao.deleteList(id)
    }

    /** Moves a list one slot up/down in the chip row; neighbours keep their relative order. */
    suspend fun moveList(id: Long, up: Boolean) {
        val ordered = dao.lists()
        val index = ordered.indexOfFirst { it.id == id }
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target !in ordered.indices) return
        // Normalise first: older rows may share a sortOrder, which would make a swap a no-op.
        val moved = ordered.mapIndexed { i, list -> list.copy(sortOrder = i) }.toMutableList()
        val tmp = moved[index]
        moved[index] = moved[target]
        moved[target] = tmp
        moved.forEach { dao.upsertList(it) }
    }

    suspend fun addItem(listId: Long, mediaType: String, contentId: String, title: String, poster: String) =
        dao.upsertItem(CollectionItemEntity(listId, mediaType, contentId, title, poster))

    suspend fun removeItem(listId: Long, mediaType: String, contentId: String) =
        dao.deleteItem(listId, mediaType, contentId)

    /** Used by "Hapus semua data". */
    suspend fun clearAll() = db.withTransaction {
        dao.clearItems()
        dao.clearLists()
    }
}
