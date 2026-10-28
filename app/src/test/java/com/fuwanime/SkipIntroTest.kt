package com.fuwanime

import com.fuwanime.data.OpMark
import com.fuwanime.ui.player.SkipAction
import com.fuwanime.ui.player.markOp
import com.fuwanime.ui.player.skipActionAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SkipIntroTest {
    private val mark = OpMark(startMs = 60_000, endMs = 150_000)
    private val duration = 24 * 60_000L

    @Test
    fun markedOp_offersSkipToItsEndOnlyInsideTheRange() {
        assertEquals(SkipAction.SkipOp(150_000), skipActionAt(60_000, duration, mark))
        assertEquals(SkipAction.SkipOp(150_000), skipActionAt(120_000, duration, mark))
        assertNull(skipActionAt(59_000, duration, mark))
        assertNull(skipActionAt(149_500, duration, mark)) // within the last second: nothing worth skipping
        assertNull(skipActionAt(200_000, duration, mark))
    }

    @Test
    fun withoutMark_offersJumpAheadDuringTheFirstThreeMinutes() {
        assertNull(skipActionAt(4_000, duration, null))
        assertEquals(SkipAction.JumpAhead(90_000), skipActionAt(5_000, duration, null))
        assertEquals(SkipAction.JumpAhead(264_000), skipActionAt(179_000, duration, null))
        assertNull(skipActionAt(180_000, duration, null))
    }

    @Test
    fun jumpAhead_neverPassesTheEnd() {
        assertNull(skipActionAt(30_000, 100_000, null))
        assertEquals(SkipAction.JumpAhead(115_000), skipActionAt(30_000, 0, null)) // unknown duration
    }

    @Test
    fun invalidMark_fallsBackToJumpAhead() {
        assertEquals(SkipAction.JumpAhead(95_000), skipActionAt(10_000, duration, OpMark(90_000, 30_000)))
    }

    @Test
    fun markOp_startKeepsLaterEndOrGuessesOne() {
        assertEquals(OpMark(30_000, 120_000), markOp(null, 30_000, isStart = true))
        assertEquals(OpMark(30_000, 150_000), markOp(mark, 30_000, isStart = true))
        assertEquals(OpMark(200_000, 290_000), markOp(mark, 200_000, isStart = true))
    }

    @Test
    fun markOp_endKeepsEarlierStartOrGuessesOne() {
        assertEquals(OpMark(30_000, 120_000), markOp(null, 120_000, isStart = false))
        assertEquals(OpMark(0, 50_000), markOp(null, 50_000, isStart = false))
        assertEquals(OpMark(60_000, 100_000), markOp(mark, 100_000, isStart = false))
        assertEquals(OpMark(0, 40_000), markOp(mark, 40_000, isStart = false))
    }
}
