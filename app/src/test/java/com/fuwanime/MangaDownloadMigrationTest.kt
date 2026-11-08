package com.fuwanime

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.fuwanime.data.local.FuwaDatabase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MangaDownloadMigrationTest {
    @Test fun migration5to6CreatesDownloadsTableMatchingEntity() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                override fun onCreate(db: SupportSQLiteDatabase) {}
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        val db = FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
        FuwaDatabase.MIGRATION_5_6.migrate(db)
        // name -> (type, notnull, pk)
        val cols = HashMap<String, Triple<String, Int, Int>>()
        db.query("PRAGMA table_info(manga_downloads)").use { c ->
            while (c.moveToNext()) cols[c.getString(1)] = Triple(c.getString(2), c.getInt(3), c.getInt(5))
        }
        val text = setOf("chapterId", "mangaId", "mangaTitle", "coverUrl", "chapterNumber", "chapterLabel", "language", "state")
        val ints = setOf("pageCount", "pagesDone", "bytes", "createdAt")
        assertEquals(text + ints, cols.keys)
        text.forEach { assertEquals(it, "TEXT", cols[it]!!.first) }
        ints.forEach { assertEquals(it, "INTEGER", cols[it]!!.first) }
        cols.forEach { (name, v) -> assertEquals(name, if (name == "coverUrl" || name == "chapterNumber") 0 else 1, v.second) }
        assertEquals(1, cols["chapterId"]!!.third)
    }
}
