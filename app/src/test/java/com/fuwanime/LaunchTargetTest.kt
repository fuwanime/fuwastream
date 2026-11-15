package com.fuwanime

import android.content.Intent
import com.fuwanime.ui.navigation.LaunchTarget
import com.fuwanime.ui.navigation.isFreshLaunch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import com.fuwanime.ui.navigation.parseLaunchTarget
import com.fuwanime.ui.navigation.resumeRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LaunchTargetTest {
    @Test fun resumeWins() = assertEquals(LaunchTarget.Resume("e1"), parseLaunchTarget("a1", "schedule", "e1"))
    @Test fun animeWhenNoResume() = assertEquals(LaunchTarget.Anime("a1"), parseLaunchTarget("a1", "schedule", null))
    @Test fun mangaWhenNoAnime() = assertEquals(LaunchTarget.Manga("m1"), parseLaunchTarget(null, null, null, "m1"))
    @Test fun episodeDeepLink() =
        assertEquals(LaunchTarget.Episode("a1", "e1", "3"), parseLaunchTarget("a1", null, null, null, "e1", "3"))
    @Test fun episodeDeepLinkWithoutNumber() =
        assertEquals(LaunchTarget.Episode("a1", "e1", ""), parseLaunchTarget("a1", null, null, null, "e1", null))
    @Test fun mangaChapterDeepLink() =
        assertEquals(LaunchTarget.MangaChapter("m1", "c1"), parseLaunchTarget(null, null, null, "m1", null, null, "c1"))
    @Test fun resumeStillBeatsEpisodeDeepLink() =
        assertEquals(LaunchTarget.Resume("old"), parseLaunchTarget("a1", null, "old", null, "e1", "3"))
    @Test fun chapterWithoutMangaFallsBackToTab() =
        assertEquals(LaunchTarget.Schedule, parseLaunchTarget(null, "schedule", null, null, null, null, "c1"))
    @Test fun tabSearch() = assertEquals(LaunchTarget.Search, parseLaunchTarget(null, "explore_search", null))
    @Test fun tabSchedule() = assertEquals(LaunchTarget.Schedule, parseLaunchTarget(null, "schedule", null))
    @Test fun unknownTabNull() = assertNull(parseLaunchTarget(null, "settings", null))
    @Test fun blanksNull() = assertNull(parseLaunchTarget(" ", "", " "))
    @Test fun resumeWithoutHistoryFallsBackHome() = assertNull(resumeRoute(null))

    @Test fun freshLaunch_appliesTarget() = assertTrue(isFreshLaunch(restored = false, flags = 0))
    @Test fun restoredState_skipsTarget() = assertFalse(isFreshLaunch(restored = true, flags = 0))
    @Test fun fromRecents_skipsTarget() = assertFalse(isFreshLaunch(restored = false, flags = Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))
}
