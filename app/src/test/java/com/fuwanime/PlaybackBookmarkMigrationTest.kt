package com.fuwanime

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.fuwanime.data.local.FuwaDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PlaybackBookmarkMigrationTest {
    private fun openV13(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(13) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // favorites exactly as v7 left it, with one existing row.
                    db.execSQL(
                        "CREATE TABLE `favorites` (`animeId` TEXT NOT NULL, `title` TEXT NOT NULL, `poster` TEXT NOT NULL, `cover` TEXT NOT NULL, " +
                            "`type` TEXT NOT NULL, `status` TEXT NOT NULL, `year` TEXT NOT NULL, `genres` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, " +
                            "`collectionStatus` TEXT NOT NULL DEFAULT 'PLANNED', PRIMARY KEY(`animeId`))",
                    )
                    db.execSQL("INSERT INTO favorites VALUES ('a1', 'Frieren', 'p', 'c', 'TV', 'ONGOING', '2026', '', 5, 'PLANNED')")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test fun migration13to14AddsBookmarksAndKeepsExistingData() {
        val db = openV13()
        FuwaDatabase.MIGRATION_13_14.migrate(db)

        // The existing row survives untouched.
        db.query("SELECT title, collectionStatus FROM favorites WHERE animeId = 'a1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Frieren", c.getString(0))
            assertEquals("PLANNED", c.getString(1))
        }
        assertEquals(
            setOf("id", "mediaType", "contentId", "episodeId", "positionMs", "label", "coverUrl", "createdAt"),
            columns(db, "playback_bookmarks"),
        )
        val indices = HashSet<String>()
        db.query("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='playback_bookmarks'").use { c ->
            while (c.moveToNext()) indices += c.getString(0)
        }
        assertTrue("index_playback_bookmarks_contentId" in indices)
        assertTrue("index_playback_bookmarks_episodeId" in indices)

        // The new table accepts the rows the app writes.
        db.execSQL(
            "INSERT INTO playback_bookmarks (mediaType, contentId, episodeId, positionMs, label, coverUrl, createdAt) " +
                "VALUES ('ANIME', 'a1', 'a1-1', 900, 'Momen 00:00', 't', 1)",
        )
        db.query("SELECT positionMs, label FROM playback_bookmarks WHERE episodeId = 'a1-1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(900, c.getLong(0))
            assertEquals("Momen 00:00", c.getString(1))
        }

        // Idempotent: re-running must not crash or duplicate anything.
        FuwaDatabase.MIGRATION_13_14.migrate(db)
        db.query("SELECT COUNT(*) FROM playback_bookmarks").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
    }

    private fun columns(db: SupportSQLiteDatabase, table: String): Set<String> {
        val out = HashSet<String>()
        db.query("PRAGMA table_info(`$table`)").use { c -> while (c.moveToNext()) out += c.getString(1) }
        return out
    }
}
