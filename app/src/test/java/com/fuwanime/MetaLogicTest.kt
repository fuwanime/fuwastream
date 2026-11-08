package com.fuwanime

import com.fuwanime.data.meta.AiringEpisode
import com.fuwanime.data.meta.AniListMedia
import com.fuwanime.data.meta.AniListTag
import com.fuwanime.data.meta.AniListTitle
import com.fuwanime.data.meta.AniSkipInterval
import com.fuwanime.data.meta.AniSkipResult
import com.fuwanime.data.meta.FuzzyDate
import com.fuwanime.data.meta.LinkState
import com.fuwanime.data.meta.SegmentKind
import com.fuwanime.data.meta.SkipSegment
import com.fuwanime.data.meta.airedYearOf
import com.fuwanime.data.meta.metaOf
import com.fuwanime.data.meta.needsRefresh
import com.fuwanime.data.meta.normTitle
import com.fuwanime.data.meta.pickMatch
import com.fuwanime.data.meta.skipSegments
import com.fuwanime.data.meta.synonymsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetaLogicTest {
    private fun media(id: Int, romaji: String, year: Int?, english: String? = null, syn: List<String> = emptyList()) =
        AniListMedia(id, idMal = id + 1, format = "TV", seasonYear = year, startDate = FuzzyDate(year), title = AniListTitle(romaji, english, null), synonyms = syn)

    @Test fun normStripsAccentsPunctuationAndSeasonWords() = assertEquals("shingekinokyojin2", normTitle("Shingeki no Kyojin: Season 2"))
    @Test fun normFoldsAccents() = assertEquals("shuumatsunowalkure", normTitle("Shuumatsu no Walküre"))
    @Test fun normNativeIsEmpty() = assertEquals("", normTitle("僕のヒーローアカデミア"))

    @Test fun exactTitleAndYear() = assertEquals(1, pickMatch("Jujutsu Kaisen", emptyList(), 2020, listOf(media(1, "Jujutsu Kaisen", 2020)), 0)?.id)

    @Test fun matchesThroughSynonym() = assertEquals(
        2,
        pickMatch(
            "Boku no Hero Academia S2", listOf("My Hero Academia Season 2"), 2017,
            listOf(media(2, "Boku no Hero Academia 2", 2017, english = "My Hero Academia Season 2")), 1,
        )?.id,
    )

    @Test fun yearOffByOneRejectedAtZero() =
        assertNull(pickMatch("Kimetsu no Yaiba: Yuukaku-hen", emptyList(), 2021, listOf(media(3, "Kimetsu no Yaiba: Yuukaku-hen", 2022)), 0))

    @Test fun yearOffByOneAcceptedAtOne() =
        assertEquals(3, pickMatch("Kimetsu no Yaiba: Yuukaku-hen", emptyList(), 2021, listOf(media(3, "Kimetsu no Yaiba: Yuukaku-hen", 2022)), 1)?.id)

    @Test fun unknownYearMatchesOnTitle() = assertEquals(4, pickMatch("Mu Shen Ji", emptyList(), null, listOf(media(4, "Mu Shen Ji", null)), 0)?.id)

    @Test fun candidateWithoutYearRejectedWhenYearKnown() = assertNull(pickMatch("Mu Shen Ji", emptyList(), 2024, listOf(media(4, "Mu Shen Ji", null)), 1))

    @Test fun noCandidates() = assertNull(pickMatch("X", emptyList(), 2020, emptyList(), 1))

    @Test fun firstMatchingCandidateWins() = assertEquals(
        5,
        pickMatch("Naruto", emptyList(), 2002, listOf(media(9, "Boruto", 2017), media(5, "NARUTO", 2002), media(6, "Naruto", 2002)), 0)?.id,
    )

    @Test fun synonymsSplit() = assertEquals(listOf("OP", "ワンピース", "One Piece"), synonymsOf("OP, ワンピース; One Piece,  ,"))

    @Test fun airedYear() {
        assertEquals(1999, airedYearOf("1999-10-20"))
        assertNull(airedYearOf(""))
        assertNull(airedYearOf("unknown"))
    }

    @Test fun metaKeepsSixNonSpoilerTagsByRank() {
        val tags = listOf(
            AniListTag("A", 10), AniListTag("B", 90), AniListTag("Spoiler", 99, isMediaSpoiler = true), AniListTag("C", 50),
            AniListTag("D", 70), AniListTag("E", 30), AniListTag("F", 80), AniListTag("G", 20),
        )
        val meta = metaOf(media(1, "X", 2020).copy(averageScore = 84, tags = tags, nextAiringEpisode = AiringEpisode(6, 1762500000)))
        assertEquals(listOf("B", "F", "D", "C", "E", "G"), meta.tags)
        assertEquals(84, meta.score)
        assertEquals(6, meta.nextEpisode)
        assertEquals(1762500000000L, meta.nextAiringAt)
    }

    @Test fun refreshRules() {
        val h = 3_600_000L
        val now = 1_000_000_000_000L
        assertFalse(needsRefresh(LinkState.LINKED, now - 11 * h, null, now))
        assertTrue(needsRefresh(LinkState.LINKED, now - 13 * h, null, now))
        assertTrue(needsRefresh(LinkState.LINKED, now - h, now - 1, now))
        assertFalse(needsRefresh(LinkState.LINKED, now - h, now + h, now))
        assertFalse(needsRefresh(LinkState.NONE, now - 6 * 24 * h, null, now))
        assertTrue(needsRefresh(LinkState.NONE, now - 8 * 24 * h, null, now))
        assertFalse(needsRefresh(LinkState.UNLINKED, 0, null, now))
    }

    private fun r(type: String, s: Double, e: Double, len: Double) = AniSkipResult(AniSkipInterval(s, e), type, len)

    @Test fun segmentsMapKindsAndFilterLength() {
        val segs = skipSegments(
            listOf(r("mixed-op", 80.0, 170.0, 1420.0), r("op", 10.0, 20.0, 1420.0), r("ed", 1300.0, 1390.0, 1400.0), r("recap", 0.0, 30.0, 1423.0), r("ed", 1330.0, 1400.0, 1422.0)),
            1_420_000,
        )
        assertEquals(
            listOf(SkipSegment(SegmentKind.RECAP, 0, 30_000), SkipSegment(SegmentKind.OP, 80_000, 170_000), SkipSegment(SegmentKind.ED, 1_330_000, 1_400_000)),
            segs,
        )
    }

    @Test fun segmentsDropInvertedIntervals() = assertTrue(skipSegments(listOf(r("op", 50.0, 40.0, 1420.0)), 1_420_000).isEmpty())

    @Test fun segmentsIgnoreUnknownTypes() = assertTrue(skipSegments(listOf(r("preview", 1.0, 40.0, 1420.0)), 1_420_000).isEmpty())
}
