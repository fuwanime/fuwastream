package com.fuwanime.ui.detail

import android.content.res.Resources
import com.fuwanime.data.Anime
import com.fuwanime.data.SeasonEntry
import com.fuwanime.util.Day
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** "Ep 6 · 45m", "Ep 6 · 2j 14m", or "Ep 6 · Sab 23.00" when it is a day or more away. */
fun airingLabel(res: Resources, episode: Int, airingAtMs: Long, nowMs: Long, tz: TimeZone = TimeZone.getDefault()): String {
    val minutes = ((airingAtMs - nowMs).coerceAtLeast(0) / 60_000).toInt()
    val whenText = when {
        minutes < 60 -> "${minutes}m"
        minutes < 24 * 60 -> "${minutes / 60}j ${minutes % 60}m"
        else -> {
            val c = Calendar.getInstance(tz).apply { timeInMillis = airingAtMs }
            val day = res.getString(Day.of(c.get(Calendar.DAY_OF_WEEK)).short)
            "%s %02d.%02d".format(Locale.ROOT, day, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        }
    }
    return "Ep $episode · $whenText"
}

/** AniList's 0–100 average as "★ 8,4"; null when unknown. */
fun scoreLabel(score: Int?): String? =
    score?.takeIf { it > 0 }?.let { "★ ${it / 10},${it % 10}" }

/** The franchise in airing order, including [current]; empty when there is nothing else to show. */
fun watchOrder(current: Anime, seasons: List<SeasonEntry>): List<SeasonEntry> {
    val others = relatedSeasons(current.id, seasons)
    if (others.isEmpty()) return emptyList()
    val self = seasons.firstOrNull { it.anime.id == current.id } ?: SeasonEntry(current, "")
    val all = others + self
    // Stable sort: undated entries keep the source order at the end.
    return all.filter { it.anime.airedStart.isNotBlank() }.sortedBy { it.anime.airedStart } +
        all.filter { it.anime.airedStart.isBlank() }
}

/** The entry after [currentId] in [ordered], e.g. S2 after S1. */
fun nextInFranchise(currentId: String, ordered: List<SeasonEntry>): SeasonEntry? {
    val i = ordered.indexOfFirst { it.anime.id == currentId }
    return if (i < 0) null else ordered.getOrNull(i + 1)
}
