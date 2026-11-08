package com.fuwanime.ui.navigation

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.fuwanime.MainActivity
import com.fuwanime.R
import com.fuwanime.data.local.WatchHistoryEntity
import com.fuwanime.util.localizedContext

/**
 * Launcher long-press shortcuts. All are dynamic: a static shortcuts.xml hardcodes the target package,
 * which differs between the release and ".debug" builds.
 */
object Shortcuts {
    private const val CONTINUE = "continue"

    private fun intent(context: Context, extra: String, value: String) =
        Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra(extra, value)

    private fun shortcut(context: Context, id: String, label: String, longLabel: String, intent: Intent, rank: Int) =
        ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(label)
            .setLongLabel(longLabel)
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(intent)
            .setRank(rank)
            .build()

    /** "Cari" and "Jadwal"; safe to call on every start. */
    fun publishFixed(context: Context) {
        runCatching {
            val res = localizedContext(context).resources
            ShortcutManagerCompat.pushDynamicShortcut(
                context,
                shortcut(context, "search", res.getString(R.string.system_shortcut_search_short), res.getString(R.string.system_shortcut_search_long), intent(context, MainActivity.EXTRA_OPEN_TAB, TAB_EXPLORE_SEARCH), 1),
            )
            ShortcutManagerCompat.pushDynamicShortcut(
                context,
                shortcut(context, "schedule", res.getString(R.string.system_shortcut_schedule_short), res.getString(R.string.system_shortcut_schedule_long), intent(context, MainActivity.EXTRA_OPEN_TAB, TAB_SCHEDULE), 2),
            )
        }
    }

    /** "Lanjutkan <judul>" for the latest history entry, or removed when history is empty. */
    fun pushContinue(context: Context, entry: WatchHistoryEntity?) {
        runCatching {
            if (entry == null) {
                ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(CONTINUE))
                return
            }
            val res = localizedContext(context).resources
            ShortcutManagerCompat.pushDynamicShortcut(
                context,
                shortcut(
                    context, CONTINUE, entry.animeTitle.take(25), res.getString(R.string.system_shortcut_continue_long, entry.animeTitle),
                    intent(context, MainActivity.EXTRA_RESUME_EPISODE_ID, entry.episodeId), 0,
                ),
            )
        }
    }
}
