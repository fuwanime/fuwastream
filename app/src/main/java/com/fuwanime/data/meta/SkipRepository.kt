package com.fuwanime.data.meta

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** AniSkip segments per episode, cached for the process; empty on any failure (not cached, so it retries). */
class SkipRepository(private val api: AniSkipApi) {
    private val cache = ConcurrentHashMap<String, List<SkipSegment>>()

    suspend fun segments(malId: Int, episode: Int, durationMs: Long): List<SkipSegment> {
        val key = "$malId/$episode"
        cache[key]?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val r = api.skipTimes(malId, episode, SKIP_TYPES, durationMs / 1000.0)
                (if (r.found) skipSegments(r.results, durationMs) else emptyList()).also { cache[key] = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                runCatching { Log.w("FuwaMeta", "AniSkip request failed", e) }
                emptyList()
            }
        }
    }
}
