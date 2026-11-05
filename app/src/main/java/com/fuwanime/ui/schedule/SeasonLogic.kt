package com.fuwanime.ui.schedule

import com.fuwanime.data.Anime
import java.util.Locale

/** An anime broadcast season (quarter), e.g. "Musim Gugur" 2026 starting in October. */
data class AnimeSeason(val name: String, val year: Int, val startMonth: Int)

/** [month] is 1–12. */
fun seasonOf(year: Int, month: Int): AnimeSeason = when (month) {
    in 1..3 -> AnimeSeason("Musim Dingin", year, 1)
    in 4..6 -> AnimeSeason("Musim Semi", year, 4)
    in 7..9 -> AnimeSeason("Musim Panas", year, 7)
    else -> AnimeSeason("Musim Gugur", year, 10)
}

/** (premiered this season, continuing), each sorted by title; duplicates by id collapse. */
fun splitBySeason(all: List<Anime>, season: AnimeSeason): Pair<List<Anime>, List<Anime>> {
    val months = (season.startMonth until season.startMonth + 3).map { "%04d-%02d".format(Locale.ROOT, season.year, it) }
    val (fresh, continuing) = all.distinctBy { it.id }.partition { a -> months.any { a.airedStart.startsWith(it) } }
    return fresh.sortedBy { it.title.lowercase() } to continuing.sortedBy { it.title.lowercase() }
}

sealed interface SeasonTab {
    data object Loading : SeasonTab
    data class Error(val message: String) : SeasonTab
    data class Ready(val all: List<Anime>) : SeasonTab
}

/** Shows whatever days loaded; an error only when every day failed. */
fun seasonTabState(days: Collection<DaySchedule>): SeasonTab {
    val ready = days.filter { it.loaded && it.error == null }
    return when {
        ready.isNotEmpty() -> SeasonTab.Ready(ready.flatMap { it.items }.distinctBy { it.id })
        days.isNotEmpty() && days.all { it.error != null } -> SeasonTab.Error(days.first().error!!)
        else -> SeasonTab.Loading
    }
}
