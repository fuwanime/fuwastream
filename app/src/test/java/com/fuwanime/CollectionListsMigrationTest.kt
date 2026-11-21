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
class CollectionListsMigrationTest {
    private fun openV12(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(12) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // content_preferences exactly as MIGRATION_6_7 created it, with one existing row.
                    db.execSQL(
                        "CREATE TABLE `content_preferences` (`key` TEXT NOT NULL, `mediaType` TEXT NOT NULL, `contentId` TEXT NOT NULL, " +
                            "`notifyMode` TEXT NOT NULL, `notifyLanguage` TEXT NOT NULL, `autoDownload` INTEGER NOT NULL, `autoDownloadLimit` INTEGER NOT NULL, " +
                            "`readerMode` TEXT, `hiddenFromContinue` INTEGER NOT NULL, `reminderMinutes` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`key`))",
                    )
                    db.execSQL(
                        "INSERT INTO content_preferences VALUES ('ANIME:a1', 'ANIME', 'a1', 'ALL', 'APP', 1, 3, NULL, 0, -1, 500)",
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test fun migration12to13KeepsPreferencesAndAddsListsAndSavedFilters() {
        val db = openV12()
        FuwaDatabase.MIGRATION_12_13.migrate(db)

        // The existing row survives with unrated/empty defaults.
        db.query("SELECT personalRating, personalNote, notifyMode, autoDownload FROM content_preferences WHERE `key` = 'ANIME:a1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
            assertEquals("", c.getString(1))
            assertEquals("ALL", c.getString(2))
            assertEquals(1, c.getInt(3))
        }
        assertEquals(
            setOf("id", "name", "emoji", "sortOrder", "createdAt"),
            columns(db, "collection_lists"),
        )
        assertEquals(
            setOf("listId", "mediaType", "contentId", "title", "poster", "addedAt"),
            columns(db, "collection_items"),
        )
        assertEquals(
            setOf("id", "name", "query", "filterJson", "createdAt"),
            columns(db, "saved_search_filters"),
        )

        // The new tables accept the rows the app writes.
        db.execSQL("INSERT INTO collection_lists (name, emoji, sortOrder, createdAt) VALUES ('Favoritku', '🌸', 0, 1)")
        db.execSQL("INSERT INTO collection_items VALUES (1, 'ANIME', 'a1', 'Judul', 'poster.jpg', 2)")
        db.execSQL("INSERT INTO saved_search_filters (name, query, filterJson, createdAt) VALUES ('Isekai', 'isekai', '{}', 3)")
        db.query("SELECT title FROM collection_items WHERE listId = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Judul", c.getString(0))
        }

        // Idempotent: a fork install that already ran part of v13 must not crash.
        FuwaDatabase.MIGRATION_12_13.migrate(db)
    }

    private fun columns(db: SupportSQLiteDatabase, table: String): Set<String> {
        val out = HashSet<String>()
        db.query("PRAGMA table_info(`$table`)").use { c -> while (c.moveToNext()) out += c.getString(1) }
        return out
    }
}
