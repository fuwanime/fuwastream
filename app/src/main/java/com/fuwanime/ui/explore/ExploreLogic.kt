package com.fuwanime.ui.explore

import com.fuwanime.data.Anime
import kotlin.random.Random

/** Every selected genre must be present (AND). "Isekai" also matches a synopsis that mentions it. */
fun matchesGenres(anime: Anime, genres: Set<String>): Boolean = TODO()

fun pickRandom(results: List<Anime>, random: Random = Random.Default): Anime? = TODO()
