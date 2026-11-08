package com.fuwanime.data.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import com.fuwanime.util.loadBitmap
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fuwanime.FuwaApp
import com.fuwanime.MainActivity
import com.fuwanime.R
import com.fuwanime.data.Episode
import com.fuwanime.data.local.FavoriteEntity
import com.fuwanime.util.localizedContext
import android.content.res.Resources
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodically checks favorited, ongoing anime for newly released episodes and posts a
 * notification. The first run only records a baseline per anime, so the user is never
 * greeted with a burst of "new episode" pings for content that already existed.
 */
class EpisodeNotifyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as FuwaApp).container
        val settings = container.settingsStore
        if (!settings.settings.first().notifyNewEpisodes) return Result.success()

        val favorites = container.libraryRepository.allFavorites()
            .filter { it.status.equals("ONGOING", true) }
            .take(25)
        val notifier = EpisodeNotifier(applicationContext)
        val notified = mutableListOf<String>()
        for (fav in favorites) {
            runCatching {
                val newest = container.animeRepository
                    .episodePage(fav.animeId, 0).getOrNull()
                    ?.maxByOrNull { it.sortKey } ?: return@runCatching
                if (newest.sortKey == Double.MAX_VALUE) return@runCatching

                val baseline = settings.notifyBaseline(fav.animeId)
                settings.setNotifyBaseline(fav.animeId, newest.sortKey)
                if (baseline != null && newest.sortKey > baseline + 0.001) {
                    notifier.notifyNewEpisode(fav, newest)
                    notified += fav.title
                }
            }
        }
        if (notified.size >= 2) notifier.postSummary(notified)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "episode_check"

        /** Schedules the daily-ish sweep; safe to call on every app start (KEEP policy). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<EpisodeNotifyWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}

/** Posts "new episode" notifications on a dedicated channel; silent no-op without permission. */
class EpisodeNotifier(private val context: Context) {

    private val res get() = localizedContext(context).resources

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** False without notification permission; otherwise makes sure the channel exists. */
    private fun ready(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    res.getString(R.string.system_channel_new_episodes_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = res.getString(R.string.system_channel_new_episodes_desc) },
            )
        }
        return true
    }

    suspend fun notifyNewEpisode(favorite: FavoriteEntity, episode: Episode) {
        if (!ready()) return

        val contentIntent = PendingIntent.getActivity(
            context,
            favorite.animeId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_ANIME_ID, favorite.animeId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Artwork is a bonus: when it can't load the notification stays text-only.
        val poster = loadBitmap(context, favorite.poster, 256)
        val cover = loadBitmap(context, favorite.cover.ifBlank { favorite.poster }, 1024)
        val text = res.getString(R.string.system_new_episode_text, episode.number)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(favorite.title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setGroup(GROUP_KEY)
            .apply {
                if (poster != null) setLargeIcon(poster)
                if (cover != null) {
                    setStyle(NotificationCompat.BigPictureStyle().bigPicture(cover).bigLargeIcon(null as Bitmap?).setSummaryText(text))
                }
            }
            .build()
        manager.notify(favorite.animeId.hashCode(), notification)
    }

    /** Collapses several "new episode" notifications under one "N new episodes" header. */
    fun postSummary(titles: List<String>) {
        if (!ready()) return
        val style = NotificationCompat.InboxStyle().also { inbox -> titles.forEach { inbox.addLine(it) } }
        val summary = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(groupSummaryTitle(res, titles.size))
            .setContentText(titles.joinToString(", "))
            .setStyle(style)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        manager.notify(SUMMARY_ID, summary)
    }

    companion object {
        const val CHANNEL_ID = "new_episodes"
        const val GROUP_KEY = "new_episodes"
        const val SUMMARY_ID = 0x5EED
    }
}

/** Title of the grouped "new episode" summary. */
fun groupSummaryTitle(res: Resources, count: Int): String =
    res.getQuantityString(R.plurals.system_new_episodes_summary, count, count)
