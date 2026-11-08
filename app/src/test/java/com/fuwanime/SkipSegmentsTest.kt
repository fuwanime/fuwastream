package com.fuwanime

import com.fuwanime.R
import com.fuwanime.data.OpMark
import com.fuwanime.data.meta.SegmentKind
import com.fuwanime.data.meta.SkipSegment
import com.fuwanime.ui.player.SkipAction
import com.fuwanime.ui.player.SkipSegmentAction
import com.fuwanime.ui.player.episodeNumberForSkip
import com.fuwanime.ui.player.segmentsFor
import com.fuwanime.ui.player.shouldAutoSkip
import com.fuwanime.ui.player.skipActionAt
import com.fuwanime.ui.player.skipLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipSegmentsTest {
    private val op = SkipSegment(SegmentKind.OP, 80_000, 170_000)
    private val ed = SkipSegment(SegmentKind.ED, 1_300_000, 1_390_000)
    private val mark = OpMark(10_000, 60_000)

    @Test fun userMarkBeatsAniSkipOp() = assertEquals(SkipAction.SkipOp(60_000), skipActionAt(30_000, 1_420_000, mark, segments = listOf(op)))
    @Test fun userMarkIgnoresAniSkipOpEvenInsideIt() = assertNull(skipActionAt(100_000, 1_420_000, mark, segments = listOf(op)))
    @Test fun aniSkipOpWithoutMark() = assertEquals(SkipSegmentAction(SegmentKind.OP, 170_000), skipActionAt(100_000, 1_420_000, null, segments = listOf(op)))
    @Test fun aniSkipEdWithMark() = assertEquals(SkipSegmentAction(SegmentKind.ED, 1_390_000), skipActionAt(1_310_000, 1_420_000, mark, segments = listOf(ed)))
    @Test fun lastSecondOfSegmentHidden() = assertNull(skipActionAt(169_500, 1_420_000, null, segments = listOf(op)))

    @Test fun genericJumpOnlyWithoutAniSkipOp() {
        assertEquals(SkipAction.JumpAhead(95_000), skipActionAt(10_000, 1_420_000, null, segments = listOf(ed)))
        assertNull(skipActionAt(10_000, 1_420_000, null, segments = listOf(op)))
    }

    @Test fun autoSkipCoversAniSkipOpNotEd() {
        assertTrue(shouldAutoSkip(true, SkipSegmentAction(SegmentKind.OP, 1), "e1", null))
        assertFalse(shouldAutoSkip(true, SkipSegmentAction(SegmentKind.ED, 1), "e1", null))
        assertFalse(shouldAutoSkip(true, SkipSegmentAction(SegmentKind.RECAP, 1), "e1", null))
        assertFalse(shouldAutoSkip(true, SkipSegmentAction(SegmentKind.OP, 1), "e1", "e1"))
    }

    @Test fun labels() {
        assertEquals(R.string.watch_skip_op, skipLabel(SkipAction.SkipOp(1)))
        assertEquals(R.string.watch_skip_op, skipLabel(SkipSegmentAction(SegmentKind.OP, 1)))
        assertEquals(R.string.watch_skip_ed, skipLabel(SkipSegmentAction(SegmentKind.ED, 1)))
        assertEquals(R.string.watch_skip_recap, skipLabel(SkipSegmentAction(SegmentKind.RECAP, 1)))
        assertEquals(R.string.watch_skip_jump, skipLabel(SkipAction.JumpAhead(1)))
    }

    @Test fun episodeNumbers() {
        assertEquals(12, episodeNumberForSkip("12"))
        assertEquals(1, episodeNumberForSkip(" 01 "))
        assertNull(episodeNumberForSkip("12.5"))
        assertNull(episodeNumberForSkip("OVA"))
        assertNull(episodeNumberForSkip(""))
        assertNull(episodeNumberForSkip("0"))
    }

    @Test fun segmentsForOtherEpisodeIsEmpty() {
        assertEquals(emptyList<SkipSegment>(), segmentsFor("e1" to listOf(op), "e2"))
        assertEquals(listOf(op), segmentsFor("e1" to listOf(op), "e1"))
    }
}
