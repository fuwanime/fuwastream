package com.fuwanime

import android.app.Application
import androidx.room.Room
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
class AnimeLinkMigrationTest {
    private fun columns(db: SupportSQLiteDatabase): Map<String, Triple<String, Int, Int>> {
        val out = HashMap<String, Triple<String, Int, Int>>()
        db.query("PRAGMA table_info(anime_link)").use { c ->
            while (c.moveToNext()) out[c.getString(1)] = Triple(c.getString(2), c.getInt(3), c.getInt(5))
        }
        return out
    }

    @Test fun migration8to9AddsSourceTitles() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(8) {
                override fun onCreate(db: SupportSQLiteDatabase) {}
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        val migrated = FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
        // anime_link as created by MIGRATION_3_4 and unchanged until v8.
        migrated.execSQL(
            "CREATE TABLE IF NOT EXISTS `anime_link` (`animeId` TEXT NOT NULL, `state` TEXT NOT NULL, `anilistId` INTEGER, " +
                "`malId` INTEGER, `score` INTEGER, `tags` TEXT NOT NULL, `nextEpisode` INTEGER, `nextAiringAt` INTEGER, " +
                "`fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`animeId`))",
        )
        migrated.execSQL("INSERT INTO anime_link VALUES ('a1', 'LINKED', 7, 1007, 80, 'Action', NULL, NULL, 1)")
        FuwaDatabase.MIGRATION_8_9.migrate(migrated)

        migrated.query("SELECT sourceTitles FROM anime_link WHERE animeId = 'a1'").use { c ->
            c.moveToFirst()
            assertEquals("", c.getString(0))
        }
        val room = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FuwaDatabase::class.java).build()
        try {
            assertEquals(columns(room.openHelper.writableDatabase), columns(migrated))
        } finally {
            room.close()
        }
    }
}
