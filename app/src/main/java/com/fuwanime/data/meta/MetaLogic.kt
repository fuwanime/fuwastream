package com.fuwanime.data.meta

enum class LinkState { LINKED, NONE, UNLINKED }

/** What Detail shows from AniList; [nextAiringAt] is epoch milliseconds. */
data class AnimeMeta(val score: Int?, val tags: List<String>, val nextEpisode: Int?, val nextAiringAt: Long?)

enum class SegmentKind { OP, ED, RECAP }

data class SkipSegment(val kind: SegmentKind, val startMs: Long, val endMs: Long)

fun normTitle(s: String?): String = TODO()

fun pickMatch(title: String, synonyms: List<String>, airedYear: Int?, candidates: List<AniListMedia>, yearTolerance: Int): AniListMedia? = TODO()

fun synonymsOf(alternativeTitles: String): List<String> = TODO()

fun airedYearOf(airedStart: String): Int? = TODO()

fun metaOf(m: AniListMedia): AnimeMeta = TODO()

fun needsRefresh(state: LinkState, fetchedAt: Long, nextAiringAt: Long?, nowMs: Long): Boolean = TODO()

fun skipSegments(results: List<AniSkipResult>, durationMs: Long): List<SkipSegment> = TODO()
