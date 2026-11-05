package com.fuwanime.ui.player

import com.fuwanime.data.Anime
import com.fuwanime.data.Episode
import com.fuwanime.data.OpMark
import com.fuwanime.data.SeasonEntry
import com.fuwanime.data.meta.SegmentKind
import com.fuwanime.data.meta.SkipSegment

sealed interface SkipAction {
    val targetMs: Long

    /** Inside a marked opening: jump to its end. */
    data class SkipOp(override val targetMs: Long) : SkipAction

    /** No mark yet: a generic jump that clears most openings. */
    data class JumpAhead(override val targetMs: Long) : SkipAction
}

/** Inside a community (AniSkip) segment: jump to its end. */
data class SkipSegmentAction(val kind: SegmentKind, override val targetMs: Long) : SkipAction

private const val JUMP_MS = 85_000L
private const val JUMP_FROM_MS = 5_000L
private const val JUMP_UNTIL_MS = 180_000L
private const val GUESSED_OP_MS = 90_000L

/** [jumpAhead] off hides the generic +85s jump; a marked opening still offers "Lewati OP". */
fun skipActionAt(
    positionMs: Long,
    durationMs: Long,
    mark: OpMark?,
    jumpAhead: Boolean = true,
    segments: List<SkipSegment> = emptyList(),
): SkipAction? {
    val validMark = mark?.takeIf { it.isValid }
    segments.firstOrNull { (validMark == null || it.kind != SegmentKind.OP) && positionMs >= it.startMs && positionMs < it.endMs - 1_000 }
        ?.let { return SkipSegmentAction(it.kind, it.endMs) }
    // A known AniSkip opening replaces the generic jump (the button appears once the opening starts).
    if (validMark == null && segments.any { it.kind == SegmentKind.OP }) return null
    if (mark != null && mark.isValid) {
        // Leave the last second alone: skipping it is pointless and the button would flash.
        return if (positionMs >= mark.startMs && positionMs < mark.endMs - 1_000) SkipAction.SkipOp(mark.endMs) else null
    }
    if (!jumpAhead) return null
    if (positionMs < JUMP_FROM_MS || positionMs >= JUMP_UNTIL_MS) return null
    val target = positionMs + JUMP_MS
    return if (durationMs <= 0 || target < durationMs) SkipAction.JumpAhead(target) else null
}

/** In the small portrait player the centred transport row reaches the bottom-right corner, so the button waits for it to hide. */
fun skipButtonVisible(controlsVisible: Boolean, fullscreen: Boolean, locked: Boolean): Boolean =
    !locked && (fullscreen || !controlsVisible)

/**
 * Auto-skip runs once per episode. [handledEpisodeId] is the episode already skipped, or the one in which the user
 * edited the mark (so marking the start never jumps to a guessed end behind the sheet).
 */
fun shouldAutoSkip(enabled: Boolean, action: SkipAction?, episodeId: String, handledEpisodeId: String?): Boolean =
    enabled && (action is SkipAction.SkipOp || (action is SkipSegmentAction && action.kind == SegmentKind.OP)) &&
        handledEpisodeId != episodeId

/** Sets one edge of the mark at [positionMs], keeping the other edge when it still makes sense. */
fun markOp(current: OpMark?, positionMs: Long, isStart: Boolean): OpMark = if (isStart) {
    OpMark(positionMs, current?.endMs?.takeIf { it > positionMs } ?: (positionMs + GUESSED_OP_MS))
} else {
    OpMark(current?.startMs?.takeIf { it < positionMs } ?: (positionMs - GUESSED_OP_MS).coerceAtLeast(0), positionMs)
}

fun skipLabel(action: SkipAction): String = when (action) {
    is SkipAction.SkipOp -> "Lewati OP"
    is SkipAction.JumpAhead -> "+85 dtk"
    is SkipSegmentAction -> when (action.kind) {
        SegmentKind.OP -> "Lewati OP"
        SegmentKind.ED -> "Lewati ED"
        SegmentKind.RECAP -> "Lewati recap"
    }
}

/** AniSkip numbers episodes 1, 2, …; fractional or named episodes ("12.5", "OVA") have no entry. */
fun episodeNumberForSkip(number: String): Int? = number.trim().toIntOrNull()?.takeIf { it > 0 }

/** Segments only for the episode they were fetched for, so a switch never shows the previous episode's. */
fun segmentsFor(current: Pair<String, List<SkipSegment>>, episodeId: String): List<SkipSegment> =
    if (current.first == episodeId) current.second else emptyList()

/** The player's loaded item is the current source and has rendered, so its duration is this episode's. */
fun segmentSourceReady(firstFrame: Boolean, loadedUri: String?, sourceUrl: String?): Boolean =
    firstFrame && sourceUrl != null && loadedUri == sourceUrl

/**
 * Whether to look up AniSkip now: once per episode after a MAL id was found, one attempt at a time, and at most
 * every 5 s while the anime is not linked yet (the Detail screen may still be linking it).
 */
fun shouldTrySegments(
    episodeId: String,
    currentEpisodeId: String,
    durationMs: Long,
    doneFor: String?,
    inFlight: Boolean,
    lastTryMs: Long?,
    nowMs: Long,
): Boolean = durationMs > 0 && episodeId == currentEpisodeId && doneFor != episodeId && !inFlight &&
    (lastTryMs == null || nowMs - lastTryMs >= 5_000)

/** The next-season card belongs to the end of a finished season, not to the latest episode of one still airing. */
fun offerNextSeason(anime: Anime?, next: Episode?, nextSeason: SeasonEntry?): SeasonEntry? =
    nextSeason?.takeIf { next == null && anime?.isOngoing != true }
