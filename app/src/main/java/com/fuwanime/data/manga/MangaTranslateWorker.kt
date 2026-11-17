package com.fuwanime.data.manga

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.fuwanime.FuwaApp
import com.fuwanime.R
import kotlinx.coroutines.CancellationException

/**
 * Translates a manga chapter in the background so it can continue after the reader is closed.
 * Pages are cached on disk by [IsMangaChapterTranslator]; partially translated chapters resume on retry.
 */
class MangaTranslateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val chapterId = inputData.getString(KEY_CHAPTER) ?: return Result.failure()
        val mangaTitle = inputData.getString(KEY_MANGA_TITLE).orEmpty()
        val chapterLabel = inputData.getString(KEY_CHAPTER_LABEL).orEmpty()
        val c = (applicationContext as FuwaApp).container
        val title = if (chapterLabel.isBlank()) mangaTitle.ifBlank { applicationContext.getString(R.string.manga_translate_notif_fallback) }
        else applicationContext.getString(R.string.manga_translate_notif, mangaTitle.ifBlank { chapterLabel }, chapterLabel)
        return try {
            runCatching { setForeground(info(title, 0, 0)) }
            val pages = c.mangaRepository.pages(chapterId)
            if (pages.isEmpty()) return Result.failure(workDataOf(KEY_ERROR to "empty"))
            val cached = c.mangaTranslator.cachedChapterPages(applicationContext.cacheDir, chapterId, pages.size)
            if (cached != null) {
                return Result.success(workDataOf(KEY_DONE to pages.size, KEY_TOTAL to pages.size))
            }
            val translated = c.mangaTranslator.translateChapter(chapterId, pages, applicationContext.cacheDir) { done, total ->
                // Progress callbacks are not suspending; use the async WorkManager APIs here.
                runCatching { setForegroundAsync(info(title, done, total)) }
                setProgressAsync(workDataOf(KEY_DONE to done, KEY_TOTAL to total))
            }
            Result.success(workDataOf(KEY_DONE to translated.size, KEY_TOTAL to translated.size))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 2) {
                Result.retry()
            } else {
                Result.failure(workDataOf(KEY_ERROR to (e.message ?: "error")))
            }
        }
    }

    private fun info(title: String, done: Int, total: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(R.string.manga_translate_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val text = if (total > 0) applicationContext.getString(R.string.manga_translate_progress, done, total)
        else applicationContext.getString(R.string.manga_translate_running)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(total.coerceAtLeast(0), done.coerceAtLeast(0), total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(NOTIFICATION_ID_BASE + (id.hashCode() and 0xFFF), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIFICATION_ID_BASE + (id.hashCode() and 0xFFF), notification)
    }

    companion object {
        const val KEY_CHAPTER = "chapterId"
        const val KEY_MANGA_TITLE = "mangaTitle"
        const val KEY_CHAPTER_LABEL = "chapterLabel"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        private const val CHANNEL_ID = "manga_translate"
        private const val NOTIFICATION_ID_BASE = 7_401

        fun uniqueName(chapterId: String): String = "manga_translate_$chapterId"

        /** Enqueues (or keeps) a background translation for [chapterId]. */
        fun enqueue(context: Context, chapterId: String, mangaTitle: String, chapterLabel: String) {
            val request = OneTimeWorkRequestBuilder<MangaTranslateWorker>()
                .setInputData(
                    workDataOf(
                        KEY_CHAPTER to chapterId,
                        KEY_MANGA_TITLE to mangaTitle,
                        KEY_CHAPTER_LABEL to chapterLabel,
                    ),
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag(uniqueName(chapterId))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(uniqueName(chapterId), ExistingWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context, chapterId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueName(chapterId))
        }
    }
}
