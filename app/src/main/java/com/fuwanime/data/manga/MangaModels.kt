package com.fuwanime.data.manga

import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Locale

enum class MangaStatus { ONGOING, COMPLETED, HIATUS, CANCELLED, UNKNOWN }

data class Manga(
    val id: String,
    val title: String,
    val coverUrl: String?,
    val description: String,
    val tags: List<String>,
    val status: MangaStatus,
    val year: Int?,
    val author: String?,
)

data class MangaChapter(
    val id: String,
    val number: String?,
    val title: String?,
    val language: String,
    val group: String?,
    val pages: Int,
    val externalUrl: String?,
    val publishAt: Long,
)

data class MangaTag(val id: String, val name: String, val group: String)

fun TagDto.toTag() = MangaTag(id, attributes.name["en"] ?: attributes.name.values.firstOrNull().orEmpty(), attributes.group)

fun MangaDto.toManga(appLanguage: String, coverSize: Int): Manga {
    val a = attributes
    val title = a.title["en"] ?: a.title["ja-ro"] ?: a.title.values.firstOrNull()
        ?: a.altTitles.firstNotNullOfOrNull { it.values.firstOrNull() }.orEmpty()
    val cover = relationships.firstOrNull { it.type == "cover_art" }?.attributes?.fileName
        ?.let { "https://uploads.mangadex.org/covers/$id/$it.$coverSize.jpg" }
    return Manga(
        id = id,
        title = title,
        coverUrl = cover,
        description = a.description[appLanguage] ?: a.description["en"].orEmpty(),
        tags = a.tags.map { it.toTag().name },
        status = when (a.status) {
            "ongoing" -> MangaStatus.ONGOING
            "completed" -> MangaStatus.COMPLETED
            "hiatus" -> MangaStatus.HIATUS
            "cancelled" -> MangaStatus.CANCELLED
            else -> MangaStatus.UNKNOWN
        },
        year = a.year,
        author = relationships.firstOrNull { it.type == "author" }?.attributes?.name,
    )
}

fun ChapterDto.toChapter() = MangaChapter(
    id = id,
    number = attributes.chapter,
    title = attributes.title,
    language = attributes.translatedLanguage,
    group = relationships.firstOrNull { it.type == "scanlation_group" }?.attributes?.name,
    pages = attributes.pages,
    externalUrl = attributes.externalUrl,
    publishAt = parsePublishAt(attributes.publishAt),
)

// SimpleDateFormat (not java.time): no core library desugaring below API 26. Not thread-safe, so one per call.
private fun parsePublishAt(s: String?): Long =
    runCatching { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(s!!)!!.time }.getOrDefault(0L)

/** One entry per chapter number (preferred language, else any; newest upload), in reading order. */
fun pickChapters(all: List<MangaChapter>, preferred: String): List<MangaChapter> {
    val (oneshots, numbered) = all.partition { it.number == null }
    val picked = numbered.groupBy { it.number }.values.map { group ->
        val pool = group.filter { it.language == preferred }.ifEmpty { group }
        pool.maxBy { it.publishAt }
    }
    val (parsable, odd) = picked.partition { it.number?.toBigDecimalOrNull() != null }
    return parsable.sortedBy { it.number!!.toBigDecimal() } +
        (odd + oneshots).sortedBy { it.publishAt }
}

fun firstReadable(list: List<MangaChapter>): MangaChapter? =
    list.firstOrNull { it.externalUrl == null && it.pages > 0 }

fun nextChapter(list: List<MangaChapter>, currentId: String): MangaChapter? =
    list.indexOfFirst { it.id == currentId }.takeIf { it >= 0 }?.let { list.getOrNull(it + 1) }

fun previousChapter(list: List<MangaChapter>, currentId: String): MangaChapter? =
    list.indexOfFirst { it.id == currentId }.takeIf { it > 0 }?.let { list[it - 1] }

fun pageUrls(baseUrl: String, hash: String, data: List<String>, dataSaver: List<String>, useDataSaver: Boolean): List<String> =
    if (useDataSaver) dataSaver.map { "$baseUrl/data-saver/$hash/$it" } else data.map { "$baseUrl/data/$hash/$it" }

fun chapterLanguageFor(appLanguage: String): String = if (appLanguage == "id") "id" else "en"
