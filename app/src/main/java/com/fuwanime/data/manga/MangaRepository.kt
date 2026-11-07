package com.fuwanime.data.manga

import com.fuwanime.R
import com.fuwanime.util.UiText
import com.fuwanime.util.UiTextException
import java.util.concurrent.ConcurrentHashMap
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
) {
    private val atHomeCache = ConcurrentHashMap<String, Pair<Long, AtHomeDto>>()
    @Volatile private var tagCache: List<MangaTag>? = null

    suspend fun browse(
        order: MangaOrder,
        query: String = "",
        tagIds: List<String> = emptyList(),
        status: MangaStatus? = null,
        offset: Int = 0,
    ): MangaPage {
        val dto = call {
            api.mangaList(
                limit = PAGE_SIZE, offset = offset,
                title = query.trim().ifEmpty { null },
                byFollowed = if (order == MangaOrder.POPULAR) "desc" else null,
                byLatest = if (order == MangaOrder.LATEST) "desc" else null,
                byRelevance = if (order == MangaOrder.RELEVANCE) "desc" else null,
                langs = CATALOG_LANGS, ratings = RATINGS, tags = tagIds,
                status = listOfNotNull(status?.takeIf { it != MangaStatus.UNKNOWN }?.name?.lowercase()),
                includes = listOf("cover_art"),
                // Only titles with at least one uploaded chapter in id/en; others would open to an empty chapter list.
                hasAvailableChapters = true,
            )
        }
        val lang = appLanguage()
        return MangaPage(dto.data.map { it.toManga(lang, 256) }, dto.total, offset)
    }

    /** Popular Indonesian titles (Shinigami). */
    suspend fun popularIndonesia(page: Int = 1): MangaPage = shinigami?.browse("popularity", null, page) ?: MangaPage(emptyList(), 0, 0)

    /** Shinigami search results for [query]; empty when the source is off or fails. */
    suspend fun searchIndonesia(query: String): List<Manga> =
        runCatching { shinigami?.browse(null, query)?.items }.getOrNull().orEmpty()

    /** The same title on Shinigami, for MangaDex titles with nothing readable in the app. */
    suspend fun findOnShinigami(manga: Manga): Manga? = shinigami?.find(listOf(manga.title) + manga.altTitles)

    suspend fun detail(id: String): Manga {
        if (isShinigamiId(id)) return shinigami?.detail(id) ?: throw notFound()
        val dto = call { api.manga(id, listOf("cover_art", "author")) }.data ?: throw notFound()
        return dto.toManga(appLanguage(), 512)
    }

    suspend fun chapters(id: String): List<MangaChapter> {
        if (isShinigamiId(id)) return shinigami?.chapters(id).orEmpty()
        val all = ArrayList<MangaChapter>()
        var offset = 0
        while (true) {
            val feed = call { api.feed(id, FEED_LIMIT, offset, CATALOG_LANGS, RATINGS, listOf("scanlation_group")) }
            all += feed.data.map { it.toChapter() }
            offset += FEED_LIMIT
            if (feed.data.isEmpty() || offset >= feed.total) break
        }
        return pickChapters(all, chapterLanguageFor(appLanguage()))
    }

    suspend fun pages(chapterId: String, dataSaver: Boolean, forceRefresh: Boolean = false): List<String> {
        if (isShinigamiId(chapterId)) return shinigami?.pages(chapterId).orEmpty()
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

    private fun notFound() = UiTextException(UiText(R.string.manga_error_not_found))

    private companion object {
        const val PAGE_SIZE = 30
        const val FEED_LIMIT = 500
        const val BASE_URL_TTL_MS = 14 * 60_000L
        val CATALOG_LANGS = listOf("id", "en")
        val RATINGS = listOf("safe", "suggestive")
    }
}
