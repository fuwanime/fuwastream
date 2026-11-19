package com.fuwanime.widget

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.fuwanime.FuwaApp
import com.fuwanime.MainActivity
import com.fuwanime.R
import com.fuwanime.data.Anime
import com.fuwanime.ui.navigation.TAB_SCHEDULE
import com.fuwanime.ui.theme.DarkScheme
import com.fuwanime.ui.theme.LightScheme
import com.fuwanime.util.Day
import com.fuwanime.util.airTime
import com.fuwanime.util.localizedContext

private val SMALL = DpSize(110.dp, 110.dp)
private val WIDE = DpSize(250.dp, 110.dp)
private val TALL = DpSize(250.dp, 250.dp)

/** The app's own palette where Material You is unavailable (Android 11 and lower). */
private val AppColors = ColorProviders(LightScheme, DarkScheme)

private sealed interface ScheduleState {
    data object Loading : ScheduleState
    data object Error : ScheduleState
    data class Ready(val items: List<Anime>) : ScheduleState
}

/**
 * Home-screen widget: today's airing schedule, earliest release first (same day mapping and ordering
 * as ScheduleScreen). A tap opens the anime detail; the background opens the schedule tab.
 */
class ScheduleWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as FuwaApp).container
        val day = Day.today()
        provideContent {
            // Fetched inside the session so the loading card paints immediately; network first, the
            // cached day as the offline fallback (same source ScheduleScreen uses).
            val state by produceState<ScheduleState>(ScheduleState.Loading) {
                val repo = container.animeRepository
                val items = repo.schedule(day.apiValue).getOrNull()
                    ?: repo.schedule(day.apiValue, cacheOnly = true).getOrNull()
                value = if (items == null) {
                    ScheduleState.Error
                } else {
                    // Earliest release first; unknown times sink to the bottom.
                    ScheduleState.Ready(items.distinctBy { it.id }.sortedBy { airTime(it.nextAirTime).ifBlank { "99" } })
                }
            }
            GlanceTheme(colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) DynamicThemeColorProviders else AppColors) {
                WidgetBody(context, day, state)
            }
        }
    }
}

class ScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScheduleWidget()
}

private fun openApp(context: Context, vararg extras: Pair<String, String>) =
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        // Distinct data keeps each item's PendingIntent from being merged with another's.
        data = Uri.parse("fuwanime://widget/schedule/" + extras.joinToString("/") { it.second })
        extras.forEach { (key, value) -> putExtra(key, value) }
    }

@Composable
private fun WidgetBody(context: Context, day: Day, state: ScheduleState) {
    val size = LocalSize.current
    val res = localizedContext(context).resources
    Box(
        GlanceModifier.fillMaxSize().appWidgetBackground().background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(R.dimen.widget_radius)
            .clickable(actionStartActivity(openApp(context, MainActivity.EXTRA_OPEN_TAB to TAB_SCHEDULE))),
    ) {
        when (state) {
            ScheduleState.Loading -> LoadingState(res.getString(R.string.widget_schedule_loading))
            ScheduleState.Error -> EmptyState(context, res.getString(R.string.widget_schedule_error))
            is ScheduleState.Ready -> when {
                state.items.isEmpty() -> EmptyState(context, res.getString(R.string.widget_schedule_empty))
                size.width < WIDE.width -> Hero(context, day, state.items.first(), res)
                else -> {
                    val rows = if (size.height >= TALL.height) 4 else 2
                    Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
                        Header(day, res)
                        state.items.take(rows).forEachIndexed { i, anime ->
                            if (i > 0) Spacer(GlanceModifier.height(6.dp))
                            ScheduleRow(context, anime, res)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(day: Day, res: Resources) {
    Text(
        res.getString(R.string.widget_schedule_header, res.getString(day.full)),
        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
        modifier = GlanceModifier.padding(start = 4.dp, bottom = 6.dp),
    )
}

/** 2x2: the next airing title with its time chip. */
@Composable
private fun Hero(context: Context, day: Day, anime: Anime, res: Resources) {
    Column(
        GlanceModifier.fillMaxSize().padding(12.dp)
            .clickable(actionStartActivity(openApp(context, MainActivity.EXTRA_OPEN_ANIME_ID to anime.id))),
    ) {
        Text(
            res.getString(R.string.widget_schedule_header, res.getString(day.full)),
            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(GlanceModifier.height(6.dp))
        TimeChip(anime)
        Spacer(GlanceModifier.height(6.dp))
        Text(
            anime.title,
            maxLines = 3,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            anime.genres.take(2).joinToString(" • "),
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
        )
    }
}

/** A full-width tonal row: time chip, title and genres; the whole row opens the detail. */
@Composable
private fun ScheduleRow(context: Context, anime: Anime, res: Resources) {
    Row(
        GlanceModifier.fillMaxWidth().height(52.dp).cornerRadius(14.dp).background(GlanceTheme.colors.secondaryContainer)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clickable(actionStartActivity(openApp(context, MainActivity.EXTRA_OPEN_ANIME_ID to anime.id))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimeChip(anime)
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(
                anime.title,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                anime.genres.take(2).joinToString(" • "),
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
            )
        }
    }
}

@Composable
private fun TimeChip(anime: Anime) {
    Text(
        airTime(anime.nextAirTime).ifBlank { "–" },
        maxLines = 1,
        style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 12.sp, fontWeight = FontWeight.Bold),
        modifier = GlanceModifier.cornerRadius(10.dp).background(GlanceTheme.colors.primaryContainer).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun LoadingState(message: String) {
    Column(GlanceModifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        // Indeterminate overload (no progress): modifier, color, backgroundColor.
        LinearProgressIndicator(
            GlanceModifier.fillMaxWidth().height(4.dp).cornerRadius(2.dp),
            GlanceTheme.colors.primary,
            GlanceTheme.colors.surfaceVariant,
        )
        Spacer(GlanceModifier.height(8.dp))
        Text(message, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun EmptyState(context: Context, message: String) {
    val res = localizedContext(context).resources
    Column(GlanceModifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.height(8.dp))
        Text(
            res.getString(R.string.widget_schedule_open),
            style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.cornerRadius(20.dp).background(GlanceTheme.colors.primary).padding(horizontal = 16.dp, vertical = 10.dp)
                .clickable(actionStartActivity(openApp(context, MainActivity.EXTRA_OPEN_TAB to TAB_SCHEDULE))),
        )
    }
}
