package com.fuwanime.data.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
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
                }
            }
        }
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

    fun notifyNewEpisode(favorite: FavoriteEntity, episode: Episode) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Episode baru",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "Notifikasi episode baru untuk anime favorit" },
            )
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            favorite.animeId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_ANIME_ID, favorite.animeId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(favorite.title)
            .setContentText("Episode ${episode.number} sudah tayang!")
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        manager.notify(favorite.animeId.hashCode(), notification)
    }

    companion object {
        const val CHANNEL_ID = "new_episodes"
    }
}

/** Title of the grouped "new episode" summary. */
fun groupSummaryTitle(count: Int): String = "stub"
