package com.fuwanime

import com.fuwanime.data.manga.sourceRank
import com.fuwanime.data.manga.translationChunks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source ordering and translation chunking: kept from the removed Comix mapping test, they outlive that source. */
class MangaSupportTest {
    @Test fun indonesianSourcesRankFirst() {
        assertEquals(0, sourceRank("sh:1"))
        assertEquals(1, sourceRank("km:1"))
        assertTrue(sourceRank("sh:1") < sourceRank("km:1"))
        assertTrue(sourceRank("km:1") < sourceRank("unknown:1"))
    }

    @Test fun translationChunksKeepEveryLine() {
        val text = (1..1_000).joinToString("\n") { "line $it" }
        val chunks = translationChunks(text, limit = 200)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 200 })
        assertEquals(text, chunks.joinToString("\n"))
    }

    @Test fun translationChunksSplitOneLongLine() =
        assertEquals(listOf("x".repeat(100), "x".repeat(100), "x".repeat(50)), translationChunks("x".repeat(250), limit = 100))
}
