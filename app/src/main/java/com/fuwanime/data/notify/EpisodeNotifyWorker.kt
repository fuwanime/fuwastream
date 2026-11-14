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
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fuwanime.FuwaApp
import com.fuwanime.MainActivity
import com.fuwanime.R
import com.fuwanime.data.Anime
import com.fuwanime.data.Episode
import com.fuwanime.data.local.FavoriteEntity
import com.fuwanime.data.local.TitleNotifyMode
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
        val prefs = container.settingsStore.settings.first()
        val favorites = container.libraryRepository.allFavorites().take(50)
        val notifier = EpisodeNotifier(applicationContext)
        val notified = mutableListOf<String>()
        for (fav in favorites) {
            runCatching {
                val preference = container.libraryRepository.titlePreferenceValue("ANIME", fav.animeId)
                val mode = preference?.notifyMode
                    ?.let { raw -> TitleNotifyMode.entries.firstOrNull { it.name == raw } }
                    ?: TitleNotifyMode.DEFAULT
                val autoDownload = preference?.autoDownload == true
                val notifyEnabled = when (mode) {
                    TitleNotifyMode.OFF -> false
                    TitleNotifyMode.ALL, TitleNotifyMode.FINAL_ONLY -> true
                    TitleNotifyMode.DEFAULT -> prefs.notifyNewEpisodes
                }
                if (!notifyEnabled && !autoDownload) return@runCatching
                // Skip finished shows before spending a request, except FINAL_ONLY which must see
                // the finished detail once to announce the last episode. Old favourites may carry a
                // blank status, and an upcoming title keeps that status in the library after it starts
                // airing, so resolve both from the detail endpoint instead of dropping them.
                val ongoing = when {
                    fav.status.equals("ONGOING", true) -> true
                    fav.status.isBlank() ||
                        fav.status.equals("UPCOMING", true) ||
                        fav.status.equals("NOT_YET_AIRED", true) ->
                        container.animeRepository.detail(fav.animeId).getOrNull()?.isOngoing == true
                    else -> false
                }
                if (!ongoing && mode != TitleNotifyMode.FINAL_ONLY && !autoDownload) return@runCatching

                // Always hit the network here: the repository caches episode pages for the UI, and a
                // long-lived process would otherwise keep comparing against the same stale episode.
                val newest = container.animeRepository
                    .episodePage(fav.animeId, 0, forceRefresh = true).getOrNull()
                    ?.maxByOrNull { it.sortKey } ?: return@runCatching
                if (newest.sortKey == Double.MAX_VALUE) return@runCatching

                val finalOnly = mode == TitleNotifyMode.FINAL_ONLY
                if (!finalOnly && !ongoing) return@runCatching
                val finished = if (finalOnly) {
                    container.animeRepository.detail(fav.animeId).getOrNull()?.isOngoing == false
                } else true

                val baseline = container.settingsStore.notifyBaseline(fav.animeId)
                // FINAL_ONLY keeps the notification baseline open until the show ends, so the last
                // episode is still announced once. Auto-download keeps its own baseline, otherwise the
                // held notification baseline would download the same episode on every run.
                container.settingsStore.setNotifyBaseline(fav.animeId, if (finalOnly && !finished) 0.0 else newest.sortKey)
                val downloadBaseline = container.settingsStore.episodeDownloadBaseline(fav.animeId)
                if (autoDownload) container.settingsStore.setEpisodeDownloadBaseline(fav.animeId, newest.sortKey)
                val shouldNotify = baseline != null && newest.sortKey > baseline + 0.001
                val shouldDownload = autoDownload && downloadBaseline != null && newest.sortKey > downloadBaseline + 0.001
                if (shouldNotify && notifyEnabled && finished) {
                    notifier.notifyNewEpisode(fav, newest)
                    notified += fav.title
                }
                if (shouldDownload) {
                    val anime = Anime(
                        id = fav.animeId, title = fav.title, poster = fav.poster, cover = fav.cover,
                        type = fav.type, status = fav.status, year = fav.year,
                        genres = fav.genres.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                    )
                    container.downloadRepository
                        .download(anime, newest, prefs.downloadQuality, prefs.downloadWifiOnly)
                }
            }
        }
        if (notified.size >= 2) notifier.postSummary(notified)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "episode_check"

        /** Schedules a frequent sweep (every 15 min, WorkManager minimum) for near-real-time favorite checks. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<EpisodeNotifyWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        /** Runs a check immediately from the notification settings screen. */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<EpisodeNotifyWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME}_now",
                ExistingWorkPolicy.KEEP,
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
    private fun ready(): Boolean = ensureChannel(
        context, manager, CHANNEL_ID,
        res.getString(R.string.system_channel_new_episodes_name),
        res.getString(R.string.system_channel_new_episodes_desc),
    )

    suspend fun notifyNewEpisode(favorite: FavoriteEntity, episode: Episode) = notifyAnime(
        animeId = favorite.animeId,
        title = favorite.title,
        poster = favorite.poster,
        cover = favorite.cover,
        text = res.getString(R.string.system_new_episode_text, episode.number),
        episodeId = episode.id,
        episodeNumber = episode.number,
    )

    /** Posts a local test notification; false when permission or the channel blocks it. */
    fun notifyTest(): Boolean {
        if (!ready()) return false
        val contentIntent = PendingIntent.getActivity(
            context,
            TEST_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_fuwa)
            .setContentTitle(res.getString(R.string.app_name))
            .setContentText(res.getString(R.string.mine_notify_test_body))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        manager.notify(TEST_ID, notification)
        return true
    }

    /** Posts a "new episode" notification for any anime (favourites and the global release feed). */
    suspend fun notifyAnime(
        animeId: String,
        title: String,
        poster: String,
        cover: String,
        text: String,
        episodeId: String? = null,
        episodeNumber: String = "",
    ) {
        if (!ready()) return

        val contentIntent = PendingIntent.getActivity(
            context,
            animeId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_ANIME_ID, animeId)
                episodeId?.let { putExtra(MainActivity.EXTRA_OPEN_EPISODE_ID, it) }
                putExtra(MainActivity.EXTRA_OPEN_EPISODE_NUMBER, episodeNumber)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Artwork is a bonus: when it can't load the notification stays text-only.
        val posterBitmap = loadBitmap(context, poster, 256)
        val coverBitmap = loadBitmap(context, cover.ifBlank { poster }, 1024)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_fuwa)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setGroup(GROUP_KEY)
            .addAction(0, res.getString(R.string.system_action_watch), contentIntent)
            .apply {
                if (posterBitmap != null) setLargeIcon(posterBitmap)
                if (coverBitmap != null) {
                    setStyle(NotificationCompat.BigPictureStyle().bigPicture(coverBitmap).bigLargeIcon(null as Bitmap?).setSummaryText(text))
                }
            }
            .build()
        manager.notify(animeId.hashCode(), notification)
    }

    /** Collapses several "new episode" notifications under one "N new episodes" header. */
    fun postSummary(titles: List<String>) {
        if (!ready()) return
        val style = NotificationCompat.InboxStyle().also { inbox -> titles.forEach { inbox.addLine(it) } }
        val summary = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_fuwa)
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
        const val TEST_ID = 0x7E57
    }
}

/** Title of the grouped "new episode" summary. */
fun groupSummaryTitle(res: Resources, count: Int): String =
    res.getQuantityString(R.plurals.system_new_episodes_summary, count, count)

/** Title of the grouped "new chapter" summary. */
fun chapterSummaryTitle(res: Resources, count: Int): String =
    res.getQuantityString(R.plurals.system_new_chapters_summary, count, count)

/**
 * False without the notification permission; otherwise makes sure a channel with [channelId] exists.
 * Shared by every notifier so the permission check and channel setup live in one place.
 */
fun ensureChannel(
    context: Context,
    manager: NotificationManager,
    channelId: String,
    name: String,
    description: String,
): Boolean {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) return false
    if (Build.VERSION.SDK_INT >= 26) {
        // createNotificationChannel never overrides a user's choice, so an existing channel
        // with importance NONE means the user (or an OEM) disabled it: posting would be a no-op.
        if (manager.getNotificationChannel(channelId)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        manager.createNotificationChannel(
            NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_DEFAULT).apply { this.description = description },
        )
    }
    return true
}
