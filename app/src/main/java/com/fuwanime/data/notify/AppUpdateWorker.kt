package com.fuwanime.data.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import com.fuwanime.BuildConfig
import com.fuwanime.FuwaApp
import com.fuwanime.MainActivity
import com.fuwanime.R
import com.fuwanime.data.isNewerRelease
import com.fuwanime.data.remote.GitHubApi
import com.fuwanime.util.localizedContext
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Checks GitHub Releases for a newer app version and posts a system notification.
 * Runs periodically in the background and can be triggered when the app opens, so
 * users get an "announcement" without a real-time push server.
 *
 * The first time a tag is seen it is notified once (if newer than the installed
 * build), unless the user already dismissed that update banner.
 */
class AppUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as FuwaApp).container
        val settings = container.settingsStore.settings.first()
        if (!settings.notifyAppUpdates) return Result.success()

        val release = GitHubApi.latest() ?: return Result.success()
        val currentTag = currentTag()
        if (!isNewerRelease(release.tagName, currentTag)) return Result.success()

        val dismissed = container.settingsStore.dismissedUpdateTag()
        val notified = container.settingsStore.updateNotifiedTag()
        if (release.tagName == dismissed || release.tagName == notified) return Result.success()

        val title = release.name?.takeIf { it.isNotBlank() } ?: "FuwaStream ${release.tagName.removePrefix("v")}"
        val posted = AppUpdateNotifier(applicationContext).notifyUpdate(
            tag = release.tagName,
            title = title,
            pageUrl = release.htmlUrl,
            apkUrl = release.assets.lastOrNull { it.name.endsWith(".apk") }?.url ?: release.assets.firstOrNull()?.url,
        )
        if (posted) container.settingsStore.setUpdateNotifiedTag(release.tagName)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "app_update_check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AppUpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<AppUpdateWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME}_now",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        /** Release tag produced by this install, e.g. "build-8" or "v2.1.7". */
        fun currentTag(): String {
            val versionName = BuildConfig.VERSION_NAME
            return if (versionName.contains("build.")) {
                "build-" + versionName.substringAfterLast('.')
            } else {
                "v$versionName"
            }
        }
    }
}

/** Posts "app update available" notifications; silent no-op without permission/channel. */
class AppUpdateNotifier(private val context: Context) {

    private val res get() = localizedContext(context).resources
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun ready(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    res.getString(R.string.system_channel_app_updates_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = res.getString(R.string.system_channel_app_updates_desc) },
            )
        }
        return true
    }

    fun notifyUpdate(tag: String, title: String, pageUrl: String, apkUrl: String?): Boolean {
        if (!ready()) return false
        val openUrl = pageUrl.ifBlank { apkUrl.orEmpty() }
        val contentIntent = if (openUrl.isNotBlank()) {
            PendingIntent.getActivity(
                context,
                tag.hashCode(),
                Intent(Intent.ACTION_VIEW, Uri.parse(openUrl)),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            PendingIntent.getActivity(
                context,
                tag.hashCode(),
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(res.getString(R.string.system_app_update_title, tag))
            .setContentText(title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(title))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        return true
    }

    companion object {
        const val CHANNEL_ID = "app_updates"
        const val NOTIFICATION_ID = 0xA11D
    }
}
