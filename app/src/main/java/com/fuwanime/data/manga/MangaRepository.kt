package com.fuwanime.data.manga

import com.fuwanime.R
import com.fuwanime.data.isMatureAnimeGenre
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

/** Rawkuma is searched by title, or browsed only under the explicit Manga chip. */
internal fun shouldUseRawkuma(query: String, type: MangaType?, hasGenreFilter: Boolean): Boolean =
    type != MangaType.MANHWA && type != MangaType.MANHUA &&
        (query.isNotBlank() || (type == MangaType.MANGA && !hasGenreFilter))

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
    /** Raw Japanese manga source; ids start with "rk:". */
    private val rawkuma: RawkumaSource? = null,
    /** "Konten dewasa" setting: explicit genres stay out of the genre sheet while it is off. */
    private val mature: suspend () -> Boolean = { false },
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
        // Rawkuma is raw Japanese manga: never let it fill Manhwa/Manhua results.
        val useRawkuma = shouldUseRawkuma(q, type, tagIds.isNotEmpty())
        if (q.isNotBlank()) {
            val items = coroutineScope {
                val english = async { searchEnglish(q) }
                val raw = async { if (useRawkuma) searchRawkuma(q) else emptyList() }
                // English stays first: when both sources have a title, keep its translated entry in the grid.
                dedupeByTitle(english.await() + raw.await())
            }
            return MangaPage(items, items.size, 0)
        }
        // No native tag/status filter on MP/KK; return general listings and let UI degrade gracefully.
        val pageNum = (offset / PAGE_SIZE).coerceAtLeast(0) + 1
        // Rawkuma joins only the explicit Manga chip; its listing carries no genres to filter by.
        val items = coroutineScope {
            val english = async { browseEnglish(pageNum) }
            val raw = async { if (useRawkuma) browseRawkuma(pageNum) else emptyList() }
            dedupeByTitle(english.await() + raw.await())
        }
        val total = if (items.size >= PAGE_SIZE / 2) offset + items.size + PAGE_SIZE else offset + items.size
        return MangaPage(items, total, offset)
    }

    private suspend fun searchRawkuma(query: String): List<Manga> = try {
        withTimeoutOrNull(RAWKUMA_TIMEOUT_MS) { rawkuma?.search(query).orEmpty() }.orEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    private suspend fun browseRawkuma(page: Int): List<Manga> = try {
        withTimeoutOrNull(RAWKUMA_TIMEOUT_MS) { rawkuma?.browse(page)?.items.orEmpty() }.orEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
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
    suspend fun searchIndonesia(query: String): List<Manga> = coroutineScope {
        suspend fun <T> quiet(block: suspend () -> List<T>): List<T> =
            runCatching { withTimeoutOrNull(ID_TIMEOUT_MS) { block() } }.getOrNull().orEmpty()
        val shinigamiHits = async { quiet { shinigami?.browse(null, query)?.items.orEmpty() } }
        val komikuHits = async { quiet { komiku?.search(query).orEmpty() } }
        val webtoonHits = async { quiet { webtoon?.search(query).orEmpty() } }
        shinigamiHits.await() + komikuHits.await() + webtoonHits.await()
    }

    /** Paged Indonesian search (Shinigami supports pages); Komiku/Webtoon included on page 1. */
    suspend fun searchIndonesiaPaged(query: String, page: Int = 1): MangaPage = coroutineScope {
        suspend fun <T> quiet(block: suspend () -> List<T>): List<T> =
            runCatching { withTimeoutOrNull(ID_TIMEOUT_MS) { block() } }.getOrNull().orEmpty()
        val shinigamiPage = async {
            runCatching { withTimeoutOrNull(ID_TIMEOUT_MS) { shinigami?.browse(null, query, page) } }.getOrNull()
        }
        val komikuHits = async { if (page == 1) quiet { komiku?.search(query).orEmpty() } else emptyList() }
        val webtoonHits = async { if (page == 1) quiet { webtoon?.search(query).orEmpty() } else emptyList() }
        val sh = shinigamiPage.await()
        val items = (sh?.items.orEmpty() + komikuHits.await() + webtoonHits.await()).distinctBy { it.id }
        val shTotal = sh?.total ?: 0
        val shHasMore = sh != null && sh.items.isNotEmpty() && page * PAGE_SIZE < shTotal
        val total = when {
            shHasMore -> (page * PAGE_SIZE) + 1
            page == 1 -> items.size
            else -> (page - 1) * PAGE_SIZE + (sh?.items?.size ?: 0)
        }
        MangaPage(items, total, (page - 1) * PAGE_SIZE)
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
        val total = if (items.size >= PAGE_SIZE / 2) Int.MAX_VALUE else items.size
        return MangaPage(items, total, (page - 1) * PAGE_SIZE)
    }

    /** Popular Komiku titles; [page] is 1-based, 10 per page. */
    suspend fun popularKomiku(page: Int = 1, type: MangaType? = null, genre: String? = null): MangaPage =
        komiku?.browse("meta_value_num", page, type?.slug, genre) ?: MangaPage(emptyList(), 0, 0)

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
            ?: look { rawkuma?.find(names) }
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
            rawkuma?.takeIf { !isRawkumaId(manga.id) && manga.allowsRawkuma() }
                ?.let { source -> async { look { source.find(names) } } },
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
            val alts = findAlternatives(names, includeRaw = manga.allowsRawkuma()).filter { it.id !in knownIds }
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

    private suspend fun findAlternatives(names: List<String>, includeRaw: Boolean = true): List<Manga> = coroutineScope {
        val lookups = buildList<suspend () -> Manga?> {
            if (mangaPark != null) add { mangaPark.find(names) }
            if (kakaManga != null) add { kakaManga.find(names) }
            if (webtoon != null) add { webtoon.find(names) }
            if (shinigami != null) add { shinigami.find(names) }
            if (komiku != null) add { komiku.find(names) }
            if (includeRaw && rawkuma != null) add { rawkuma.find(names) }
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
        if (isRawkumaId(id)) return rawkuma?.detail(id) ?: throw notFound()
        throw notFound()
    }

    suspend fun chapters(id: String): List<MangaChapter> {
        if (isShinigamiId(id)) return shinigami?.chapters(id).orEmpty()
        if (isKomikuId(id)) return komiku?.chapters(id).orEmpty()
        if (isWebtoonId(id)) return webtoon?.chapters(id).orEmpty()
        if (isKakaMangaId(id)) return kakaManga?.chapters(id).orEmpty()
        if (isMangaParkId(id)) return mangaPark?.chapters(id).orEmpty()
        if (isRawkumaId(id)) return rawkuma?.chapters(id).orEmpty()
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
        if (isRawkumaId(chapterId)) return rawkuma?.pages(chapterId).orEmpty()
        return emptyList()
    }

    /** Aggregated genre list from all anime/manga sources used by FuwaStream (deduped, cleaned). */
    suspend fun tags(): List<MangaTag> = mangaGenresFromAllSources()
        .filter { mature() || !isMatureAnimeGenre(it) }
        .map { name -> MangaTag(id = name, name = name, group = "genre") }

    /** Rawkuma has Japanese originals; it is only relevant to Manga entries (unknown type still passes). */
    private fun Manga.allowsRawkuma(): Boolean =
        type == null || (!type.equals("Manhwa", ignoreCase = true) && !type.equals("Manhua", ignoreCase = true))

    private fun notFound() = UiTextException(UiText(R.string.manga_error_not_found))

    private companion object {
        const val PAGE_SIZE = 30
        const val LOOKUP_TIMEOUT_MS = 8_000L
        /** Indonesian sources answer fast or not at all; don't let one hold the others' results back. */
        const val ID_TIMEOUT_MS = 8_000L
        const val OTHER_CHAPTERS_TIMEOUT_MS = 15_000L
        const val PRIMARY_CHAPTERS_TIMEOUT_MS = 20_000L
        const val EN_TIMEOUT_MS = 20_000L
        /** Cloudflare sometimes stalls Rawkuma; never hold the other sources' results for it. */
        const val RAWKUMA_TIMEOUT_MS = 12_000L
        const val RELEASES_TTL_MS = 30 * 60_000L
    }
}
