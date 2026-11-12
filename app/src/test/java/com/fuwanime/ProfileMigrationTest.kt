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
class ProfileMigrationTest {
    private val tables = listOf("user_profile", "quest_progress", "counted_items", "unlocked_borders")

    private fun columns(db: SupportSQLiteDatabase, table: String): Map<String, Triple<String, Int, Int>> {
        // name -> (type, notnull, pk)
        val out = HashMap<String, Triple<String, Int, Int>>()
        db.query("PRAGMA table_info($table)").use { c ->
            while (c.moveToNext()) out[c.getString(1)] = Triple(c.getString(2), c.getInt(3), c.getInt(5))
        }
        return out
    }

    @Test fun migration7to8CreatesTablesMatchingTheEntities() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                override fun onCreate(db: SupportSQLiteDatabase) {}
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        val migrated = FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
        FuwaDatabase.MIGRATION_7_8.migrate(migrated)

        // The schema Room builds from the entities is the ground truth; a mismatch would crash on open.
        val room = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FuwaDatabase::class.java).build()
        try {
            val expected = room.openHelper.writableDatabase
            tables.forEach { assertEquals(it, columns(expected, it), columns(migrated, it)) }
        } finally {
            room.close()
        }
    }

    @Test fun profileTableKeepsNoQuestCounterColumns() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                override fun onCreate(db: SupportSQLiteDatabase) {}
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        val db = FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
        FuwaDatabase.MIGRATION_7_8.migrate(db)
        assertEquals(setOf("id", "name", "xp", "equippedBorder"), columns(db, "user_profile").keys)
    }
}
