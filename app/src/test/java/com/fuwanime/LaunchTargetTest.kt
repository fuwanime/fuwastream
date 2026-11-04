package com.fuwanime

import com.fuwanime.ui.navigation.LaunchTarget
import com.fuwanime.ui.navigation.parseLaunchTarget
import com.fuwanime.ui.navigation.resumeRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LaunchTargetTest {
    @Test fun resumeWins() = assertEquals(LaunchTarget.Resume("e1"), parseLaunchTarget("a1", "schedule", "e1"))
    @Test fun animeWhenNoResume() = assertEquals(LaunchTarget.Anime("a1"), parseLaunchTarget("a1", "schedule", null))
    @Test fun tabSearch() = assertEquals(LaunchTarget.Search, parseLaunchTarget(null, "explore_search", null))
    @Test fun tabSchedule() = assertEquals(LaunchTarget.Schedule, parseLaunchTarget(null, "schedule", null))
    @Test fun unknownTabNull() = assertNull(parseLaunchTarget(null, "settings", null))
    @Test fun blanksNull() = assertNull(parseLaunchTarget(" ", "", " "))
    @Test fun resumeWithoutHistoryFallsBackHome() = assertNull(resumeRoute(null))
}
