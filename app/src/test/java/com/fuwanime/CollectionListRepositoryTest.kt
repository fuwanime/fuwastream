package com.fuwanime

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fuwanime.data.CollectionListRepository
import com.fuwanime.data.local.FuwaDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CollectionListRepositoryTest {
    private lateinit var db: FuwaDatabase
    private lateinit var repo: CollectionListRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FuwaDatabase::class.java).build()
        repo = CollectionListRepository(db)
    }

    @After fun tearDown() = db.close()

    @Test fun createRenameReorderAndDelete() = runBlocking {
        val a = repo.createList("Pertama", "🌸")
        val b = repo.createList("Kedua", null)
        assertTrue(a > 0 && b > 0)
        assertEquals(listOf("Pertama", "Kedua"), repo.lists.first().map { it.name })

        repo.renameList(a, "Ubah", "⭐")
        assertEquals("Ubah" to "⭐", repo.lists.first().first().let { it.name to it.emoji })

        // Move the second list up: order flips, and moving past the edge is a no-op.
        repo.moveList(b, up = true)
        assertEquals(listOf("Kedua", "Ubah"), repo.lists.first().map { it.name })
        repo.moveList(b, up = true)
        assertEquals(listOf("Kedua", "Ubah"), repo.lists.first().map { it.name })

        // Deleting a list removes its items but not the other list's.
        repo.addItem(a, "ANIME", "x1", "Judul X", "p.jpg")
        repo.addItem(b, "ANIME", "x1", "Judul X", "p.jpg")
        repo.deleteList(a)
        assertEquals(listOf("Kedua"), repo.lists.first().map { it.name })
        assertEquals(listOf(b), repo.items.first().map { it.listId })
    }

    @Test fun membershipTracksAddAndRemove() = runBlocking {
        val list = repo.createList("Satu", null)
        assertEquals(emptySet<Long>(), repo.memberships("ANIME", "a1").first())
        repo.addItem(list, "ANIME", "a1", "A", "poster")
        assertEquals(setOf(list), repo.memberships("ANIME", "a1").first())
        // Upsert: adding twice keeps one row.
        repo.addItem(list, "ANIME", "a1", "A", "poster")
        assertEquals(1, repo.items.first().size)
        repo.removeItem(list, "ANIME", "a1")
        assertEquals(emptySet<Long>(), repo.memberships("ANIME", "a1").first())
        // Manga and anime ids never collide.
        repo.addItem(list, "MANGA", "a1", "M", "cover")
        assertEquals(emptySet<Long>(), repo.memberships("ANIME", "a1").first())
        assertEquals(setOf(list), repo.memberships("MANGA", "a1").first())
    }

    @Test fun blankNamesAreRejected() = runBlocking {
        assertEquals(-1, repo.createList("   ", null))
        assertTrue(repo.lists.first().isEmpty())
    }
}
