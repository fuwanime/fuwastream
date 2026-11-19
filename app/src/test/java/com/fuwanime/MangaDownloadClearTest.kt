package com.fuwanime

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.fuwanime.data.local.FuwaDatabase
import com.fuwanime.data.local.MangaDownloadState
import com.fuwanime.data.local.MangaDownloadEntity
import com.fuwanime.data.local.SettingsStore
import com.fuwanime.data.manga.MangaDownloadRepository
import com.fuwanime.data.manga.chapterDir
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MangaDownloadClearTest {
    private fun row(chapterId: String, mangaId: String, fileUri: String? = null) =
        MangaDownloadEntity(chapterId, mangaId, "Title $mangaId", null, "1", "Ch. 1", "id", 3, 3, 10, MangaDownloadState.DONE.name, 0, fileUri)

    @Test fun clearAllDeletesRowsPagesAndCbz() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, FuwaDatabase::class.java).build()
        val dao = db.mangaDownloadDao()
        val repo = MangaDownloadRepository(context, dao, SettingsStore(context))

        // Loose pages under the app's manga dir, plus a CBZ in a picked folder (file:// URI).
        val dir = chapterDir(repo.root(), "m1", "c1")
        dir.mkdirs()
        File(dir, "001.jpg").writeBytes(ByteArray(10))
        val cbz = File(repo.root(), "c2.cbz")
        cbz.writeBytes(ByteArray(20))
        dao.upsert(row("c1", "m1"))
        dao.upsert(row("c2", "m2", Uri.fromFile(cbz).toString()))

        repo.clearAll()

        assertTrue(dao.all().isEmpty())
        assertFalse(File(repo.root(), "manga").exists())
        assertFalse(cbz.exists())
        db.close()
    }
}
