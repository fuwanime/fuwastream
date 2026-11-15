package com.fuwanime

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fuwanime.data.LibraryRepository
import com.fuwanime.data.ProfileRepository
import com.fuwanime.data.ProfileRules
import com.fuwanime.data.ProfileUi
import com.fuwanime.data.local.FuwaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Quest, XP, claim and wipe rules from the profile review (points 2-8). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ProfileLogicTest {
    private lateinit var db: FuwaDatabase
    private lateinit var repo: ProfileRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FuwaDatabase::class.java).build()
        repo = ProfileRepository(db)
    }

    @After fun tearDown() = db.close()

    private suspend fun ui(): ProfileUi = repo.observe().first()
    private suspend fun progress(id: String) = ui().quests.first { it.def.id == id }.progress

    @Test fun finishThresholdsAreInclusive() {
        assertFalse(ProfileRules.episodeWatched(919, 1000))
        assertTrue(ProfileRules.episodeWatched(920, 1000))
        assertFalse(ProfileRules.episodeWatched(1000, 0))
        assertFalse(ProfileRules.chapterRead(7, 10)) // page 8 of 10 = 80%
        assertTrue(ProfileRules.chapterRead(8, 10)) // page 9 of 10 = 90%
        assertFalse(ProfileRules.chapterRead(0, 0))
    }

    @Test fun levelTwoNeeds1201Xp() {
        assertEquals(1, ProfileRules.levelFor(1_200))
        assertEquals(2, ProfileRules.levelFor(1_201))
        assertEquals(10, ProfileRules.levelFor(Long.MAX_VALUE))
    }

    // Bug 2: saveProgress runs every 5 s, so one episode used to count about 20 times.
    @Test fun episodeCountsOncePerId() = runBlocking {
        repeat(20) { repo.onEpisodeWatched("e1") }
        assertEquals(1, progress(ProfileRules.QUEST_WATCH_EPISODES))
        repo.onEpisodeWatched("e2")
        assertEquals(2, progress(ProfileRules.QUEST_WATCH_EPISODES))
    }

    // Bug 3: every page turn past 90% used to count the chapter again.
    @Test fun chapterCountsOncePerId() = runBlocking {
        repeat(5) { repo.onChapterRead("c1") }
        assertEquals(1, progress(ProfileRules.QUEST_READ_CHAPTERS))
        assertEquals(0, progress(ProfileRules.QUEST_WATCH_EPISODES))
    }

    // Bug 5: (5000 / 60000).toInt() was always 0, so the 60-minute quest could never move.
    @Test fun watchQuestAdvancesInSeconds() = runBlocking {
        repo.recordWatch(5_000)
        assertEquals(5, progress(ProfileRules.QUEST_WATCH_SECONDS))
        repo.recordWatch(3_600_000)
        assertEquals(3_600, progress(ProfileRules.QUEST_WATCH_SECONDS)) // capped at the target
    }

    // Bug 6: XP used to be added by two code paths, giving 2 XP per 5 s.
    @Test fun fiveSecondsOfWatchingIsOneXp() = runBlocking {
        repo.recordWatch(5_000)
        assertEquals(1L, ui().xp)
    }

    @Test fun levelUpIsAnnouncedOnce() = runBlocking {
        val ups = mutableListOf<Int>()
        val job = async(Dispatchers.Unconfined) { repo.levelUps.collect { ups += it } }
        repo.recordWatch(1_201 * 5_000L)
        job.cancel()
        assertEquals(listOf(2), ups)
    }

    // Bug 4: claiming never unlocked the border, so equipping was always refused.
    @Test fun claimUnlocksTheBorderAndItCanBeEquipped() = runBlocking {
        assertNull(repo.claim(ProfileRules.QUEST_WATCH_EPISODES)) // not finished yet
        assertFalse(repo.setEquippedBorder(ProfileRules.BORDER_SAKURA))
        repeat(5) { repo.onEpisodeWatched("e$it") }
        val claimed = repo.claim(ProfileRules.QUEST_WATCH_EPISODES)
        assertNotNull(claimed)
        assertEquals(ProfileRules.BORDER_SAKURA, claimed!!.borderId)
        assertTrue(repo.setEquippedBorder(ProfileRules.BORDER_SAKURA))
        assertEquals(ProfileRules.BORDER_SAKURA, ui().equippedBorder)
        assertFalse(repo.setEquippedBorder(ProfileRules.BORDER_GOLD)) // never earned
    }

    // Bug 7: a fast double tap used to pay the reward twice.
    @Test fun doubleClaimPaysOnce() = runBlocking {
        repeat(5) { repo.onEpisodeWatched("e$it") }
        val results = (1..8).map { async(Dispatchers.Default) { repo.claim(ProfileRules.QUEST_WATCH_EPISODES) } }.awaitAll()
        assertEquals(1, results.count { it != null })
        assertEquals(150L, ui().xp)
        assertTrue(ui().quests.first { it.def.id == ProfileRules.QUEST_WATCH_EPISODES }.claimed)
    }

    // Bug 8: "delete all data" has to wipe the profile, quests, counters and borders too.
    @Test fun deleteAllDataWipesProfileData() = runBlocking {
        repo.setName("Ciel")
        repeat(5) { repo.onEpisodeWatched("e$it") }
        repo.claim(ProfileRules.QUEST_WATCH_EPISODES)
        repo.setEquippedBorder(ProfileRules.BORDER_SAKURA)
        LibraryRepository(db).clearAll()
        val after = ui()
        assertEquals("", after.name)
        assertEquals(0L, after.xp)
        assertTrue(after.unlockedBorders.isEmpty())
        assertNull(after.equippedBorder)
        assertTrue(after.quests.all { it.progress == 0 && !it.claimed })
        // The once-only counters are gone as well, so the same episode counts again after a wipe.
        repo.onEpisodeWatched("e0")
        assertEquals(1, progress(ProfileRules.QUEST_WATCH_EPISODES))
    }
}
