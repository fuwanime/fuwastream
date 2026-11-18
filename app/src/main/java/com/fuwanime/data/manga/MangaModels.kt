package com.fuwanime.data.manga

import java.math.BigDecimal

enum class MangaStatus { ONGOING, COMPLETED, HIATUS, CANCELLED, UNKNOWN }

/** Comix's release-status value for this status. */
fun MangaStatus.comixValue(): String? = when (this) {
    MangaStatus.ONGOING -> "releasing"
    MangaStatus.COMPLETED -> "finished"
    MangaStatus.HIATUS -> "on_hiatus"
    MangaStatus.CANCELLED -> "discontinued"
    MangaStatus.UNKNOWN -> null
}

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

/** The format name from a source's original-language code. */
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
    /** When the source made the chapter readable; 0 when it does not report it. */
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

/** One entry per manga; the newest release wins. */
fun latestPerManga(releases: List<MangaRelease>): List<MangaRelease> = releases.distinctBy { it.mangaId }

data class MangaTag(val id: String, val name: String, val group: String)

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

/** One entry per title: a later entry whose title matches an earlier one (either side's alt titles) is dropped. */
fun dedupeByTitle(list: List<Manga>): List<Manga> {
    val kept = ArrayList<Manga>()
    for (m in list) {
        if (kept.none { k -> sameTitle(k.title, m.title, m.altTitles) || sameTitle(m.title, k.title, k.altTitles) }) kept += m
    }
    return kept
}

/** Source spellings of the same genre, keyed by lowercase, mapped to the one name the app shows and filters by. */
private val GENRE_ALIASES = mapOf(
    "shounen ai" to "Boys Love", "shonen ai" to "Boys Love", "yaoi" to "Boys Love", "bl" to "Boys Love",
    "shoujo ai" to "Girls Love", "shojo ai" to "Girls Love", "yuri" to "Girls Love", "gl" to "Girls Love",
    "school" to "School Life", "super power" to "Superpower", "superpowers" to "Superpower",
    "sci fi" to "Sci-Fi", "scifi" to "Sci-Fi", "science fiction" to "Sci-Fi",
    "slice-of-life" to "Slice of Life", "martial art" to "Martial Arts",
)

/** Clean a genre/tag name from sources (strip quotes, normalise whitespace, unify known spellings). */
fun cleanGenreName(raw: String): String {
    val clean = raw
        .replace("\"", "")
        .replace("“", "").replace("”", "").replace("„", "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .trim(',', ';')
    return GENRE_ALIASES[clean.lowercase().replace('_', ' ')] ?: clean
}

/**
 * Applies the explore filters the sources can't. [status] and [type] only drop titles whose source states
 * the opposite (an unknown value passes, so a source without that field is never emptied by a chip);
 * [genres] must all be present, compared after [cleanGenreName].
 */
fun filterManga(items: List<Manga>, status: MangaStatus?, type: MangaType?, genres: Set<String>): List<Manga> {
    val wanted = genres.map { cleanGenreName(it).lowercase() }.filter { it.isNotBlank() }
    return items.filter { m ->
        (status == null || m.status == MangaStatus.UNKNOWN || m.status == status) &&
            (type == null || m.type == null || m.type.equals(type.label, ignoreCase = true)) &&
            (wanted.isEmpty() || m.tags.mapTo(HashSet()) { cleanGenreName(it).lowercase() }.containsAll(wanted))
    }
}

/** Deduplicate genre names case-insensitively while preserving first-seen display form. */
fun dedupeGenreNames(names: Iterable<String>): List<String> {
    val out = mutableListOf<String>()
    val seen = HashSet<String>()
    for (n in names) {
        val clean = cleanGenreName(n)
        if (clean.isBlank()) continue
        val key = clean.lowercase()
        if (seen.add(key)) out += clean
    }
    return out
}

/** Union of genres across anime and manga sources used by FuwaStream (deduped). */
fun mangaGenresFromAllSources(): List<String> = dedupeGenreNames(
    listOf(
        // Anime (general + mature)
        "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Isekai", "Romance", "School",
        "Slice of Life", "Supernatural", "Mystery", "Psychological", "Sci-Fi", "Mecha", "Sports",
        "Magic", "Super Power", "Martial Arts", "Historical", "Military", "Seinen", "Shounen",
        "Shoujo", "Shoujo Ai", "Shounen Ai", "Josei", "Harem", "Horror", "Thriller", "Game",
        "Parody", "Demons", "Vampire", "Music", "Police", "Space", "Cars", "Samurai", "Ecchi",
        "Erotica", "Hentai",
        // Manga sources (Shinigami/Komiku/MangaPark/KakaManga/Webtoon common genres)
        "Adult", "Doujinshi", "Gender Bender", "Mature", "Tragedy", "Webtoon", "Full Color",
        "Adaptation", "Animals", "Anthology", "Boys Love", "Girls Love", "Cooking", "Crime",
        "Delinquents", "Dementia", "Gore", "Gyaru", "Incest", "Isekai", "Loli", "Medical",
        "Monster Girls", "Ninja", "Office Workers", "Philosophical", "Post-Apocalyptic",
        "Reincarnation", "Reverse Harem", "Romance", "School Life", "Sci-Fi", "Shota",
        "Smut", "Survival", "Time Travel", "Traditional Games", "Urban", "Villainess",
        "Virtual Reality", "Wuxia", "Zombies", "4-Koma", "Award Winning", "Oneshot",
        "Long Strip", "Manhwa", "Manhua", "Comic", "Yaoi", "Yuri",
    ),
)

enum class ChapterLang { ID, EN, RAW }

/** Raw = untranslated uploads (Japanese, Korean, Chinese); everything else that is not Indonesian counts as English. */
fun languageGroup(language: String): ChapterLang = when (language.lowercase()) {
    "id" -> ChapterLang.ID
    "ja", "ko", "zh", "zh-hk" -> ChapterLang.RAW
    else -> ChapterLang.EN
}

/** 0 Shinigami, 1 Komiku, 2 Webtoon, 3 KakaManga, 4 MangaPark: lower wins when several sources have the same chapter. */
fun sourceRank(chapterId: String): Int = when {
    chapterId.startsWith(SH_PREFIX) -> 0
    chapterId.startsWith(KM_PREFIX) -> 1
    isWebtoonId(chapterId) -> 2
    isKakaMangaId(chapterId) -> 3
    isMangaParkId(chapterId) -> 4
    else -> 5
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
enum class MangaType(val label: String, val slug: String) {
    MANGA("Manga", "manga"),
    MANHWA("Manhwa", "manhwa"),
    MANHUA("Manhua", "manhua"),
}

fun firstReadable(list: List<MangaChapter>): MangaChapter? =
    list.firstOrNull { it.externalUrl == null && it.pages > 0 }

fun nextChapter(list: List<MangaChapter>, currentId: String): MangaChapter? =
    list.indexOfFirst { it.id == currentId }.takeIf { it >= 0 }?.let { list.getOrNull(it + 1) }

fun previousChapter(list: List<MangaChapter>, currentId: String): MangaChapter? =
    list.indexOfFirst { it.id == currentId }.takeIf { it > 0 }?.let { list[it - 1] }

private const val PAGE_CANDIDATE_SEPARATOR = '\u001F'

/** Network alternatives for one logical page. Most sources have one; Comix rotates several equivalent hosts. */
fun mangaPageCandidates(value: String): List<String> =
    value.split(PAGE_CANDIDATE_SEPARATOR).filter { it.isNotBlank() }.distinct()

internal fun packMangaPageCandidates(values: List<String>): String =
    values.filter { it.isNotBlank() }.distinct().joinToString(PAGE_CANDIDATE_SEPARATOR.toString())

