package com.fuwanime.data.manga

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.fuwanime.FuwaApp
import com.fuwanime.R
import com.fuwanime.data.local.MangaDownloadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/** Downloads one queued chapter; the page loop is [downloadPages]. */
class MangaDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val chapterId = inputData.getString(KEY_CHAPTER) ?: return Result.success()
        val c = (applicationContext as FuwaApp).container
        val dao = c.mangaDownloadDao
        val row = dao.get(chapterId) ?: return Result.success() // deleted while queued
        dao.setState(chapterId, MangaDownloadState.RUNNING.name)
        val title = applicationContext.getString(R.string.manga_download_notif, row.mangaTitle, row.chapterLabel)
        return try {
            runCatching { setForeground(info(title, 0, 0)) } // a refused foreground start still lets the work run
            val urls = c.mangaRepository.pages(chapterId, c.settingsStore.mangaDataSaver.first(), forceRefresh = true)
            dao.progress(chapterId, urls.size, 0, 0)
            val dir = chapterDir(c.mangaDownloads.root(), row.mangaId, chapterId)
            val finished = downloadPages(urls, dir, c.httpClient, keepGoing = { dao.get(chapterId) != null }) { done, bytes ->
                dao.progress(chapterId, urls.size, done, bytes)
                runCatching { setForeground(info(title, done, urls.size)) }
            }
            if (!finished) {
                dir.deleteRecursively()
                return Result.success()
            }
            dao.setState(chapterId, MangaDownloadState.DONE.name)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (dao.get(chapterId) == null) return Result.success()
            if (runAttemptCount < 2) {
                dao.setState(chapterId, MangaDownloadState.QUEUED.name)
                Result.retry()
            } else {
                dao.setState(chapterId, MangaDownloadState.FAILED.name)
                Result.failure()
            }
        }
    }

    private fun info(title: String, done: Int, total: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, applicationContext.getString(R.string.manga_download_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {
        const val KEY_CHAPTER = "chapterId"
        private const val CHANNEL_ID = "manga_downloads"
        private const val NOTIFICATION_ID = 7_301
    }
}
