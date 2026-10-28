package com.fuwanime

import com.fuwanime.data.remote.DetailData
import com.fuwanime.ui.player.SkipAction
import com.fuwanime.ui.player.SleepOption
import com.fuwanime.ui.player.autoPlayAllowed
import com.fuwanime.ui.player.shouldAutoSkip
import com.fuwanime.ui.player.skipButtonVisible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression tests for the final review's findings. */
class ReviewFixesTest {
    @Test
    fun detailResponse_survivesEpisodeSentAsEmptyArray() {
        val json = Fixtures.text("detail.json").replace(Regex(""""episode":\s*\{[^}]*\}"""), "\"episode\": []")
        val adapter = Fixtures.moshi.adapter<com.fuwanime.data.remote.ApiResponse<DetailData>>(
            com.squareup.moshi.Types.newParameterizedType(com.fuwanime.data.remote.ApiResponse::class.java, DetailData::class.java),
        )
        assertEquals("4832", adapter.fromJson(json)?.data?.movie?.id)
    }

    @Test
    fun skipButton_staysOffThePortraitTransportControls() {
        assertTrue(skipButtonVisible(controlsVisible = false, fullscreen = false, locked = false))
        assertFalse(skipButtonVisible(controlsVisible = true, fullscreen = false, locked = false))
        assertTrue(skipButtonVisible(controlsVisible = true, fullscreen = true, locked = false))
        assertFalse(skipButtonVisible(controlsVisible = false, fullscreen = true, locked = true))
    }

    @Test
    fun autoSkip_firesOncePerEpisodeAndNotAfterMarkingIt() {
        val op = SkipAction.SkipOp(120_000)
        assertTrue(shouldAutoSkip(enabled = true, action = op, episodeId = "e1", handledEpisodeId = null))
        // Marking (or an earlier skip) in this episode records it as handled.
        assertFalse(shouldAutoSkip(enabled = true, action = op, episodeId = "e1", handledEpisodeId = "e1"))
        assertTrue(shouldAutoSkip(enabled = true, action = op, episodeId = "e2", handledEpisodeId = "e1"))
        assertFalse(shouldAutoSkip(enabled = false, action = op, episodeId = "e1", handledEpisodeId = null))
        assertFalse(shouldAutoSkip(enabled = true, action = SkipAction.JumpAhead(90_000), episodeId = "e1", handledEpisodeId = null))
    }

    @Test
    fun autoPlay_staysOffOnceTheSleepTimerFired() {
        assertFalse(autoPlayAllowed(true, SleepOption.OFF, sleepExpired = true))
        assertTrue(autoPlayAllowed(true, SleepOption.OFF, sleepExpired = false))
    }
}
