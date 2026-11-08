package com.fuwanime

import com.fuwanime.data.Anime
import com.fuwanime.data.Episode
import com.fuwanime.data.local.VideoResize
import com.fuwanime.ui.player.NowPlaying
import com.fuwanime.ui.player.StopAction
import com.fuwanime.ui.player.clampPipAspect
import com.fuwanime.ui.player.isFullscreen
import com.fuwanime.ui.player.nowPlaying
import com.fuwanime.ui.player.pinchResize
import com.fuwanime.ui.player.stopAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "id")
class PlayerPolicyTest {
    private fun anime(title: String, poster: String) = Anime(id = "a", title = title, poster = poster)

    @Test fun closingPipPauses() = assertEquals(StopAction.PAUSE, stopAction(inPip = true, screenOn = true, backgroundAudio = true))
    // Locking the phone during PiP also stops the activity; that is "screen off", not "window closed".
    @Test fun screenOffInPipKeepsPlayingWithSetting() =
        assertEquals(StopAction.KEEP_PLAYING, stopAction(inPip = true, screenOn = false, backgroundAudio = true))
    @Test fun screenOffInPipPausesWithoutSetting() =
        assertEquals(StopAction.PAUSE, stopAction(inPip = true, screenOn = false, backgroundAudio = false))
    @Test fun leavingWithSettingOffPauses() = assertEquals(StopAction.PAUSE, stopAction(inPip = false, screenOn = true, backgroundAudio = false))
    @Test fun leavingWithSettingOnKeepsPlaying() = assertEquals(StopAction.KEEP_PLAYING, stopAction(inPip = false, screenOn = true, backgroundAudio = true))

    @Test fun normalAspectUnchanged() = assertEquals(1920 to 1080, clampPipAspect(1920, 1080))
    @Test fun ultrawideClampedToMax() = assertEquals(239 to 100, clampPipAspect(2400, 1000))
    @Test fun tallClampedToMin() = assertEquals(100 to 239, clampPipAspect(1000, 3000))
    @Test fun zeroSizeFallsBack() = assertEquals(16 to 9, clampPipAspect(0, 0))
    @Test fun negativeSizeFallsBack() = assertEquals(16 to 9, clampPipAspect(-1, 720))

    @Test fun pinchOutZooms() = assertEquals(VideoResize.ZOOM, pinchResize(VideoResize.FIT, 1.15f))
    @Test fun pinchInFits() = assertEquals(VideoResize.FIT, pinchResize(VideoResize.ZOOM, 0.87f))
    @Test fun smallPinchIgnored() {
        assertNull(pinchResize(VideoResize.FIT, 1.1f))
        assertNull(pinchResize(VideoResize.ZOOM, 0.9f))
    }
    @Test fun pinchToCurrentModeIsNull() {
        assertNull(pinchResize(VideoResize.ZOOM, 1.5f))
        assertNull(pinchResize(VideoResize.FIT, 0.5f))
    }

    @Test fun landscapeIsFullscreen() = assertTrue(isFullscreen(landscape = true, manual = null, inPip = false))
    // A tablet that ignores the portrait request stays landscape; the user's exit must still win.
    @Test fun manualExitInLandscapeIsNotFullscreen() = assertFalse(isFullscreen(landscape = true, manual = false, inPip = false))
    @Test fun manualPortraitIsFullscreen() = assertTrue(isFullscreen(landscape = false, manual = true, inPip = false))
    @Test fun pipIsNeverFullscreen() = assertFalse(isFullscreen(landscape = true, manual = true, inPip = true))
    @Test fun portraitNotManualIsNotFullscreen() = assertFalse(isFullscreen(landscape = false, manual = null, inPip = false))

    @Test fun nowPlayingUsesEpisodeAndAnime() = assertEquals(
        NowPlaying("Pertemuan", "Frieren", "Episode 3", "https://t/3.jpg"),
        nowPlaying(testResources(), Episode(id = "e3", number = "3", title = "Pertemuan", thumbnail = "https://t/3.jpg"), anime("Frieren", "https://p.jpg")),
    )
    @Test fun nowPlayingFallsBack() = assertEquals(
        NowPlaying("Frieren", "Frieren", "Episode 3", "https://p.jpg"),
        nowPlaying(testResources(), Episode(id = "e3", number = "3", title = ""), anime("Frieren", "https://p.jpg")),
    )
    @Test fun nowPlayingWithoutAnimeOrNumber() = assertEquals(
        NowPlaying("Spesial", "", "", ""),
        nowPlaying(testResources(), Episode(id = "x", number = "", title = "Spesial"), null),
    )
}
