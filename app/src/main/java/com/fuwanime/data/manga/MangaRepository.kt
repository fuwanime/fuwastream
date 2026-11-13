package com.fuwanime.data.manga

import com.fuwanime.R
import com.fuwanime.util.UiText
import com.fuwanime.util.UiTextException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException

/** RELEVANCE only makes sense together with a title query. */
enum class MangaOrder { POPULAR, LATEST, RELEVANCE }

data class MangaPage(val items: List<Manga>, val total: Int, val offset: Int)

class MangaRepository(
    private val api: MangaDexApi,
    private val appLanguage: () -> String,
    private val now: () -> Long = System::currentTimeMillis,
    /** Second source (Indonesian); ids starting with "sh:" are routed there. */
    private val shinigami: ShinigamiSource? = null,
    /** Third source (Indonesian, large archive of older titles); ids starting with "km:". */
    private val komiku: KomikuSource? = null,
    /** Fourth source (official, originals + canvas in id/en); ids starting with "wt:". */
    private val webtoon: WebtoonSource? = null,
    /** "Konten dewasa" setting: also show MangaDex titles rated erotica (e.g. Berserk, Vagabond); never pornographic. */
    private val mature: suspend () -> Boolean = { false },
) {
    private val atHomeCache = ConcurrentHashMap<String, Pair<Long, AtHomeDto>>()
    @Volatile private var tagCache: List<MangaTag>? = null
    private val sourceCache = ConcurrentHashMap<String, List<Manga>>()
    private val mergedCache = ConcurrentHashMap<String, List<MangaChapter>>()

    suspend fun browse(
        order: MangaOrder,
        query: String = "",
        tagIds: List<String> = emptyList(),
        status: MangaStatus? = null,
        offset: Int = 0,
        type: MangaType? = null,
    ): MangaPage {
        val dto = call {
            api.mangaList(
                limit = PAGE_SIZE, offset = offset,
                title = query.trim().ifEmpty { null },
                byFollowed = if (order == MangaOrder.POPULAR) "desc" else null,
                byLatest = if (order == MangaOrder.LATEST) "desc" else null,
                byRelevance = if (order == MangaOrder.RELEVANCE) "desc" else null,
                langs = CATALOG_LANGS, ratings = ratings(), tags = tagIds,
                status = listOfNotNull(status?.takeIf { it != MangaStatus.UNKNOWN }?.name?.lowercase()),
                includes = listOf("cover_art"),
                // Only titles with at least one uploaded chapter in id/en; others would open to an empty chapter list.
                hasAvailableChapters = true,
                originalLanguages = type?.mangaDex.orEmpty(),
            )
        }
        val lang = appLanguage()
        return MangaPage(dto.data.map { it.toManga(lang, 256) }, dto.total, offset)
    }

    /** Popular Indonesian titles (Shinigami). */
    suspend fun popularIndonesia(page: Int = 1, type: MangaType? = null): MangaPage =
        shinigami?.browse("popularity", null, page, type?.slug) ?: MangaPage(emptyList(), 0, 0)

    /** Shinigami search results for [query]; empty when the source is off or fails. */
    suspend fun searchIndonesia(query: String): List<Manga> =
        runCatching { shinigami?.browse(null, query)?.items }.getOrNull().orEmpty() +
            runCatching { komiku?.search(query) }.getOrNull().orEmpty() +
            runCatching { webtoon?.search(query) }.getOrNull().orEmpty()

    /** Popular Komiku titles; [page] is 1-based, 10 per page. */
    suspend fun popularKomiku(page: Int = 1, type: MangaType? = null): MangaPage =
        komiku?.browse("meta_value_num", page, type?.slug) ?: MangaPage(emptyList(), 0, 0)

    /** The app language ("id", "en", …) the repository uses for descriptions and the default chapter language. */
    fun language(): String = appLanguage()

    /** The MangaDex entry for a title (tries each name in order), or null. */
    suspend fun findOnMangaDex(names: List<String>): Manga? {
        for (name in names.filter { it.isNotBlank() }.distinct().take(3)) {
            val hit = runCatching { browse(MangaOrder.RELEVANCE, name).items }.getOrNull()
                ?.firstOrNull { sameTitle(name, it.title, it.altTitles) }
            if (hit != null) return hit
        }
        return null
    }

    /** [manga] plus the same title on the other sources; a lookup that fails or takes over 8 s is left out. */
    suspend fun sources(manga: Manga): List<Manga> = sourceCache[manga.id] ?: coroutineScope {
        // Short alt names ("Love") match unrelated titles; search by the title and longer alt names only.
        val names = listOf(manga.title) + manga.altTitles.filter { n -> n.count(Char::isLetterOrDigit) >= 5 }
        suspend fun look(block: suspend () -> Manga?) = runCatching { withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { block() } }.getOrNull()
        val others = listOfNotNull(
            if (isShinigamiId(manga.id)) null else async { look { shinigami?.find(names) } },
            if (isKomikuId(manga.id)) null else async { look { komiku?.find(names) } },
            if (isWebtoonId(manga.id)) null else async { look { webtoon?.find(names) } },
            if (isShinigamiId(manga.id) || isKomikuId(manga.id) || isWebtoonId(manga.id)) async { look { findOnMangaDex(names) } } else null,
        ).awaitAll().filterNotNull()
        (listOf(manga) + others).also { sourceCache[manga.id] = it }
    }

    /** Every chapter of [manga] on every source, all languages; [mergeChapters] picks per language. Fails only if every source fails. */
    suspend fun mergedChapters(manga: Manga): List<MangaChapter> = mergedCache[manga.id] ?: coroutineScope {
        // The opened title may take its time; another source that stalls is dropped instead of holding the list back.
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
        if (isWebtoonId(id)) return webtoon?.detail(id) ?: throw notFound()
        val dto = call { api.manga(id, listOf("cover_art", "author")) }.data ?: throw notFound()
        return dto.toManga(appLanguage(), 512)
    }

    suspend fun chapters(id: String): List<MangaChapter> {
        if (isShinigamiId(id)) return shinigami?.chapters(id).orEmpty()
        if (isKomikuId(id)) return komiku?.chapters(id).orEmpty()
        if (isWebtoonId(id)) return webtoon?.chapters(id).orEmpty()
        val all = ArrayList<MangaChapter>()
        var offset = 0
        while (true) {
            val feed = call { api.feed(id, FEED_LIMIT, offset, FEED_LANGS, ratings(), listOf("scanlation_group")) }
            all += feed.data.map { it.toChapter() }
            offset += FEED_LIMIT
            if (feed.data.isEmpty() || offset >= feed.total) break
        }
        return all
    }

    /** Newest app-language chapter per recently updated ongoing manga, newest first; feeds the release notifications. */
    suspend fun latestOngoingChapters(limit: Int = 100): List<MangaRelease> {
        val feed = call {
            api.latestChapters(limit = limit, langs = FEED_LANGS, ratings = ratings(), includes = listOf("manga"))
        }
        // Chapters scheduled for later must not seed a baseline before the user can read them.
        val now = System.currentTimeMillis()
        return latestPerManga(feed.data.mapNotNull { it.mangaRelease(now) })
    }

    suspend fun pages(chapterId: String, dataSaver: Boolean, forceRefresh: Boolean = false): List<String> {
        if (isShinigamiId(chapterId)) return shinigami?.pages(chapterId).orEmpty()
        if (isKomikuId(chapterId)) return komiku?.pages(chapterId).orEmpty()
        if (isWebtoonId(chapterId)) return webtoon?.pages(chapterId).orEmpty()
        val cached = atHomeCache[chapterId]?.takeIf { !forceRefresh && now() - it.first < BASE_URL_TTL_MS }
        val dto = cached?.second ?: call { api.atHome(chapterId) }.also { atHomeCache[chapterId] = now() to it }
        return pageUrls(dto.baseUrl, dto.chapter.hash, dto.chapter.data, dto.chapter.dataSaver, dataSaver)
    }

    suspend fun tags(): List<MangaTag> = tagCache ?: call { api.tags() }.data.map { it.toTag() }
        .sortedBy { it.name }.also { tagCache = it }

    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (e: HttpException) {
        if (e.code() == 404) throw notFound() else throw e
    }

    private suspend fun ratings() = if (mature()) RATINGS_MATURE else RATINGS

    private fun notFound() = UiTextException(UiText(R.string.manga_error_not_found))

    private companion object {
        const val PAGE_SIZE = 30
        const val FEED_LIMIT = 500
        const val BASE_URL_TTL_MS = 14 * 60_000L
        val CATALOG_LANGS = listOf("id", "en")
        // Chapter feeds also bring raw uploads for the Raw language chip.
        val FEED_LANGS = listOf("id", "en", "ja", "ko", "zh", "zh-hk")
        const val LOOKUP_TIMEOUT_MS = 8_000L
        const val OTHER_CHAPTERS_TIMEOUT_MS = 15_000L
        val RATINGS = listOf("safe", "suggestive")
        val RATINGS_MATURE = RATINGS + "erotica"
    }
}
