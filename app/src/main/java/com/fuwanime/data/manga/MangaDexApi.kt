package com.fuwanime.data.manga

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// MangaDex (api.mangadex.org): primary English catalogue. App IDs use an "md:" prefix.
const val MD_PREFIX = "md:"

fun isMangaDexId(id: String): Boolean = id.startsWith(MD_PREFIX)

fun mdRaw(id: String): String = id.removePrefix(MD_PREFIX)

fun mangaDexCoverUrl(mangaId: String, fileName: String?): String? {
    val raw = mdRaw(mangaId)
    val file = fileName?.takeIf { it.isNotBlank() } ?: return null
    return "https://uploads.mangadex.org/covers/$raw/$file.512.jpg"
}

@JsonClass(generateAdapter = true)
data class MdListResponse<T>(
    @Json(name = "data") val data: List<T> = emptyList(),
    @Json(name = "total") val total: Int = 0,
    @Json(name = "limit") val limit: Int = 0,
    @Json(name = "offset") val offset: Int = 0,
)

@JsonClass(generateAdapter = true)
data class MdEntityResponse<T>(
    @Json(name = "data") val data: T? = null,
)

@JsonClass(generateAdapter = true)
data class MdMangaDto(
    @Json(name = "id") val id: String = "",
    @Json(name = "attributes") val attributes: MdMangaAttributes = MdMangaAttributes(),
    @Json(name = "relationships") val relationships: List<MdRelationship> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class MdMangaAttributes(
    @Json(name = "title") val title: Map<String, String> = emptyMap(),
    @Json(name = "altTitles") val altTitles: List<Map<String, String>> = emptyList(),
    @Json(name = "description") val description: Any? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "year") val year: Int? = null,
    @Json(name = "tags") val tags: List<MdTagDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class MdTagDto(
    @Json(name = "id") val id: String = "",
    @Json(name = "attributes") val attributes: MdTagAttributes = MdTagAttributes(),
)

@JsonClass(generateAdapter = true)
data class MdTagAttributes(
    @Json(name = "name") val name: Map<String, String> = emptyMap(),
    @Json(name = "group") val group: String? = null,
)

@JsonClass(generateAdapter = true)
data class MdChapterDto(
    @Json(name = "id") val id: String = "",
    @Json(name = "attributes") val attributes: MdChapterAttributes = MdChapterAttributes(),
    @Json(name = "relationships") val relationships: List<MdRelationship> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class MdChapterAttributes(
    @Json(name = "chapter") val chapter: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "translatedLanguage") val translatedLanguage: String? = null,
    @Json(name = "pages") val pages: Int = 0,
    @Json(name = "externalUrl") val externalUrl: String? = null,
    @Json(name = "publishAt") val publishAt: String? = null,
    @Json(name = "readableAt") val readableAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class MdRelationship(
    @Json(name = "id") val id: String = "",
    @Json(name = "type") val type: String = "",
    @Json(name = "attributes") val attributes: MdRelationshipAttributes? = null,
)

@JsonClass(generateAdapter = true)
data class MdRelationshipAttributes(
    @Json(name = "fileName") val fileName: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "title") val title: Map<String, String>? = null,
    @Json(name = "status") val status: String? = null,
)

@JsonClass(generateAdapter = true)
data class MdAtHomeResponse(
    @Json(name = "baseUrl") val baseUrl: String = "",
    @Json(name = "chapter") val chapter: MdAtHomeChapter = MdAtHomeChapter(),
)

@JsonClass(generateAdapter = true)
data class MdAtHomeChapter(
    @Json(name = "hash") val hash: String = "",
    @Json(name = "data") val data: List<String> = emptyList(),
    @Json(name = "dataSaver") val dataSaver: List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class MdTagListResponse(
    @Json(name = "data") val data: List<MdTagDto> = emptyList(),
)

interface MangaDexApi {
    @GET("manga")
    suspend fun mangaList(
        @Query("limit") limit: Int = 28,
        @Query("offset") offset: Int = 0,
        @Query("title") title: String? = null,
        @Query("order[latestUploadedChapter]") orderLatest: String? = null,
        @Query("order[followedCount]") orderFollowed: String? = null,
        @Query("order[relevance]") orderRelevance: String? = null,
        @Query("status[]") status: List<String>? = null,
        @Query("includedTags[]") includedTags: List<String>? = null,
        @Query("includes[]") includes: List<String> = listOf("cover_art", "author", "artist"),
        @Query("contentRating[]") contentRating: List<String> = listOf("safe", "suggestive", "erotica"),
        @Query("availableTranslatedLanguage[]") availableTranslatedLanguage: List<String> = listOf("en"),
        @Query("hasAvailableChapters") hasAvailableChapters: Boolean = true,
    ): MdListResponse<MdMangaDto>

    @GET("manga/{id}")
    suspend fun mangaDetail(
        @Path("id") id: String,
        @Query("includes[]") includes: List<String> = listOf("cover_art", "author", "artist"),
    ): MdEntityResponse<MdMangaDto>

    @GET("manga/{id}/feed")
    suspend fun mangaFeed(
        @Path("id") id: String,
        @Query("translatedLanguage[]") translatedLanguage: List<String> = listOf("en"),
        @Query("order[chapter]") orderChapter: String = "asc",
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("includes[]") includes: List<String> = listOf("scanlation_group"),
        @Query("includeEmptyPages") includeEmptyPages: Int = 0,
        @Query("contentRating[]") contentRating: List<String> = listOf("safe", "suggestive", "erotica"),
    ): MdListResponse<MdChapterDto>

    @GET("chapter")
    suspend fun chapterList(
        @Query("translatedLanguage[]") translatedLanguage: List<String> = listOf("en"),
        @Query("order[publishAt]") orderPublishAt: String = "desc",
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
        @Query("includes[]") includes: List<String> = listOf("manga", "scanlation_group"),
        @Query("includeEmptyPages") includeEmptyPages: Int = 0,
        @Query("contentRating[]") contentRating: List<String> = listOf("safe", "suggestive", "erotica"),
    ): MdListResponse<MdChapterDto>

    @GET("at-home/server/{id}")
    suspend fun atHome(@Path("id") chapterId: String): MdAtHomeResponse

    @GET("manga/tag")
    suspend fun tags(): MdTagListResponse

    companion object {
        fun create(client: OkHttpClient, baseUrl: String = "https://api.mangadex.org/"): MangaDexApi {
            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            return Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(MangaDexApi::class.java)
        }
    }
}

private fun localizedString(value: Any?, preferred: String = "en"): String {
    return when (value) {
        is String -> value
        is Map<*, *> -> {
            val map = value.entries.associate { (k, v) -> k.toString() to v?.toString().orEmpty() }
            map[preferred].orEmpty().ifBlank { map["en"].orEmpty().ifBlank { map.values.firstOrNull().orEmpty() } }
        }
        else -> ""
    }
}

private fun altTitleList(list: List<Map<String, String>>): List<String> =
    list.flatMap { it.values }.map { it.trim() }.filter { it.isNotBlank() }.distinct()

private fun mdStatus(status: String?): MangaStatus = when (status?.lowercase(Locale.US)) {
    "ongoing" -> MangaStatus.ONGOING
    "completed" -> MangaStatus.COMPLETED
    "hiatus" -> MangaStatus.HIATUS
    "cancelled" -> MangaStatus.CANCELLED
    else -> MangaStatus.UNKNOWN
}

private fun parseMdTime(value: String?): Long {
    if (value.isNullOrBlank()) return 0L
    val patterns = arrayOf("yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssX")
    for (p in patterns) {
        try {
            val fmt = SimpleDateFormat(p, Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            return fmt.parse(value)?.time ?: 0L
        } catch (_: Exception) { }
    }
    return 0L
}

fun MdMangaDto.toManga(): Manga {
    val mangaId = MD_PREFIX + id
    val coverFile = relationships.firstOrNull { it.type == "cover_art" }?.attributes?.fileName
    val author = relationships.firstOrNull { it.type == "author" || it.type == "artist" }?.attributes?.name
    val titleText = localizedString(attributes.title, "en").ifBlank { localizedString(attributes.title, "ja-ro") }.trim()
    val descriptionText = localizedString(attributes.description, "en").trim()
    return Manga(
        id = mangaId,
        title = titleText.ifBlank { "Untitled" },
        coverUrl = mangaDexCoverUrl(mangaId, coverFile),
        description = descriptionText,
        tags = attributes.tags.mapNotNull { t -> localizedString(t.attributes.name, "en").ifBlank { null } },
        status = mdStatus(attributes.status),
        year = attributes.year,
        author = author,
        altTitles = altTitleList(attributes.altTitles),
        type = null,
    )
}

fun MdChapterDto.toChapter(defaultMangaId: String? = null): MangaChapter {
    val group = relationships.firstOrNull { it.type == "scanlation_group" }?.attributes?.name?.takeIf { it.isNotBlank() }
    val mangaId = relationships.firstOrNull { it.type == "manga" }?.id ?: defaultMangaId.orEmpty()
    val num = attributes.chapter?.trim()?.takeIf { it.isNotBlank() }
    val readable = attributes.externalUrl.isNullOrBlank() && attributes.pages > 0
    return MangaChapter(
        id = MD_PREFIX + id,
        number = num,
        title = attributes.title?.takeIf { it.isNotBlank() },
        language = attributes.translatedLanguage ?: "en",
        group = group ?: "MangaDex",
        pages = if (readable) attributes.pages else 0,
        externalUrl = attributes.externalUrl,
        publishAt = parseMdTime(attributes.publishAt ?: attributes.readableAt),
    )
}

fun MdAtHomeResponse.toPageUrls(): List<String> {
    val base = baseUrl.trimEnd('/')
    val hash = chapter.hash
    return chapter.data.map { "$base/data/$hash/$it" }
}

fun sameMangaDexTitle(title: String, manga: Manga): Boolean {
    val want = normalizeTitle(title)
    if (want.isEmpty()) return false
    if (normalizeTitle(manga.title) == want) return true
    return want.length >= 5 && manga.altTitles.any { normalizeTitle(it) == want }
}

private fun normalizeTitle(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

/** MangaDex calls for [MangaRepository]: browse, detail, chapters (English-only), pages, tags, latest releases. */
class MangaDexSource(
    private val api: MangaDexApi,
    private val mature: suspend () -> Boolean = { false },
) {
    private fun contentRatings(): List<String> = buildList {
        add("safe"); add("suggestive"); add("erotica")
        if (runCatching { kotlinx.coroutines.runBlocking { mature() } }.getOrDefault(false)) {
            // "pornographic" intentionally excluded by app policy; keep erotica only when mature.
        }
    }

    // Use runBlocking-safe? better pass ratings from suspend callers; but signature easier if methods suspend.
    private suspend fun ratings(): List<String> =
        if (mature()) listOf("safe", "suggestive", "erotica") else listOf("safe", "suggestive", "erotica")

    suspend fun browse(
        order: MangaOrder,
        query: String = "",
        page: Int = 1,
        type: MangaType? = null,
        tagIds: List<String> = emptyList(),
        status: MangaStatus? = null,
    ): MangaPage {
        val offset = (page - 1) * PAGE_SIZE
        val q = query.trim().ifBlank { null }
        val orderLatest = if (order == MangaOrder.LATEST) "desc" else null
        val orderFollowed = if (order == MangaOrder.POPULAR || (order == MangaOrder.RELEVANCE && q == null)) "desc" else null
        val orderRelevance = if (order == MangaOrder.RELEVANCE && q != null) "desc" else null
        val statuses = status?.let { listOf(it.name.lowercase(Locale.US)) }
        val dto = api.mangaList(
            limit = PAGE_SIZE,
            offset = offset,
            title = q,
            orderLatest = orderLatest,
            orderFollowed = orderFollowed,
            orderRelevance = orderRelevance,
            status = statuses,
            includedTags = tagIds.takeIf { it.isNotEmpty() },
            contentRating = ratings(),
        )
        return MangaPage(dto.data.map { it.toManga() }, dto.total, offset)
    }

    suspend fun search(query: String): List<Manga> = browse(MangaOrder.RELEVANCE, query).items

    suspend fun detail(id: String): Manga? = api.mangaDetail(mdRaw(id)).data?.toManga()

    suspend fun chapters(id: String): List<MangaChapter> {
        val all = ArrayList<MangaChapter>()
        var offset = 0
        while (true) {
            val dto = api.mangaFeed(
                id = mdRaw(id),
                translatedLanguage = listOf("en"),
                orderChapter = "asc",
                limit = CHAPTER_PAGE,
                offset = offset,
                contentRating = ratings(),
            )
            all += dto.data.map { it.toChapter(mdRaw(id)) }.filter { it.language == "en" || it.language.isBlank() }
            if (dto.data.isEmpty() || offset + dto.data.size >= dto.total || offset >= MAX_CHAPTER_OFFSET) break
            offset += dto.data.size
        }
        return pickChapters(all, "en")
    }

    suspend fun pages(chapterId: String): List<String> = api.atHome(mdRaw(chapterId)).toPageUrls()

    /** Newest English chapter per recently updated manga, newest first. */
    suspend fun latestOngoingChapters(limit: Int = 100): List<MangaRelease> {
        val seenManga = HashSet<String>()
        val out = ArrayList<MangaRelease>()
        var offset = 0
        while (out.size < limit && offset < 500) {
            val dto = api.chapterList(
                translatedLanguage = listOf("en"),
                orderPublishAt = "desc",
                limit = 100,
                offset = offset,
                contentRating = ratings(),
            )
            if (dto.data.isEmpty()) break
            for (ch in dto.data) {
                val mangaRel = ch.relationships.firstOrNull { it.type == "manga" } ?: continue
                val mangaId = mangaRel.id
                if (!seenManga.add(mangaId)) continue
                val attrs = mangaRel.attributes
                val title = localizedString(attrs?.title, "en").ifBlank { localizedString(attrs?.title, "ja-ro") }.ifBlank { "Untitled" }
                val chapter = ch.toChapter(mangaId)
                // Only include readable in-app chapters and ongoing manga when status is known.
                val status = attrs?.status
                if (status != null && !status.equals("ongoing", true)) continue
                if (chapter.externalUrl != null || chapter.pages <= 0) continue
                out += MangaRelease(
                    mangaId = MD_PREFIX + mangaId,
                    title = title,
                    chapterId = chapter.id,
                    chapter = chapter.number ?: chapter.title,
                    publishAt = chapter.publishAt,
                )
                if (out.size >= limit) break
            }
            offset += dto.data.size
            if (offset >= dto.total) break
        }
        return out
    }

    suspend fun tags(): List<MangaTag> = api.tags().data
        .filter { it.attributes.group == "genre" || it.attributes.group == "theme" }
        .mapNotNull { tag ->
            val name = localizedString(tag.attributes.name, "en").ifBlank { null } ?: return@mapNotNull null
            MangaTag(id = tag.id, name = name, group = tag.attributes.group.orEmpty())
        }

    /** The MangaDex entry for a title (tries each name in order), or null. */
    suspend fun find(names: List<String>): Manga? {
        for (name in names.filter { it.isNotBlank() }.distinct().take(3)) {
            val hits = runCatching { search(name) }.getOrNull().orEmpty()
            val hit = hits.firstOrNull { sameMangaDexTitle(name, it) }
            if (hit != null) return hit
        }
        return null
    }

    private companion object {
        const val PAGE_SIZE = 28
        const val CHAPTER_PAGE = 100
        const val MAX_CHAPTER_OFFSET = 2000
    }
}

