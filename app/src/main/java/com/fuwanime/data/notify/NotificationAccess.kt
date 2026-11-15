package com.fuwanime.data.notify

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Why Android may drop FuwaStream notifications, ordered from the setting the user should fix first. */
enum class NotificationAccess {
    GRANTED,
    PERMISSION_MISSING,
    APP_BLOCKED,
    CHANNEL_BLOCKED,
}

/** Checks the runtime permission, the app-level switch and both notification channels. */
fun notificationAccess(context: Context): NotificationAccess {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return NotificationAccess.PERMISSION_MISSING
    }
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
        return NotificationAccess.APP_BLOCKED
    }
    if (Build.VERSION.SDK_INT >= 26) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val blocked = listOf(
            EpisodeNotifier.CHANNEL_ID,
            MangaNotifier.CHANNEL_ID,
            AppUpdateNotifier.CHANNEL_ID,
        )
            .mapNotNull { manager.getNotificationChannel(it) }
            .any { it.importance == NotificationManager.IMPORTANCE_NONE }
        if (blocked) return NotificationAccess.CHANNEL_BLOCKED
    }
    return NotificationAccess.GRANTED
}

/** Opens the system notification settings for FuwaStream; falls back to the app details page. */
fun openNotificationSettings(context: Context) {
    val appDetails = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    )
    val notificationSettings = if (Build.VERSION.SDK_INT >= 26) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        appDetails
    }
    val opened = runCatching {
        context.startActivity(notificationSettings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
    if (!opened) {
        runCatching { context.startActivity(appDetails.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
