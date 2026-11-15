package com.fuwanime

import com.fuwanime.data.SourceReliability
import com.fuwanime.data.StreamSource
import com.fuwanime.data.reliabilityKey
import com.fuwanime.data.smartOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartSourceTest {
    private fun source(url: String, height: Int, server: String = "") = StreamSource(url, "${height}p", server)

    @Test
    fun closestResolutionComesFirst() {
        val ranked = listOf(source("a", 1080), source("b", 720), source("c", 360)).smartOrder(720, emptyMap())
        assertEquals(listOf("b", "a", "c"), ranked.map { it.url })
    }

    @Test
    fun unknownResolutionRanksLast() {
        val ranked = listOf(source("a", 0), source("b", 480)).smartOrder(720, emptyMap())
        assertEquals(listOf("b", "a"), ranked.map { it.url })
    }

    @Test
    fun sameResolutionPrefersReliableServer() {
        val stats = mapOf(
            "server a" to SourceReliability(successes = 1, failures = 9, startupTotalMs = 1_000),
            "server b" to SourceReliability(successes = 9, failures = 1, startupTotalMs = 5_000),
        )
        val ranked = listOf(source("a", 720, "Server A"), source("b", 720, "Server B")).smartOrder(720, stats)
        assertEquals(listOf("b", "a"), ranked.map { it.url })
    }

    @Test
    fun sameReliabilityPrefersFasterStartup() {
        val stats = mapOf(
            "slow" to SourceReliability(5, 1, 30_000),
            "fast" to SourceReliability(5, 1, 5_000),
        )
        val ranked = listOf(source("a", 720, "slow"), source("b", 720, "fast")).smartOrder(720, stats)
        assertEquals(listOf("b", "a"), ranked.map { it.url })
    }

    @Test
    fun untriedSourceIsNeutralNotPerfect() {
        val stats = mapOf("known" to SourceReliability(2, 2, 4_000))
        val ranked = listOf(source("new", 720, "fresh"), source("known", 720, "known")).smartOrder(720, stats)
        // Laplace smoothing: 2/2 is exactly neutral (0.5), and the known source is faster.
        assertEquals(listOf("known", "new"), ranked.map { it.url })
    }

    @Test
    fun reliabilityKeyFallsBackToQualityWhenServerIsBlank() {
        assertEquals("server a", source("a", 720, " Server A ").reliabilityKey())
        assertEquals("720p", source("a", 720, "").reliabilityKey())
    }

    @Test
    fun laplaceRateNeverZeroOrOne() {
        assertEquals(0.5, SourceReliability(0, 0, 0).rate, 0.0)
        assertTrue(SourceReliability(0, 10, 0).rate > 0.0)
        assertTrue(SourceReliability(10, 0, 0).rate < 1.0)
    }
}
