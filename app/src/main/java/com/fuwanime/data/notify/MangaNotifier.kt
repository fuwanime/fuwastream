package com.fuwanime.data.notify

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.fuwanime.MainActivity
import com.fuwanime.R
import com.fuwanime.util.localizedContext

/** Posts "new chapter" notifications for ongoing manga on a dedicated channel; silent no-op without permission. */
class MangaNotifier(private val context: Context) {

    private val res get() = localizedContext(context).resources

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun ready(): Boolean = ensureChannel(
        context, manager, CHANNEL_ID,
        res.getString(R.string.system_channel_new_chapters_name),
        res.getString(R.string.system_channel_new_chapters_desc),
    )

    /** [chapterLabel] is the display form ("12.5"); the tap opens that chapter in the reader. */
    fun notifyNewChapter(mangaId: String, title: String, chapterId: String, chapterLabel: String) {
        if (!ready()) return

        val contentIntent = PendingIntent.getActivity(
            context,
            mangaId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_OPEN_MANGA_ID, mangaId)
                putExtra(MainActivity.EXTRA_OPEN_CHAPTER_ID, chapterId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val text = res.getString(R.string.system_new_chapter_text, chapterLabel)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_fuwa)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setGroup(GROUP_KEY)
            .addAction(0, res.getString(R.string.system_action_read), contentIntent)
            .build()
        manager.notify(mangaId.hashCode(), notification)
    }

    /** Collapses several "new chapter" notifications under one "N new chapters" header. */
    fun postSummary(titles: List<String>) {
        if (!ready()) return
        val style = NotificationCompat.InboxStyle().also { inbox -> titles.forEach { inbox.addLine(it) } }
        val summary = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_fuwa)
            .setContentTitle(chapterSummaryTitle(res, titles.size))
            .setContentText(titles.joinToString(", "))
            .setStyle(style)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        manager.notify(SUMMARY_ID, summary)
    }

    companion object {
        const val CHANNEL_ID = "new_chapters"
        const val GROUP_KEY = "new_chapters"
        const val SUMMARY_ID = 0x5EED2
    }
}
