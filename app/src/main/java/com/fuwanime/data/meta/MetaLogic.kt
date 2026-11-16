package com.fuwanime.data.meta

import java.text.Normalizer
import kotlin.math.abs

enum class LinkState { LINKED, NONE, UNLINKED }

/** What Detail shows from AniList; [nextAiringAt] is epoch milliseconds; [sourceTitles] name the manga it adapts. */
data class AnimeMeta(
    val score: Int?,
    val tags: List<String>,
    val nextEpisode: Int?,
    val nextAiringAt: Long?,
    val sourceTitles: List<String> = emptyList(),
    /** Main and supporting characters with their Japanese voice actor, main first. */
    val cast: List<CastEntry> = emptyList(),
)

enum class CastRole { MAIN, SUPPORTING }

data class CastEntry(val character: String, val imageUrl: String?, val role: CastRole, val voiceActor: String?)

enum class SegmentKind { OP, ED, RECAP }

data class SkipSegment(val kind: SegmentKind, val startMs: Long, val endMs: Long)

private val SEASON_WORDS = Regex("\\b(season|the|part|cour)\\b")
private val NON_ALNUM = Regex("[^a-z0-9]")
private val MARKS = Regex("\\p{M}+")

/** Compares titles across sources: accents folded, punctuation and words like "season" dropped. Non-Latin text → "". */
fun normTitle(s: String?): String {
    if (s.isNullOrBlank()) return ""
    val ascii = MARKS.replace(Normalizer.normalize(s, Normalizer.Form.NFKD), "").lowercase()
    return NON_ALNUM.replace(SEASON_WORDS.replace(ascii, " "), "")
}

fun pickMatch(title: String, synonyms: List<String>, airedYear: Int?, candidates: List<AniListMedia>, yearTolerance: Int): AniListMedia? {
    val wanted = (listOf(title) + synonyms).map(::normTitle).filter { it.isNotEmpty() }.toSet()
    if (wanted.isEmpty()) return null
    return candidates.firstOrNull { c ->
        val names = (listOf(c.title.romaji, c.title.english, c.title.native) + c.synonyms).map(::normTitle).filter { it.isNotEmpty() }
        val year = c.seasonYear ?: c.startDate?.year
        names.any { it in wanted } && (airedYear == null || (year != null && abs(year - airedYear) <= yearTolerance))
    }
}

fun synonymsOf(alternativeTitles: String): List<String> =
    alternativeTitles.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }

fun airedYearOf(airedStart: String): Int? = airedStart.take(4).takeIf { it.length == 4 }?.toIntOrNull()

fun metaOf(m: AniListMedia): AnimeMeta = AnimeMeta(
    score = m.averageScore,
    tags = m.tags.filterNot { it.isMediaSpoiler }.sortedByDescending { it.rank ?: 0 }.take(6).map { it.name },
    nextEpisode = m.nextAiringEpisode?.episode,
    nextAiringAt = m.nextAiringEpisode?.airingAt?.times(1000),
    sourceTitles = sourceMangaTitles(m),
    cast = castOf(m),
)

/** Main and supporting characters (background ones are left out), in AniList's order. */
fun castOf(m: AniListMedia): List<CastEntry> = m.characters?.edges.orEmpty().mapNotNull { e ->
    val role = when (e.role) {
        "MAIN" -> CastRole.MAIN
        "SUPPORTING" -> CastRole.SUPPORTING
        else -> return@mapNotNull null
    }
    val name = e.node?.name?.full?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
    CastEntry(name, e.node.image?.medium, role, e.voiceActors.firstOrNull()?.name?.full?.trim()?.takeIf { it.isNotEmpty() })
}

/** One cast entry per line, fields tab-separated, for the anime_link cache. */
fun encodeCast(cast: List<CastEntry>): String = cast.joinToString("\n") { c ->
    listOf(c.role.name, c.character, c.voiceActor.orEmpty(), c.imageUrl.orEmpty()).joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }
}

fun decodeCast(text: String): List<CastEntry> = text.lines().mapNotNull { line ->
    val f = line.split('\t')
    if (f.size < 4) return@mapNotNull null
    val role = CastRole.entries.firstOrNull { it.name == f[0] } ?: return@mapNotNull null
    CastEntry(f[1], f[3].ifEmpty { null }, role, f[2].ifEmpty { null })
}

/**
 * Names of the manga this anime adapts (AniList SOURCE relation), romaji first; empty for light novels and originals.
 * Native titles are left out: the manga sources match on Latin names only.
 */
fun sourceMangaTitles(m: AniListMedia): List<String> {
    val node = m.relations?.edges.orEmpty()
        .firstOrNull { it.relationType == "SOURCE" && it.node?.type == "MANGA" && it.node.format != "NOVEL" }?.node
        ?: return emptyList()
    return (listOf(node.title.romaji, node.title.english) + node.synonyms)
        .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        .distinct()
}

private const val HOUR_MS = 3_600_000L

fun needsRefresh(state: LinkState, fetchedAt: Long, nextAiringAt: Long?, nowMs: Long): Boolean = when (state) {
    LinkState.LINKED -> nowMs - fetchedAt > 12 * HOUR_MS || (nextAiringAt != null && nextAiringAt <= nowMs)
    LinkState.NONE -> nowMs - fetchedAt > 7 * 24 * HOUR_MS
    LinkState.UNLINKED -> false
}

/** One segment per kind (the first that fits this encode), ordered by start. */
fun skipSegments(results: List<AniSkipResult>, durationMs: Long): List<SkipSegment> = results
    .filter { abs(it.episodeLength * 1000 - durationMs) <= 5_000 }
    .mapNotNull { r ->
        val kind = when (r.skipType) {
            "op", "mixed-op" -> SegmentKind.OP
            "ed", "mixed-ed" -> SegmentKind.ED
            "recap" -> SegmentKind.RECAP
            else -> return@mapNotNull null
        }
        SkipSegment(kind, (r.interval.startTime * 1000).toLong(), (r.interval.endTime * 1000).toLong()).takeIf { it.endMs > it.startMs }
    }
    .distinctBy { it.kind }
    .sortedBy { it.startMs }
