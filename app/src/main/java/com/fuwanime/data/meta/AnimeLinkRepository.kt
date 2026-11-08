package com.fuwanime.data.meta

import android.util.Log
import com.fuwanime.data.Anime
import com.fuwanime.data.local.AnimeLinkDao
import com.fuwanime.data.local.AnimeLinkEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Links catalog anime to AniList/MAL on demand (one Detail at a time) and caches the result in Room. */
class AnimeLinkRepository(
    private val dao: AnimeLinkDao,
    private val api: AniListApi,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Metadata for [anime], or null when it is not linked or AniList could not be reached. Never throws. */
    suspend fun link(anime: Anime): AnimeMeta? = withContext(Dispatchers.IO) {
        val row = dao.get(anime.id)
        val state = row?.let { r -> LinkState.entries.firstOrNull { it.name == r.state } }
        if (row == null || state == null) return@withContext search(anime)
        when (state) {
            LinkState.UNLINKED -> null
            LinkState.LINKED -> refreshed(row) ?: row.meta()
            LinkState.NONE -> if (needsRefresh(LinkState.NONE, row.fetchedAt, null, now())) search(anime) else null
        }
    }

    /** "Bukan anime ini?": hide enrichment for good and never search again. */
    suspend fun unlink(animeId: String) = withContext(Dispatchers.IO) {
        dao.put(AnimeLinkEntity(animeId, LinkState.UNLINKED.name, null, null, null, "", null, null, now()))
    }

    /** The MAL id for AniSkip; null unless linked. Reads the cache only. */
    suspend fun malIdFor(animeId: String): Int? = withContext(Dispatchers.IO) {
        dao.get(animeId)?.takeIf { it.state == LinkState.LINKED.name }?.malId
    }

    /** Title first (same year), then up to three synonyms (±1 year); at most four requests. */
    private suspend fun search(anime: Anime): AnimeMeta? {
        val synonyms = synonymsOf(anime.alternativeTitles)
        val year = airedYearOf(anime.airedStart)
        (listOf(anime.title) + synonyms.take(3)).forEachIndexed { i, query ->
            val candidates = request(SEARCH_QUERY, mapOf("s" to query))?.page?.media ?: return null
            val match = pickMatch(anime.title, synonyms, year, candidates, yearTolerance = if (i == 0) 0 else 1)
            if (match != null) return store(anime.id, match)
        }
        dao.put(AnimeLinkEntity(anime.id, LinkState.NONE.name, null, null, null, "", null, null, now()))
        return null
    }

    /** New metadata for a stale link, or null when it is fresh or the refresh failed. */
    private suspend fun refreshed(row: AnimeLinkEntity): AnimeMeta? {
        if (!needsRefresh(LinkState.LINKED, row.fetchedAt, row.nextAiringAt, now())) return null
        val id = row.anilistId ?: return null
        val media = request(BY_ID_QUERY, mapOf("id" to id))?.media ?: return null
        return store(row.animeId, media)
    }

    private suspend fun store(animeId: String, m: AniListMedia): AnimeMeta {
        val meta = metaOf(m)
        dao.put(
            AnimeLinkEntity(
                animeId, LinkState.LINKED.name, m.id, m.idMal, meta.score, meta.tags.joinToString("\n"),
                meta.nextEpisode, meta.nextAiringAt, now(),
            ),
        )
        return meta
    }

    /** Null on any failure (network, 429, 5xx, schema); nothing is stored then, so the next visit retries. */
    private suspend fun request(query: String, variables: Map<String, Any?>): GraphQlData? = try {
        api.query(GraphQlRequest(query, variables)).data
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        runCatching { Log.w(TAG, "AniList request failed", e) }
        null
    }

    private fun AnimeLinkEntity.meta() = AnimeMeta(score, tags.split('\n').filter { it.isNotEmpty() }, nextEpisode, nextAiringAt)

    private companion object {
        const val TAG = "FuwaMeta"
    }
}
