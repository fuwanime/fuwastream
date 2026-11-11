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
    /** Other names (romaji, English, …); used to find the same title on another source. */
    val altTitles: List<String> = emptyList(),
    /** "Manga", "Manhwa" or "Manhua" when the source says. */
    val type: String? = null,
)

/** The format name from MangaDex's original language. */
fun mangaType(originalLanguage: String?): String? = when (originalLanguage?.substringBefore('-')) {
    "ja" -> "Manga"
    "ko" -> "Manhwa"
    "zh" -> "Manhua"
    else -> null
}

data class MangaChapter(
    val id: String,
    val number: String?,
    val title: String?,
    val language: String,
    val group: String?,
    val pages: Int,
    val externalUrl: String?,
    val publishAt: Long,
    /** When MangaDex made the chapter readable; 0 when the source does not report it. */
    val readableAt: Long = 0,
)

/** A newly published chapter of an ongoing manga, for the release notifications. */
data class MangaRelease(
    val mangaId: String,
    val title: String,
    val chapterId: String,
    val chapter: String?,
    val publishAt: Long,
)

/**
 * Reads the owning manga off a chapter from the global `/chapter` feed (`includes[]=manga`); null when absent or
 * not ongoing. [now] drops chapters that are not readable yet, so a scheduled upload cannot become a notification.
 */
fun ChapterDto.mangaRelease(now: Long): MangaRelease? {
    val rel = relationships.firstOrNull { it.type == "manga" && it.id.isNotBlank() } ?: return null
    val status = rel.attributes?.status
    if (status != null && !status.equals("ongoing", true)) return null
    val titles = loc(rel.attributes?.title)
    val title = titles["en"] ?: titles["ja-ro"] ?: titles.values.firstOrNull() ?: return null
    val ch = toChapter()
    val availableAt = ch.readableAt.takeIf { it > 0 } ?: ch.publishAt
    if (availableAt > now) return null
    return MangaRelease(rel.id, title, ch.id, ch.number, availableAt)
}

/** One entry per manga; a readableAt-desc feed makes the first occurrence the newest chapter. */
fun latestPerManga(releases: List<MangaRelease>): List<MangaRelease> = releases.distinctBy { it.mangaId }

data class MangaTag(val id: String, val name: String, val group: String)

@Suppress("UNCHECKED_CAST")
internal fun loc(x: Any?): Map<String, String> = (x as? Map<String, Any?>)?.filterValues { it is String } as? Map<String, String> ?: emptyMap()

fun TagDto.toTag(): MangaTag {
    val n = loc(attributes.name)
    return MangaTag(id, n["en"] ?: n.values.firstOrNull().orEmpty(), attributes.group)
}

fun MangaDto.toManga(appLanguage: String, coverSize: Int): Manga {
    val a = attributes
    val t = loc(a.title)
    val title = t["en"] ?: t["ja-ro"] ?: t.values.firstOrNull()
        ?: a.altTitles.firstNotNullOfOrNull { loc(it).values.firstOrNull() }.orEmpty()
    val desc = loc(a.description)
    val cover = relationships.firstOrNull { it.type == "cover_art" }?.attributes?.fileName
        ?.let { "https://uploads.mangadex.org/covers/$id/$it.$coverSize.jpg" }
    return Manga(
        id = id,
        title = title,
        coverUrl = cover,
        description = desc[appLanguage] ?: desc["en"].orEmpty(),
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
        type = mangaType(a.originalLanguage),
        altTitles = (t.values + a.altTitles.flatMap { loc(it).filterKeys { k -> k == "en" || k.endsWith("-ro") }.values }).filter { it != title }.distinct(),
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
    readableAt = parsePublishAt(attributes.readableAt),
)

// SimpleDateFormat (not java.time): no core library desugaring below API 26. Not thread-safe, so one per call.
private fun parsePublishAt(s: String?): Long =
    runCatching { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).parse(s!!)!!.time }.getOrDefault(0L)

/** One entry per chapter number (preferred language, else any; readable before external/empty; newest upload), in reading order. */
fun pickChapters(all: List<MangaChapter>, preferred: String): List<MangaChapter> {
    val (oneshots, numbered) = all.partition { it.number == null }
    val picked = numbered.groupBy { it.number }.values.map { group ->
        val pool = group.filter { it.language == preferred }.ifEmpty { group }
        // A newer official (external) or empty upload must not hide a readable scan of the same chapter.
        pool.filter { it.externalUrl == null && it.pages > 0 }.ifEmpty { pool }.maxBy { it.publishAt }
    }
    val (parsable, odd) = picked.partition { it.number?.toBigDecimalOrNull() != null }
    return parsable.sortedBy { it.number!!.toBigDecimal() } +
        (odd + oneshots).sortedBy { it.publishAt }
}

enum class ChapterLang { ID, EN, RAW }

/** Raw = untranslated uploads (Japanese, Korean, Chinese); everything else that is not Indonesian counts as English. */
fun languageGroup(language: String): ChapterLang = when (language.lowercase()) {
    "id" -> ChapterLang.ID
    "ja", "ko", "zh", "zh-hk" -> ChapterLang.RAW
    else -> ChapterLang.EN
}

/** 0 Shinigami, 1 Komiku, 2 MangaDex: lower wins when several sources have the same chapter. */
fun sourceRank(chapterId: String): Int = when {
    chapterId.startsWith("sh:") -> 0
    chapterId.startsWith("km:") -> 1
    else -> 2
}

private fun MangaChapter.readable() = externalUrl == null && pages > 0

fun availableLanguages(chapters: List<MangaChapter>): List<ChapterLang> {
    val present = chapters.filter { it.readable() }.mapTo(HashSet()) { languageGroup(it.language) }
    return ChapterLang.entries.filter { it in present }
}

fun defaultLanguage(available: List<ChapterLang>, appLanguage: String): ChapterLang? = when {
    // Older Android reports Indonesian as the legacy code "in".
    (appLanguage == "id" || appLanguage == "in") && ChapterLang.ID in available -> ChapterLang.ID
    ChapterLang.EN in available -> ChapterLang.EN
    else -> available.firstOrNull()
}

fun effectiveLanguage(chosen: ChapterLang?, available: List<ChapterLang>, appLanguage: String): ChapterLang? =
    chosen?.takeIf { it in available } ?: defaultLanguage(available, appLanguage)

/** The language the reader stays in: that of the opened chapter, or the default when it is not in the list. */
fun readerLanguage(all: List<MangaChapter>, chapterId: String, appLanguage: String): ChapterLang? =
    all.firstOrNull { it.id == chapterId }?.let { languageGroup(it.language) } ?: defaultLanguage(availableLanguages(all), appLanguage)

/** Chapter number as a comparable key: "5", "5.0" and "05" are the same chapter. */
fun numberKey(c: MangaChapter): String? = c.number?.let { it.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: it }

/** [list] with the entry of [opened]'s number replaced by [opened], so Prev/Next still work when it lost the merge to another source. */
fun withChapter(list: List<MangaChapter>, opened: MangaChapter): List<MangaChapter> {
    val key = numberKey(opened) ?: return list
    return list.map { if (numberKey(it) == key) opened else it }
}

/** One chapter per number in [lang], from all sources: readable first, then [sourceRank], then newest; in reading order. */
fun mergeChapters(all: List<MangaChapter>, lang: ChapterLang): List<MangaChapter> {
    val inLang = all.filter { languageGroup(it.language) == lang }
    val (oneshots, numbered) = inLang.partition { it.number == null }
    val picked = numbered.groupBy { numberKey(it) }.values.map { group ->
        group.filter { it.readable() }.ifEmpty { group }.sortedWith(compareBy<MangaChapter> { sourceRank(it.id) }.thenByDescending { it.publishAt }).first()
    }
    // Oneshots have no number to match on; take them from one source only so they don't show twice.
    val bestOneshotRank = oneshots.filter { it.readable() }.ifEmpty { oneshots }.minOfOrNull { sourceRank(it.id) }
    val (parsable, odd) = picked.partition { it.number?.toBigDecimalOrNull() != null }
    return parsable.sortedBy { it.number!!.toBigDecimal() } +
        (odd + oneshots.filter { sourceRank(it.id) == bestOneshotRank }).sortedBy { it.publishAt }
}

/** A comic's origin; each source filters by it server-side. */
enum class MangaType(val label: String, val mangaDex: List<String>, val slug: String) {
    MANGA("Manga", listOf("ja"), "manga"),
    MANHWA("Manhwa", listOf("ko"), "manhwa"),
    MANHUA("Manhua", listOf("zh", "zh-hk"), "manhua"),
}

fun firstReadable(list: List<MangaChapter>): MangaChapter? =
    list.firstOrNull { it.externalUrl == null && it.pages > 0 }

fun nextChapter(list: List<MangaChapter>, currentId: String): MangaChapter? =
    list.indexOfFirst { it.id == currentId }.takeIf { it >= 0 }?.let { list.getOrNull(it + 1) }

fun previousChapter(list: List<MangaChapter>, currentId: String): MangaChapter? =
    list.indexOfFirst { it.id == currentId }.takeIf { it > 0 }?.let { list[it - 1] }

fun pageUrls(baseUrl: String, hash: String, data: List<String>, dataSaver: List<String>, useDataSaver: Boolean): List<String> =
    if (useDataSaver) dataSaver.map { "$baseUrl/data-saver/$hash/$it" } else data.map { "$baseUrl/data/$hash/$it" }

