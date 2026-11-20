package com.fuwanime.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.fuwanime.FuwaApp
import com.fuwanime.MainActivity
import com.fuwanime.R
import com.fuwanime.ui.navigation.TAB_EXPLORE_SEARCH
import com.fuwanime.util.loadBitmap
import com.fuwanime.util.localizedContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** Poster bitmaps are decoded at this height; whole-image fit keeps the RemoteViews bitmap budget small. */
private const val POSTER_PX = 240

/** Home-screen widget: the last anime and manga touched, newest first; a tap resumes the episode or chapter. */
class ContinueWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(widgetSmall, widgetWide, widgetTall))
    override val stateDefinition: GlanceStateDefinition<*> = WidgetStateDefinition("continue")

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as FuwaApp).container
        val entriesFlow = combine(container.libraryRepository.continueWatching(4), container.mangaLibrary.continueReading) { w, r ->
            mergeContinue(w, r, limit = 4)
        }
        val initial = entriesFlow.first()
        val initialPosters = loadPosters(context, initial)
        provideContent {
            // Collected inside the session: updates that arrive while it is alive only recompose, they don't re-run
            // provideGlance, so a list captured outside would go stale.
            val entries by entriesFlow.collectAsState(initial)
            val posters by produceState(if (entries == initial) initialPosters else emptyMap(), entries) {
                value = loadPosters(context, entries)
            }
            WidgetTheme {
                WidgetBody(context, entries, posters)
            }
        }
    }

    private suspend fun loadPosters(context: Context, entries: List<ContinueEntry>): Map<String, Bitmap?> =
        entries.associate { it.key to loadBitmap(context, it.imageUrl, POSTER_PX)?.let(::roundPosterCorners) }
}

class ContinueWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ContinueWidget()
}

private fun openApp(context: Context, vararg extras: Pair<String, String>) =
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        // Distinct data keeps each item's PendingIntent from being merged with another's.
        data = Uri.parse("fuwanime://widget/continue/" + extras.joinToString("/") { it.second })
        extras.forEach { (key, value) -> putExtra(key, value) }
    }

private fun ContinueEntry.target(context: Context) = when (this) {
    is ContinueEntry.Anime -> openApp(context, MainActivity.EXTRA_RESUME_EPISODE_ID to row.episodeId)
    is ContinueEntry.Manga -> openApp(context, MainActivity.EXTRA_OPEN_MANGA_ID to row.mangaId, MainActivity.EXTRA_OPEN_CHAPTER_ID to row.chapterId)
}

@Composable
private fun WidgetBody(context: Context, entries: List<ContinueEntry>, posters: Map<String, Bitmap?>) {
    val size = LocalSize.current
    val res = localizedContext(context).resources
    // Tonal M3 base like the app's surface; the whole widget opens the app, cards and "Explore"
    // override it with their own targets.
    Box(
        GlanceModifier.fillMaxSize().appWidgetBackground().widgetSurface()
            .clickable(actionStartActivity(openApp(context))),
    ) {
        when {
            entries.isEmpty() -> WidgetEmptyState(
                res.getString(R.string.system_widget_empty),
                res.getString(R.string.system_widget_explore),
                actionStartActivity(openApp(context, MainActivity.EXTRA_OPEN_TAB to TAB_EXPLORE_SEARCH)),
            )
            size.width < widgetWide.width -> Hero(context, entries.first(), posters[entries.first().key], res, featured = false)
            size.height >= widgetTall.height -> ContinueList(context, entries.take(4), posters, res)
            else -> Hero(context, entries.first(), posters[entries.first().key], res, featured = true)
        }
    }
}

private fun subtitle(entry: ContinueEntry, res: android.content.res.Resources) = when (entry) {
    is ContinueEntry.Anime -> res.getString(R.string.system_widget_episode, entry.row.episodeNumber)
    is ContinueEntry.Manga -> res.getString(R.string.system_widget_chapter, entry.row.chapterLabel, entry.row.page + 1, entry.row.pageCount)
}

/**
 * One entry, poster shown whole beside the text so nothing of the art is cropped. SMALL puts it on a
 * tonal card under a "Continue" header; WIDE lets a primaryContainer card fill the widget so the art
 * stays large (a second fixed-height row never fit the 110dp height and was clipped away).
 */
@Composable
private fun Hero(context: Context, entry: ContinueEntry, poster: Bitmap?, res: android.content.res.Resources, featured: Boolean) {
    Column(
        GlanceModifier.fillMaxSize().padding(12.dp)
            .clickable(actionStartActivity(entry.target(context))),
    ) {
        if (!featured) WidgetHeader(res.getString(R.string.system_widget_continue), fontSize = 12.sp)
        Row(
            GlanceModifier.fillMaxWidth().defaultWeight()
                .then(if (featured) GlanceModifier.widgetPrimaryCard() else GlanceModifier.widgetCard())
                .padding(if (featured) 10.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WidgetPoster(
                entry.title, poster,
                GlanceModifier.size(if (featured) 44.dp else 32.dp, if (featured) 66.dp else 48.dp),
            )
            Spacer(GlanceModifier.width(if (featured) 12.dp else 8.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    entry.title,
                    maxLines = if (featured) 2 else 1,
                    style = TextStyle(
                        color = if (featured) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurface,
                        fontSize = if (featured) 14.sp else 12.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    subtitle(entry, res),
                    maxLines = 1,
                    style = TextStyle(
                        color = if (featured) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurfaceVariant,
                        fontSize = if (featured) 11.sp else 10.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(GlanceModifier.height(if (featured) 6.dp else 4.dp))
                WidgetProgress(entry.progress, height = if (featured) 6.dp else 4.dp, track = GlanceTheme.colors.surface)
            }
        }
    }
}

/** TALL: the four most recent entries as equal-height tonal cards under a "Continue" header. */
@Composable
private fun ContinueList(context: Context, entries: List<ContinueEntry>, posters: Map<String, Bitmap?>, res: android.content.res.Resources) {
    Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
        WidgetHeader(res.getString(R.string.system_widget_continue))
        entries.forEachIndexed { i, entry ->
            if (i > 0) Spacer(GlanceModifier.height(8.dp))
            EntryRow(context, entry, posters[entry.key], subtitle(entry, res))
        }
    }
}

/** A full-width tonal card; rows share the height by weight so every card stays inside the widget. */
@Composable
private fun ColumnScope.EntryRow(context: Context, entry: ContinueEntry, poster: Bitmap?, subtitle: String) {
    Row(
        GlanceModifier.fillMaxWidth().defaultWeight().widgetCard()
            .padding(6.dp).clickable(actionStartActivity(entry.target(context))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetPoster(entry.title, poster, GlanceModifier.size(22.dp, 33.dp))
        Spacer(GlanceModifier.width(8.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(
                entry.title,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                subtitle,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Medium),
            )
            Spacer(GlanceModifier.height(2.dp))
            WidgetProgress(entry.progress, height = 4.dp, track = GlanceTheme.colors.surface)
        }
    }
}
