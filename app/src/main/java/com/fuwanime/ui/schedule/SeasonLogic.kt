package com.fuwanime.ui.schedule

import com.fuwanime.data.Anime

/** An anime broadcast season (quarter), e.g. "Musim Gugur" 2026 starting in October. */
data class AnimeSeason(val name: String, val year: Int, val startMonth: Int)

/** [month] is 1–12. */
fun seasonOf(year: Int, month: Int): AnimeSeason = TODO()

/** (premiered this season, continuing), each sorted by title; duplicates by id collapse. */
fun splitBySeason(all: List<Anime>, season: AnimeSeason): Pair<List<Anime>, List<Anime>> = TODO()

sealed interface SeasonTab {
    data object Loading : SeasonTab
    data class Error(val message: String) : SeasonTab
    data class Ready(val all: List<Anime>) : SeasonTab
}

/** Shows whatever days loaded; an error only when every day failed. */
fun seasonTabState(days: Collection<DaySchedule>): SeasonTab = TODO()
