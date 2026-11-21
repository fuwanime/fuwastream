package com.fuwanime.widget

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import androidx.compose.runtime.Composable
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
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
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
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.fuwanime.FuwaApp
import com.fuwanime.MainActivity
import com.fuwanime.R
import com.fuwanime.data.Anime
import com.fuwanime.ui.navigation.TAB_SCHEDULE
import com.fuwanime.util.Day
import com.fuwanime.util.airTime
import com.fuwanime.util.localizedContext

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

    override val sizeMode = SizeMode.Responsive(setOf(widgetSmall, widgetWide, widgetTall))
    override val stateDefinition: GlanceStateDefinition<*> = WidgetStateDefinition("schedule")

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
            WidgetTheme {
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
        GlanceModifier.fillMaxSize().appWidgetBackground().widgetSurface()
            .clickable(actionStartActivity(openApp(context, MainActivity.EXTRA_OPEN_TAB to TAB_SCHEDULE))),
    ) {
        when (state) {
            ScheduleState.Loading -> LoadingState(res.getString(R.string.widget_schedule_loading))
            ScheduleState.Error -> WidgetEmptyState(
                res.getString(R.string.widget_schedule_error),
                res.getString(R.string.widget_schedule_open),
                actionStartActivity(openApp(context, MainActivity.EXTRA_OPEN_TAB to TAB_SCHEDULE)),
            )
            is ScheduleState.Ready -> when {
                state.items.isEmpty() -> WidgetEmptyState(
                    res.getString(R.string.widget_schedule_empty),
                    res.getString(R.string.widget_schedule_open),
                    actionStartActivity(openApp(context, MainActivity.EXTRA_OPEN_TAB to TAB_SCHEDULE)),
                )
                size.width < widgetWide.width -> Hero(context, day, state.items.first(), res)
                else -> {
                    val compact = size.height < widgetTall.height
                    Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
                        WidgetHeader(res.getString(R.string.widget_schedule_header, res.getString(day.full)))
                        Spacer(GlanceModifier.height(6.dp))
                        // Scrollable: the whole day's lineup, not just the first few rows.
                        LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                            items(state.items, itemId = { it.id.hashCode().toLong() }) { anime ->
                                ScheduleRow(context, anime, res, compact = compact)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 2x2: the next airing title with its time pill. */
@Composable
private fun Hero(context: Context, day: Day, anime: Anime, res: Resources) {
    Column(
        GlanceModifier.fillMaxSize().padding(12.dp)
            .clickable(actionStartActivity(openApp(context, MainActivity.EXTRA_OPEN_ANIME_ID to anime.id))),
    ) {
        WidgetHeader(res.getString(R.string.widget_schedule_header, res.getString(day.full)), fontSize = 12.sp)
        WidgetPill(airTime(anime.nextAirTime).ifBlank { "–" }, primary = true)
        Spacer(GlanceModifier.height(6.dp))
        Text(
            anime.title,
            maxLines = 2,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold),
        )
    }
}

/**
 * A full-width tonal row: time pill and title (plus genres when tall enough); the whole row opens the
 * detail. Rows share the height by weight so they always fit, even at the 110dp minimum.
 */
@Composable
private fun ScheduleRow(context: Context, anime: Anime, res: Resources, compact: Boolean) {
    Row(
        GlanceModifier.fillMaxWidth().padding(bottom = 6.dp).widgetSecondaryCard()
            .padding(horizontal = 8.dp, vertical = if (compact) 6.dp else 8.dp)
            .clickable(actionStartActivity(openApp(context, MainActivity.EXTRA_OPEN_ANIME_ID to anime.id))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetPill(airTime(anime.nextAirTime).ifBlank { "–" }, primary = true)
        Spacer(GlanceModifier.width(8.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(
                anime.title,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = if (compact) 12.sp else 13.sp, fontWeight = FontWeight.Bold),
            )
            if (!compact) {
                Text(
                    anime.genres.take(2).joinToString(" • "),
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                )
            }
        }
    }
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
