package com.fuwanime.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.DynamicThemeColorProviders
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.fuwanime.FuwaApp
import com.fuwanime.MainActivity
import com.fuwanime.R
import com.fuwanime.ui.navigation.TAB_EXPLORE_SEARCH
import com.fuwanime.ui.theme.DarkScheme
import com.fuwanime.ui.theme.LightScheme
import com.fuwanime.util.loadBitmap
import com.fuwanime.util.localizedContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

private val SMALL = DpSize(110.dp, 110.dp)
private val WIDE = DpSize(250.dp, 110.dp)
private val TALL = DpSize(250.dp, 250.dp)

/** Poster bitmaps are decoded at this height; whole-image fit keeps the RemoteViews bitmap budget small. */
private const val POSTER_PX = 240

/** The app's own palette where Material You is unavailable (Android 11 and lower). */
private val AppColors = ColorProviders(LightScheme, DarkScheme)

/** Home-screen widget: the last anime and manga touched, newest first; a tap resumes the episode or chapter. */
class ContinueWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, TALL))

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
            GlanceTheme(colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) DynamicThemeColorProviders else AppColors) {
                WidgetBody(context, entries, posters)
            }
        }
    }

    private suspend fun loadPosters(context: Context, entries: List<ContinueEntry>): Map<String, Bitmap?> =
        entries.associate { it.key to loadBitmap(context, it.imageUrl, POSTER_PX) }
}

class ContinueWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ContinueWidget()
}

private fun openApp(context: Context, vararg extras: Pair<String, String>) =
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        // Distinct data keeps each item's PendingIntent from being merged with another's.
        data = Uri.parse("fuwanime://widget/" + extras.joinToString("/") { it.second })
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
    // The whole widget opens the app; rows and "Explore" override it with their own targets.
    Box(
        GlanceModifier.fillMaxSize().appWidgetBackground().background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(R.dimen.widget_radius)
            .clickable(actionStartActivity(openApp(context))),
    ) {
        when {
            entries.isEmpty() -> EmptyState(context, res.getString(R.string.system_widget_empty), res.getString(R.string.system_widget_explore))
            size.width < WIDE.width -> Hero(context, entries.first(), posters[entries.first().key], res)
            else -> {
                val rows = if (size.height >= TALL.height) 4 else 2
                Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
                    Text(
                        res.getString(R.string.system_widget_continue),
                        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.padding(start = 4.dp, bottom = 6.dp),
                    )
                    entries.take(rows).forEachIndexed { i, entry ->
                        if (i > 0) Spacer(GlanceModifier.height(6.dp))
                        EntryRow(context, entry, posters[entry.key], subtitle(entry, res))
                    }
                }
            }
        }
    }
}

private fun subtitle(entry: ContinueEntry, res: android.content.res.Resources) = when (entry) {
    is ContinueEntry.Anime -> res.getString(R.string.system_widget_episode, entry.row.episodeNumber)
    is ContinueEntry.Manga -> res.getString(R.string.system_widget_chapter, entry.row.chapterLabel, entry.row.page + 1, entry.row.pageCount)
}

/** 2x2: one title, its poster shown whole beside the text so nothing of the art is cropped. */
@Composable
private fun Hero(context: Context, entry: ContinueEntry, poster: Bitmap?, res: android.content.res.Resources) {
    Column(
        GlanceModifier.fillMaxSize().padding(12.dp).clickable(actionStartActivity(entry.target(context))),
    ) {
        Text(
            res.getString(R.string.system_widget_continue),
            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(GlanceModifier.height(6.dp))
        Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.Top) {
            Poster(entry, poster, GlanceModifier.size(48.dp, 72.dp))
            Spacer(GlanceModifier.width(8.dp))
            Text(
                entry.title,
                maxLines = 3,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight(),
            )
        }
        Text(subtitle(entry, res), maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp))
        Spacer(GlanceModifier.height(4.dp))
        Progress(entry.progress)
    }
}

/** A full-width row, at least 72dp tall, so each title is an easy tap target. */
@Composable
private fun EntryRow(context: Context, entry: ContinueEntry, poster: Bitmap?, subtitle: String) {
    Row(
        GlanceModifier.fillMaxWidth().height(72.dp).cornerRadius(12.dp).background(GlanceTheme.colors.secondaryContainer)
            .padding(6.dp).clickable(actionStartActivity(entry.target(context))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(entry, poster, GlanceModifier.size(40.dp, 60.dp))
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(
                entry.title,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            )
            Text(subtitle, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp))
            Spacer(GlanceModifier.height(4.dp))
            Progress(entry.progress)
        }
    }
}

@Composable
private fun Poster(entry: ContinueEntry, poster: Bitmap?, modifier: GlanceModifier) {
    Box(modifier.cornerRadius(8.dp).background(GlanceTheme.colors.surfaceVariant), contentAlignment = Alignment.Center) {
        if (poster != null) {
            Image(ImageProvider(poster), entry.title, GlanceModifier.fillMaxSize().cornerRadius(8.dp), contentScale = ContentScale.Fit)
        }
    }
}

@Composable
private fun Progress(value: Float) = LinearProgressIndicator(
    progress = value,
    modifier = GlanceModifier.fillMaxWidth().height(4.dp).cornerRadius(2.dp),
    color = GlanceTheme.colors.primary,
    backgroundColor = GlanceTheme.colors.surfaceVariant,
)

@Composable
private fun EmptyState(context: Context, message: String, action: String) {
    Column(GlanceModifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.height(8.dp))
        Text(
            action,
            style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.cornerRadius(20.dp).background(GlanceTheme.colors.primary).padding(horizontal = 16.dp, vertical = 10.dp)
                .clickable(actionStartActivity(openApp(context, MainActivity.EXTRA_OPEN_TAB to TAB_EXPLORE_SEARCH))),
        )
    }
}
