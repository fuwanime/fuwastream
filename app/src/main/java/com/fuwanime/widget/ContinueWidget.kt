package com.fuwanime.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.fuwanime.FuwaApp
import com.fuwanime.MainActivity
import com.fuwanime.data.local.WatchHistoryEntity
import com.fuwanime.ui.navigation.TAB_EXPLORE_SEARCH
import com.fuwanime.util.loadBitmap
import kotlinx.coroutines.flow.first

private val Surface = Color(0xFF16141C)
private val Outline = Color(0xFF2A2633)
private val Primary = Color(0xFFD4BBFF)
private val TextColor = Color(0xFFE8E0EE)
private val Muted = Color(0xFFA39DAE)

private val SMALL = DpSize(110.dp, 110.dp)
private val MEDIUM = DpSize(250.dp, 180.dp)
private val LARGE = DpSize(250.dp, 250.dp)

/** Home-screen widget: the last 1–3 anime watched, each tap resumes its episode in the player. */
class ContinueWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val library = (context.applicationContext as FuwaApp).container.libraryRepository
        val history = library.continueWatching(3)
        val initial = history.first()
        val initialPosters = loadPosters(context, initial)
        provideContent {
            // Collected inside the session: updates that arrive while it is alive only recompose, they don't re-run
            // provideGlance, so a list captured outside would go stale.
            val entries by history.collectAsState(initial)
            val posters by produceState(if (entries == initial) initialPosters else emptyMap(), entries) {
                value = loadPosters(context, entries)
            }
            WidgetBody(context, entries, posters)
        }
    }

    private suspend fun loadPosters(context: Context, entries: List<WatchHistoryEntity>): Map<String, Bitmap?> =
        entries.associate { it.episodeId to loadBitmap(context, it.animePoster.ifBlank { it.animeCover }, 160) }
}

class ContinueWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ContinueWidget()
}

private fun openApp(context: Context, extra: Pair<String, String>? = null) =
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        // Distinct data keeps each item's PendingIntent from being merged with another's.
        data = Uri.parse("fuwanime://widget/${extra?.second.orEmpty()}")
        extra?.let { (key, value) -> putExtra(key, value) }
    }

@Composable
private fun WidgetBody(context: Context, entries: List<WatchHistoryEntity>, posters: Map<String, Bitmap?>) {
    val height = LocalSize.current.height
    val rows = when {
        height >= LARGE.height -> 3
        height >= MEDIUM.height -> 2
        else -> 1
    }
    // The whole widget opens the app; item rows and "Jelajahi" override it with their own targets.
    Column(
        GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(Surface).padding(12.dp)
            .clickable(actionStartActivity(openApp(context))),
    ) {
        Text(
            "Lanjutkan",
            style = TextStyle(color = ColorProvider(Primary), fontSize = 12.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(GlanceModifier.height(8.dp))
        if (entries.isEmpty()) {
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Text("Belum ada tontonan", style = TextStyle(color = ColorProvider(TextColor), fontSize = 13.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    "Jelajahi",
                    style = TextStyle(color = ColorProvider(Primary), fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.clickable(
                        actionStartActivity(openApp(context, MainActivity.EXTRA_OPEN_TAB to TAB_EXPLORE_SEARCH)),
                    ),
                )
            }
            return@Column
        }
        entries.take(rows).forEachIndexed { i, entry ->
            if (i > 0) Spacer(GlanceModifier.height(8.dp))
            EntryRow(context, entry, posters[entry.episodeId])
        }
    }
}

@Composable
private fun EntryRow(context: Context, entry: WatchHistoryEntity, poster: Bitmap?) {
    Row(
        GlanceModifier.fillMaxWidth().clickable(
            actionStartActivity(openApp(context, MainActivity.EXTRA_RESUME_EPISODE_ID to entry.episodeId)),
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(GlanceModifier.size(40.dp, 60.dp).cornerRadius(8.dp).background(Outline)) {
            if (poster != null) {
                Image(ImageProvider(poster), entry.animeTitle, GlanceModifier.fillMaxSize().cornerRadius(8.dp), contentScale = ContentScale.Crop)
            }
        }
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(
                entry.animeTitle,
                maxLines = 2,
                style = TextStyle(color = ColorProvider(TextColor), fontSize = 13.sp, fontWeight = FontWeight.Bold),
            )
            Text("Ep ${entry.episodeNumber}", style = TextStyle(color = ColorProvider(Muted), fontSize = 11.sp))
            Spacer(GlanceModifier.height(4.dp))
            LinearProgressIndicator(
                progress = entry.progress,
                modifier = GlanceModifier.fillMaxWidth().height(3.dp),
                color = ColorProvider(Primary),
                backgroundColor = ColorProvider(Outline),
            )
        }
    }
}
