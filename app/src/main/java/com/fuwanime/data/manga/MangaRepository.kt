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
    /** Indonesian source (large archive of older titles); ids starting with "km:". */
    private val komiku: KomikuSource? = null,
    /** Official source (originals + canvas in id/en); ids starting with "wt:". */
    private val webtoon: WebtoonSource? = null,
    /** English source (MangaKakalot/KakaManga network); ids start with "kk:". */
    private val kakaManga: KakaMangaSource? = null,
    /** English source (MangaPark); ids start with "mp:". */
    private val mangaPark: MangaParkSource? = null,
) {
    private val sourceCache = ConcurrentHashMap<String, List<Manga>>()
    private val mergedCache = ConcurrentHashMap<String, List<MangaChapter>>()
    @Volatile private var releaseCache: Pair<Long, List<MangaRelease>>? = null

    /** English browse/search backed by MangaPark and MangaKakalot (no Comix). */
    suspend fun browse(
        order: MangaOrder,
        query: String = "",
        tagIds: List<String> = emptyList(),
        status: MangaStatus? = null,
        offset: Int = 0,
        type: MangaType? = null,
    ): MangaPage {
        val q = query.trim()
        if (q.isNotBlank()) {
            val items = dedupeByTitle(searchEnglish(q))
            return MangaPage(items, items.size, 0)
        }
        // No native tag/status filter on MP/KK; return general listings and let UI degrade gracefully.
        val pageNum = (offset / PAGE_SIZE).coerceAtLeast(0) + 1
        val items = dedupeByTitle(browseEnglish(pageNum))
        return MangaPage(items, items.size, offset)
    }

    private suspend fun searchEnglish(query: String): List<Manga> = coroutineScope {
        val mp = async { runCatching { withTimeoutOrNull(EN_TIMEOUT_MS) { mangaPark?.search(query).orEmpty() } }.getOrNull().orEmpty() }
        val kk = async { runCatching { withTimeoutOrNull(EN_TIMEOUT_MS) { kakaManga?.search(query).orEmpty() } }.getOrNull().orEmpty() }
        dedupeByTitle(mp.await() + kk.await())
    }

    private suspend fun browseEnglish(page: Int): List<Manga> = coroutineScope {
        val mp = async { runCatching { withTimeoutOrNull(EN_TIMEOUT_MS) { mangaPark?.browse(page)?.items.orEmpty() } }.getOrNull().orEmpty() }
        val kk = async { runCatching { withTimeoutOrNull(EN_TIMEOUT_MS) { kakaManga?.browse(page)?.items.orEmpty() } }.getOrNull().orEmpty() }
        dedupeByTitle(mp.await() + kk.await())
    }

    /** Popular Indonesian titles (Shinigami). */
    suspend fun popularIndonesia(page: Int = 1, type: MangaType? = null): MangaPage =
        shinigami?.browse("popularity", null, page, type?.slug) ?: MangaPage(emptyList(), 0, 0)

    /** Shinigami, Komiku and Webtoon search results for [query]; empty when a source is off or fails. */
    suspend fun searchIndonesia(query: String): List<Manga> {
        val shinigamiHits = runCatching { shinigami?.browse(null, query)?.items.orEmpty() }.getOrDefault(emptyList())
        val komikuHits = runCatching { komiku?.search(query).orEmpty() }.getOrDefault(emptyList())
        val webtoonHits = runCatching { webtoon?.search(query).orEmpty() }.getOrDefault(emptyList())
        return shinigamiHits + komikuHits + webtoonHits
    }

    /** English results from MangaPark & MangaKakalot (compat name kept for callers). */
    suspend fun searchComix(query: String): List<Manga> = try {
        withTimeoutOrNull(EN_TIMEOUT_MS) { searchEnglish(query) }.orEmpty()
    } catch (_: CancellationException) {
        throw CancellationException()
    } catch (_: Exception) {
        emptyList()
    }

    /** Popular English titles from MangaPark & MangaKakalot (compat name kept for callers). */
    suspend fun popularComix(page: Int = 1, type: MangaType? = null): MangaPage {
        val items = runCatching { withTimeoutOrNull(EN_TIMEOUT_MS) { browseEnglish(page) } }.getOrNull().orEmpty()
        return MangaPage(items, items.size, 0)
    }

    /** Popular Komiku titles; [page] is 1-based, 10 per page. */
    suspend fun popularKomiku(page: Int = 1, type: MangaType? = null): MangaPage =
        komiku?.browse("meta_value_num", page, type?.slug) ?: MangaPage(emptyList(), 0, 0)

    fun language(): String = appLanguage()

    /** A MangaPark/MangaKakalot entry for a title (tries each name), or null. */
    suspend fun findOnComix(names: List<String>): Manga? = findEnglish(names)

    private suspend fun findEnglish(names: List<String>): Manga? {
        for (name in names.filter { it.isNotBlank() }.distinct().take(3)) {
            val hits = runCatching { searchEnglish(name) }.getOrNull().orEmpty()
            val hit = hits.firstOrNull { sameTitle(name, it.title, it.altTitles) } ?: hits.firstOrNull()
            if (hit != null && names.any { sameTitle(it, hit.title, hit.altTitles) }) return hit
        }
        return runCatching { mangaPark?.find(names) }.getOrNull() ?: runCatching { kakaManga?.find(names) }.getOrNull()
    }

    /** The first source that has a title by one of [names]. Never throws. */
    suspend fun findAnywhere(names: List<String>): Manga? {
        suspend fun look(block: suspend () -> Manga?) = runCatching { withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { block() } }.getOrNull()
        return look { findEnglish(names) }
            ?: look { shinigami?.find(names) }
            ?: look { komiku?.find(names) }
            ?: look { webtoon?.find(names) }
    }

    /** [manga] plus the same title on the other sources; a lookup that fails or takes over timeout is left out. */
    suspend fun sources(manga: Manga): List<Manga> = sourceCache[manga.id] ?: coroutineScope {
        val names = listOf(manga.title) + manga.altTitles.filter { n -> n.count(Char::isLetterOrDigit) >= 5 }
        suspend fun look(block: suspend () -> Manga?) = runCatching { withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { block() } }.getOrNull()
        val others = listOfNotNull(
            if (isShinigamiId(manga.id)) null else async { look { shinigami?.find(names) } },
            if (isKomikuId(manga.id)) null else async { look { komiku?.find(names) } },
            if (isWebtoonId(manga.id)) null else async { look { webtoon?.find(names) } },
            if (isKakaMangaId(manga.id)) null else async { look { kakaManga?.find(names) } },
            if (isMangaParkId(manga.id)) null else async { look { mangaPark?.find(names) } },
        ).awaitAll().filterNotNull()
        (listOf(manga) + others).distinctBy { it.id }.also { sourceCache[manga.id] = it }
    }

    /** Every chapter of [manga] on every source, all languages. Fails only if every source fails. */
    suspend fun mergedChapters(manga: Manga): List<MangaChapter> = mergedCache[manga.id] ?: coroutineScope {
        val found = sources(manga)
        val results = found.mapIndexed { i, m ->
            async {
                runCatching {
                    if (i == 0) withTimeoutOrNull(PRIMARY_CHAPTERS_TIMEOUT_MS) { chapters(m.id) }
                        ?: throw java.io.IOException("Chapters unavailable")
                    else withTimeoutOrNull(OTHER_CHAPTERS_TIMEOUT_MS) { chapters(m.id) }.orEmpty()
                }
            }
        }.awaitAll()
        val merged = results.flatMap { it.getOrDefault(emptyList()) }
        if (merged.isNotEmpty()) return@coroutineScope merged.also { mergedCache[manga.id] = it }
        // Primary yielded nothing: actively discover alternatives by title.
        if (results.firstOrNull()?.isFailure == true || merged.isEmpty()) {
            val names = (listOf(manga.title) + manga.altTitles).filter { it.isNotBlank() }.distinct()
            val knownIds = found.mapTo(HashSet()) { it.id }
            val alts = findAlternatives(names).filter { it.id !in knownIds }
            if (alts.isNotEmpty()) {
                val extra = alts.map { m ->
                    async { runCatching { withTimeoutOrNull(OTHER_CHAPTERS_TIMEOUT_MS) { chapters(m.id) }.orEmpty() } }
                }.awaitAll().flatMap { it.getOrDefault(emptyList()) }
                if (extra.isNotEmpty()) return@coroutineScope extra.also { mergedCache[manga.id] = it }
            }
        }
        if (results.all { it.isFailure }) throw (results.first().exceptionOrNull() ?: notFound())
        merged.also { mergedCache[manga.id] = it }
    }

    fun clearMerged(mangaId: String) {
        sourceCache.remove(mangaId)
        mergedCache.remove(mangaId)
    }

    private suspend fun findAlternatives(names: List<String>): List<Manga> = coroutineScope {
        val lookups = buildList<suspend () -> Manga?> {
            if (mangaPark != null) add { mangaPark.find(names) }
            if (kakaManga != null) add { kakaManga.find(names) }
            if (webtoon != null) add { webtoon.find(names) }
            if (shinigami != null) add { shinigami.find(names) }
            if (komiku != null) add { komiku.find(names) }
        }
        if (lookups.isEmpty()) return@coroutineScope emptyList()
        lookups.map { fn -> async { runCatching { withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { fn() } }.getOrNull() } }
            .awaitAll().filterNotNull().distinctBy { it.id }
    }

    suspend fun detail(id: String): Manga {
        if (isShinigamiId(id)) return shinigami?.detail(id) ?: throw notFound()
        if (isKomikuId(id)) return komiku?.detail(id) ?: throw notFound()
        if (isWebtoonId(id)) return webtoon?.detail(id) ?: throw notFound()
        if (isKakaMangaId(id)) return kakaManga?.detail(id) ?: throw notFound()
        if (isMangaParkId(id)) return mangaPark?.detail(id) ?: throw notFound()
        throw notFound()
    }

    suspend fun chapters(id: String): List<MangaChapter> {
        if (isShinigamiId(id)) return shinigami?.chapters(id).orEmpty()
        if (isKomikuId(id)) return komiku?.chapters(id).orEmpty()
        if (isWebtoonId(id)) return webtoon?.chapters(id).orEmpty()
        if (isKakaMangaId(id)) return kakaManga?.chapters(id).orEmpty()
        if (isMangaParkId(id)) return mangaPark?.chapters(id).orEmpty()
        return emptyList()
    }

    /** Release feed (best-effort). Comix removed; returns cached/empty if unavailable. */
    suspend fun latestOngoingChapters(limit: Int = 100): List<MangaRelease> {
        val cached = releaseCache?.takeIf { now() - it.first < RELEASES_TTL_MS }
        if (cached != null) return cached.second
        // No Comix feed; keep notifications non-fatal.
        val releases = emptyList<MangaRelease>()
        releaseCache = now() to releases
        return releases
    }

    suspend fun pages(chapterId: String, dataSaver: Boolean = false, forceRefresh: Boolean = false): List<String> {
        if (isShinigamiId(chapterId)) return shinigami?.pages(chapterId).orEmpty()
        if (isKomikuId(chapterId)) return komiku?.pages(chapterId).orEmpty()
        if (isWebtoonId(chapterId)) return webtoon?.pages(chapterId).orEmpty()
        if (isKakaMangaId(chapterId)) return kakaManga?.pages(chapterId).orEmpty()
        if (isMangaParkId(chapterId)) return mangaPark?.pages(chapterId).orEmpty()
        return emptyList()
    }

    /** Aggregated genre list from all anime/manga sources used by FuwaStream (deduped, cleaned). */
    suspend fun tags(): List<MangaTag> = mangaGenresFromAllSources().map { name -> MangaTag(id = name, name = name, group = "genre") }

    private fun notFound() = UiTextException(UiText(R.string.manga_error_not_found))

    private companion object {
        const val PAGE_SIZE = 30
        const val LOOKUP_TIMEOUT_MS = 8_000L
        const val OTHER_CHAPTERS_TIMEOUT_MS = 15_000L
        const val PRIMARY_CHAPTERS_TIMEOUT_MS = 20_000L
        const val EN_TIMEOUT_MS = 20_000L
        const val RELEASES_TTL_MS = 30 * 60_000L
    }
}

/** Dedupe manga list by normalized title/alt-titles. */
internal fun dedupeByTitle(items: List<Manga>): List<Manga> {
    val out = mutableListOf<Manga>()
    for (m in items) {
        val exists = out.any { sameTitle(m.title, it.title, it.altTitles) || sameTitle(it.title, m.title, m.altTitles) }
        if (!exists) out += m
    }
    return out
}
