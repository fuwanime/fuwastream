package com.fuwanime.ui.detail

import com.fuwanime.data.Anime
import com.fuwanime.data.SeasonEntry
import java.util.TimeZone

/** "Ep 6 · 45m", "Ep 6 · 2j 14m", or "Ep 6 · Sab 23.00" when it is a day or more away. */
fun airingLabel(episode: Int, airingAtMs: Long, nowMs: Long, tz: TimeZone = TimeZone.getDefault()): String = TODO()

/** AniList's 0–100 average as "★ 8,4"; null when unknown. */
fun scoreLabel(score: Int?): String? = TODO()

/** The franchise in airing order, including [current]; empty when there is nothing else to show. */
fun watchOrder(current: Anime, seasons: List<SeasonEntry>): List<SeasonEntry> = TODO()

/** The entry after [currentId] in [ordered], e.g. S2 after S1. */
fun nextInFranchise(currentId: String, ordered: List<SeasonEntry>): SeasonEntry? = TODO()
