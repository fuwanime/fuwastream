package com.fuwanime.data.meta

/** AniSkip segments per episode, cached for the process; empty on any failure. */
class SkipRepository(private val api: AniSkipApi) {
    suspend fun segments(malId: Int, episode: Int, durationMs: Long): List<SkipSegment> = TODO()
}
