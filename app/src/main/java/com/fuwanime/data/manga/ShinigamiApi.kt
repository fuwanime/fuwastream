package com.fuwanime.data.manga

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.text.SimpleDateFormat
import java.util.Locale
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// Shinigami (api.shngm.io): Indonesian scanlation catalogue with a plain JSON API. Ids get an "sh:" prefix in the app.
const val SH_PREFIX = "sh:"

fun isShinigamiId(id: String): Boolean = id.startsWith(SH_PREFIX)
private fun String.raw() = removePrefix(SH_PREFIX)

@JsonClass(generateAdapter = true)
data class ShMetaDto(@Json(name = "total_record") val totalRecord: Int = 0, @Json(name = "total_page") val totalPage: Int = 0)

@JsonClass(generateAdapter = true)
data class ShListDto(@Json(name = "data") val data: List<ShMangaDto> = emptyList(), @Json(name = "meta") val meta: ShMetaDto = ShMetaDto())

@JsonClass(generateAdapter = true)
data class ShDetailDto(@Json(name = "data") val data: ShMangaDto? = null)

@JsonClass(generateAdapter = true)
data class ShTermDto(@Json(name = "name") val name: String = "")

@JsonClass(generateAdapter = true)
data class ShTaxonomyDto(
    @Json(name = "Genre") val genre: List<ShTermDto> = emptyList(),
    @Json(name = "Author") val author: List<ShTermDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class ShMangaDto(
    @Json(name = "manga_id") val mangaId: String = "",
    @Json(name = "title") val title: String = "",
    @Json(name = "alternative_title") val alternativeTitle: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "cover_portrait_url") val coverPortrait: String? = null,
    @Json(name = "cover_image_url") val cover: String? = null,
    @Json(name = "status") val status: Int? = null,
    @Json(name = "release_year") val releaseYear: String? = null,
    @Json(name = "taxonomy") val taxonomy: ShTaxonomyDto? = null,
)

@JsonClass(generateAdapter = true)
data class ShChapterListDto(@Json(name = "data") val data: List<ShChapterDto> = emptyList(), @Json(name = "meta") val meta: ShMetaDto = ShMetaDto())

@JsonClass(generateAdapter = true)
data class ShChapterDto(
    @Json(name = "chapter_id") val chapterId: String = "",
    @Json(name = "chapter_number") val chapterNumber: Double? = null,
    @Json(name = "chapter_title") val chapterTitle: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
)

@JsonClass(generateAdapter = true)
data class ShPagesDto(@Json(name = "data") val data: ShPagesDataDto? = null)

@JsonClass(generateAdapter = true)
data class ShPagesDataDto(
    @Json(name = "base_url") val baseUrl: String = "",
    @Json(name = "chapter") val chapter: ShChapterFilesDto = ShChapterFilesDto(),
)

@JsonClass(generateAdapter = true)
data class ShChapterFilesDto(@Json(name = "path") val path: String = "", @Json(name = "data") val data: List<String> = emptyList())

/** "932" for whole numbers, "10.5" otherwise. */
fun shChapterNumber(n: Double): String = if (n % 1.0 == 0.0) n.toLong().toString() else n.toString()

fun ShMangaDto.toManga() = Manga(
    id = SH_PREFIX + mangaId,
    title = title.trim(),
    coverUrl = (coverPortrait ?: cover)?.takeIf { it.isNotBlank() },
    description = description.orEmpty().trim(),
    tags = taxonomy?.genre.orEmpty().map { it.name }.filter { it.isNotBlank() },
    status = when (status) {
        1 -> MangaStatus.ONGOING
        2 -> MangaStatus.COMPLETED
        3 -> MangaStatus.HIATUS
        else -> MangaStatus.UNKNOWN
    },
    year = releaseYear?.toIntOrNull(),
    author = taxonomy?.author.orEmpty().map { it.name }.filter { it.isNotBlank() }.joinToString(", ").ifBlank { null },
)

// Pages are only known when the chapter is opened; 1 marks the chapter as readable in the app.
fun ShChapterDto.toChapter() = MangaChapter(
    id = SH_PREFIX + chapterId,
    number = chapterNumber?.let(::shChapterNumber),
    title = chapterTitle?.takeIf { it.isNotBlank() },
    language = "id",
    group = "Shinigami",
    pages = 1,
    externalUrl = null,
    publishAt = runCatching { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US).parse(releaseDate!!)!!.time }.getOrDefault(0L),
)

fun ShPagesDataDto.toPageUrls(): List<String> = chapter.data.map { baseUrl.trimEnd('/') + chapter.path + it }

private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

/** True when a Shinigami title (or one of its comma-separated alternative titles) is the same as [title]. */
fun sameTitle(title: String, shTitle: String, shAlternatives: List<String>): Boolean {
    val want = norm(title).ifEmpty { return false }
    return norm(shTitle) == want || shAlternatives.any { norm(it) == want }
}

fun ShMangaDto.alternatives(): List<String> = alternativeTitle.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }

interface ShinigamiApi {
    @GET("v1/manga/list")
    suspend fun list(
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
        @Query("sort") sort: String?,
        @Query("q") query: String?,
    ): ShListDto

    @GET("v1/manga/detail/{id}")
    suspend fun detail(@Path("id") id: String): ShDetailDto

    @GET("v1/chapter/{id}/list")
    suspend fun chapters(
        @Path("id") id: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
        @Query("sort_by") sortBy: String = "chapter_number",
        @Query("sort_order") sortOrder: String = "asc",
    ): ShChapterListDto

    @GET("v1/chapter/detail/{id}")
    suspend fun pages(@Path("id") chapterId: String): ShPagesDto

    companion object {
        fun create(client: OkHttpClient, baseUrl: String = "https://api.shngm.io/"): ShinigamiApi = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(ShinigamiApi::class.java)
    }
}

/** Shinigami calls for [MangaRepository]: browse, detail, chapters, pages, and finding a MangaDex title there. */
class ShinigamiSource(private val api: ShinigamiApi) {
    suspend fun browse(sort: String?, query: String?, page: Int = 1): MangaPage {
        val dto = api.list(page, PAGE_SIZE, sort, query?.trim()?.ifEmpty { null })
        return MangaPage(dto.data.map { it.toManga() }, dto.meta.totalRecord, (page - 1) * PAGE_SIZE)
    }

    suspend fun detail(id: String): Manga? = api.detail(id.raw()).data?.toManga()

    suspend fun chapters(id: String): List<MangaChapter> {
        val all = ArrayList<MangaChapter>()
        var page = 1
        while (true) {
            val dto = api.chapters(id.raw(), page, CHAPTER_PAGE)
            all += dto.data.map { it.toChapter() }
            if (dto.data.isEmpty() || page >= dto.meta.totalPage || page >= MAX_CHAPTER_PAGES) break
            page++
        }
        return pickChapters(all, "id")
    }

    suspend fun pages(chapterId: String): List<String> = api.pages(chapterId.raw()).data?.toPageUrls().orEmpty()

    /** The Shinigami entry for a title (tries each name in order), or null. */
    suspend fun find(names: List<String>): Manga? {
        for (name in names.filter { it.isNotBlank() }.distinct().take(3)) {
            val hit = runCatching { api.list(1, 10, null, name).data }.getOrNull()
                ?.firstOrNull { sameTitle(name, it.title, it.alternatives()) }
            if (hit != null) return hit.toManga()
        }
        return null
    }

    private companion object {
        const val PAGE_SIZE = 30
        const val CHAPTER_PAGE = 100
        const val MAX_CHAPTER_PAGES = 30
    }
}
