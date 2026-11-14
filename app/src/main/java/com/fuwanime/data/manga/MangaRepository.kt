package com.fuwanime.data.manga

import com.fuwanime.R
import com.fuwanime.util.UiText
import com.fuwanime.util.UiTextException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/** RELEVANCE only makes sense together with a title query. */
enum class MangaOrder { POPULAR, LATEST, RELEVANCE }

data class MangaPage(val items: List<Manga>, val total: Int, val offset: Int)

class MangaRepository(
    private val appLanguage: () -> String,
    private val now: () -> Long = System::currentTimeMillis,
    /** Indonesian source; ids starting with "sh:" are routed there. */
    private val shinigami: ShinigamiSource? = null,
    /** Indonesian source (large archive); ids starting with "km:". */
    private val komiku: KomikuSource? = null,
    /** Primary English catalogue backed by MangaDex; ids starting with "md:" (or plain) are routed there. */
    private val mangadex: MangaDexSource? = null,
    /** "Konten dewasa" setting: also show mature-rated catalogue entries where allowed; never pornographic. */
    private val mature: suspend () -> Boolean = { false },
) {
    private val sourceCache = ConcurrentHashMap<String, List<Manga>>()
    private val mergedCache = ConcurrentHashMap<String, List<MangaChapter>>()
    @Volatile private var tagCache: List<MangaTag>? = null
    @Volatile private var releaseCache: Pair<Long, List<MangaRelease>>? = null

    /** The primary MangaDex catalogue for browse/search (English-only chapters); Indonesian sources remain available. */
    suspend fun browse(
        order: MangaOrder,
        query: String = "",
        tagIds: List<String> = emptyList(),
        status: MangaStatus? = null,
        offset: Int = 0,
        type: MangaType? = null,
    ): MangaPage {
        val source = mangadex ?: return MangaPage(emptyList(), 0, offset)
        val page = offset / PAGE_SIZE + 1
        val result = source.browse(order, query, page, type, tagIds, status)
        return result.copy(offset = offset)
    }

    /** Popular Indonesian titles (Shinigami). */
    suspend fun popularIndonesia(page: Int = 1, type: MangaType? = null): MangaPage =
        shinigami?.browse("popularity", null, page, type?.slug) ?: MangaPage(emptyList(), 0, 0)

    /** Shinigami and Komiku search results for [query]; empty when a source is off or fails. */
    suspend fun searchIndonesia(query: String): List<Manga> {
        val shinigamiHits = try {
            shinigami?.browse(null, query)?.items.orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        val komikuHits = try {
            komiku?.search(query).orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        return shinigamiHits + komikuHits
    }

    /** English MangaDex results; empty when unavailable or too slow for a search. */
    suspend fun searchComix(query: String): List<Manga> = try {
        withTimeoutOrNull(SEARCH_TIMEOUT_MS) { mangadex?.search(query).orEmpty() }.orEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    /** Popular English titles from MangaDex. */
    suspend fun popularComix(page: Int = 1, type: MangaType? = null): MangaPage =
        mangadex?.browse(MangaOrder.POPULAR, page = page, type = type) ?: MangaPage(emptyList(), 0, 0)

    /** Popular Komiku titles; [page] is 1-based, 10 per page. */
    suspend fun popularKomiku(page: Int = 1, type: MangaType? = null): MangaPage =
        komiku?.browse("meta_value_num", page, type?.slug) ?: MangaPage(emptyList(), 0, 0)

    /** The app language ("id", "en", …) the repository uses for descriptions and the default chapter language. */
    fun language(): String = appLanguage()

    /** The MangaDex entry for a title (tries each name in order), or null. */
    suspend fun findOnComix(names: List<String>): Manga? {
        for (name in names.filter { it.isNotBlank() }.distinct().take(3)) {
            val hit = runCatching { browse(MangaOrder.RELEVANCE, name).items }.getOrNull()
                ?.firstOrNull { sameTitle(name, it.title, it.altTitles) }
            if (hit != null) return hit
        }
        return null
    }

    /** [manga] plus the same title on the other sources; a lookup that fails or takes over 8 s is left out. */
    suspend fun sources(manga: Manga): List<Manga> = sourceCache[manga.id] ?: coroutineScope {
        val names = listOf(manga.title) + manga.altTitles.filter { n -> n.count(Char::isLetterOrDigit) >= 5 }
        suspend fun look(block: suspend () -> Manga?) = runCatching { withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { block() } }.getOrNull()
        val others = listOfNotNull(
            if (isShinigamiId(manga.id)) null else async { look { shinigami?.find(names) } },
            if (isKomikuId(manga.id)) null else async { look { komiku?.find(names) } },
            if (isMangaDexId(manga.id) || (!isShinigamiId(manga.id) && !isKomikuId(manga.id))) null else async { look { mangadex?.find(names) } },
        ).awaitAll().filterNotNull()
        (listOf(manga) + others).also { sourceCache[manga.id] = it }
    }

    /** Every chapter of [manga] on every source, all languages; [mergeChapters] picks per language. Fails only if every source fails. */
    suspend fun mergedChapters(manga: Manga): List<MangaChapter> = mergedCache[manga.id] ?: coroutineScope {
        val results = sources(manga).mapIndexed { i, m ->
            async {
                runCatching {
                    if (i == 0) chapters(m.id) else withTimeoutOrNull(OTHER_CHAPTERS_TIMEOUT_MS) { chapters(m.id) }.orEmpty()
                }
            }
        }.awaitAll()
        if (results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
        results.flatMap { it.getOrDefault(emptyList()) }.also { mergedCache[manga.id] = it }
    }

    /** Forget the cached sources and chapters of one title, so a retry looks again. */
    fun clearMerged(mangaId: String) {
        sourceCache.remove(mangaId)
        mergedCache.remove(mangaId)
    }

    suspend fun detail(id: String): Manga {
        if (isShinigamiId(id)) return shinigami?.detail(id) ?: throw notFound()
        if (isKomikuId(id)) return komiku?.detail(id) ?: throw notFound()
        return mangadex?.detail(mangaDexId(id)) ?: throw notFound()
    }

    suspend fun chapters(id: String): List<MangaChapter> {
        if (isShinigamiId(id)) return shinigami?.chapters(id).orEmpty()
        if (isKomikuId(id)) return komiku?.chapters(id).orEmpty()
        return mangadex?.chapters(mangaDexId(id)).orEmpty()
    }

    /** Newest English chapter per recently updated manga, newest first; feeds release notifications and the home summary. */
    suspend fun latestOngoingChapters(limit: Int = 100): List<MangaRelease> {
        val cached = releaseCache?.takeIf { now() - it.first < RELEASES_TTL_MS }
        if (cached != null) return cached.second
        val releases = mangadex?.latestOngoingChapters(limit).orEmpty()
        releaseCache = now() to releases
        return releases
    }

    suspend fun pages(chapterId: String): List<String> {
        if (isShinigamiId(chapterId)) return shinigami?.pages(chapterId).orEmpty()
        if (isKomikuId(chapterId)) return komiku?.pages(chapterId).orEmpty()
        return mangadex?.pages(chapterId).orEmpty()
    }

    suspend fun tags(): List<MangaTag> = tagCache ?: mangadex?.tags().orEmpty().sortedBy { it.name }.also { tagCache = it }

    private fun mangaDexId(id: String): String = if (isMangaDexId(id)) id else MD_PREFIX + id

    private fun notFound() = UiTextException(UiText(R.string.manga_error_not_found))

    private companion object {
        const val PAGE_SIZE = 28
        const val LOOKUP_TIMEOUT_MS = 8_000L
        const val OTHER_CHAPTERS_TIMEOUT_MS = 15_000L
        const val SEARCH_TIMEOUT_MS = 15_000L
        const val RELEASES_TTL_MS = 30 * 60_000L
    }
}
