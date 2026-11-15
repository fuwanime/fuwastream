package com.fuwanime

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.fuwanime.data.local.FuwaDatabase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PersonalizationMigrationTest {
    @Test fun migration6to7AddsCollectionStatusAndPreferences() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Only the columns the migration reads/writes are needed for this check.
                    db.execSQL("CREATE TABLE favorites (animeId TEXT NOT NULL PRIMARY KEY)")
                    db.execSQL("CREATE TABLE manga_favorite (mangaId TEXT NOT NULL PRIMARY KEY)")
                    db.execSQL("CREATE TABLE watch_history (animeId TEXT NOT NULL)")
                    db.execSQL("CREATE TABLE manga_history (mangaId TEXT NOT NULL)")
                    db.execSQL("INSERT INTO favorites VALUES ('watched')")
                    db.execSQL("INSERT INTO favorites VALUES ('planned')")
                    db.execSQL("INSERT INTO watch_history VALUES ('watched')")
                    db.execSQL("INSERT INTO manga_favorite VALUES ('read')")
                    db.execSQL("INSERT INTO manga_favorite VALUES ('planned')")
                    db.execSQL("INSERT INTO manga_history VALUES ('read')")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        val db = FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
        FuwaDatabase.MIGRATION_6_7.migrate(db)

        val anime = HashMap<String, String>()
        db.query("SELECT animeId, collectionStatus FROM favorites").use { c ->
            while (c.moveToNext()) anime[c.getString(0)] = c.getString(1)
        }
        assertEquals(mapOf("watched" to "IN_PROGRESS", "planned" to "PLANNED"), anime)

        val manga = HashMap<String, String>()
        db.query("SELECT mangaId, collectionStatus FROM manga_favorite").use { c ->
            while (c.moveToNext()) manga[c.getString(0)] = c.getString(1)
        }
        assertEquals(mapOf("read" to "IN_PROGRESS", "planned" to "PLANNED"), manga)

        assertEquals(
            setOf("key", "mediaType", "contentId", "notifyMode", "notifyLanguage", "autoDownload", "autoDownloadLimit", "readerMode", "hiddenFromContinue", "reminderMinutes", "updatedAt"),
            columns(db, "content_preferences"),
        )
        assertEquals(setOf("sourceKey", "successes", "failures", "startupTotalMs", "lastUsedAt"), columns(db, "source_stats"))
    }

    private fun columns(db: SupportSQLiteDatabase, table: String): Set<String> {
        val out = HashSet<String>()
        db.query("PRAGMA table_info(`$table`)").use { c -> while (c.moveToNext()) out += c.getString(1) }
        return out
    }
}
