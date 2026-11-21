package com.fuwanime.widget

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
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
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.fuwanime.FuwaApp
import com.fuwanime.MainActivity
import com.fuwanime.R
import com.fuwanime.ui.navigation.TAB_EXPLORE_SEARCH
import com.fuwanime.ui.navigation.TAB_STATS
import com.fuwanime.ui.stats.DayBar
import com.fuwanime.ui.stats.WeekSummary
import com.fuwanime.ui.stats.watchTimeLabel
import com.fuwanime.ui.stats.weekSummary
import com.fuwanime.ui.stats.weekdayLabel
import com.fuwanime.util.epochDay
import com.fuwanime.util.localizedContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Home-screen widget: this week's watch time and finished episodes, from the same weekSummary the
 * stats screen uses (Monday-based weeks would shift the window; the screen shows the last 7 days).
 * A tap opens the stats screen.
 */
class WeeklyStatsWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(widgetSmall, widgetWide, widgetTall))
    override val stateDefinition: GlanceStateDefinition<*> = WidgetStateDefinition("weeklyStats")

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as FuwaApp).container
        val library = container.libraryRepository
        val weekFlow = combine(library.allHistory, library.watchDaily) { history, daily ->
            weekSummary(history, daily, epochDay(System.currentTimeMillis()))
        }
        val initial = weekFlow.first()
        provideContent {
            // Collected inside the session so progress recorded while it is alive refreshes the numbers.
            val week by weekFlow.collectAsState(initial)
            WidgetTheme {
                WidgetBody(context, week)
            }
        }
    }
}

class WeeklyStatsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeeklyStatsWidget()
}

private fun openApp(context: Context, vararg extras: Pair<String, String>) =
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        // Distinct data keeps each target's PendingIntent from being merged with another's.
        data = Uri.parse("fuwanime://widget/weekly-stats/" + extras.joinToString("/") { it.second })
        extras.forEach { (key, value) -> putExtra(key, value) }
    }

@Composable
private fun WidgetBody(context: Context, week: WeekSummary) {
    val size = LocalSize.current
    val res = localizedContext(context).resources
    Box(
        GlanceModifier.fillMaxSize().appWidgetBackground().widgetSurface()
            .clickable(actionStartActivity(openApp(context, MainActivity.EXTRA_OPEN_TAB to TAB_STATS))),
    ) {
        if (week.watchedMs == 0L && week.episodes == 0) {
            WidgetEmptyState(
                res.getString(R.string.widget_weekly_stats_empty),
                res.getString(R.string.system_widget_explore),
                actionStartActivity(openApp(context, MainActivity.EXTRA_OPEN_TAB to TAB_EXPLORE_SEARCH)),
            )
        } else when {
            size.width < widgetWide.width -> Small(week, res)
            size.height >= widgetTall.height -> Tall(context, week, res)
            else -> Wide(week, res)
        }
    }
}

/** 2x2: this week's watch time with the finished-episode count on a primary tonal card. */
@Composable
private fun Small(week: WeekSummary, res: Resources) {
    Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
        WidgetHeader(res.getString(R.string.widget_weekly_stats_title), fontSize = 12.sp)
        Column(GlanceModifier.fillMaxWidth().defaultWeight().widgetPrimaryCard().padding(6.dp)) {
            Text(
                watchTimeLabel(res, week.watchedMs),
                maxLines = 2,
                style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                res.getString(R.string.widget_weekly_stats_episodes_line, week.episodes),
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 10.sp),
            )
        }
    }
}

/** 4x2: watch time, finished episodes and the last-7-days bars side by side. */
@Composable
private fun Wide(week: WeekSummary, res: Resources) {
    Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
        WidgetHeader(res.getString(R.string.widget_weekly_stats_title))
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            WeeklyCard(
                watchTimeLabel(res, week.watchedMs), res.getString(R.string.widget_weekly_stats_time),
                GlanceModifier.defaultWeight().widgetPrimaryCard(), GlanceTheme.colors.onPrimaryContainer,
                13.sp, labelMaxLines = 1,
            )
            Spacer(GlanceModifier.width(8.dp))
            WeeklyCard(
                week.episodes.toString(), res.getString(R.string.widget_stats_episodes),
                GlanceModifier.defaultWeight().widgetSecondaryCard(), GlanceTheme.colors.onSecondaryContainer,
                18.sp, labelMaxLines = 2,
            )
            Spacer(GlanceModifier.width(8.dp))
            Column(
                GlanceModifier.defaultWeight().widgetCard().padding(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                WeekBars(week.days, barHeight = 36.dp)
            }
        }
    }
}

/** 4x4: a hero watch-time card, the episode count and daily average, and a labeled 7-day chart. */
@Composable
private fun Tall(context: Context, week: WeekSummary, res: Resources) {
    val locale = res.configuration.locales[0]
    Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
        WidgetHeader(res.getString(R.string.widget_weekly_stats_title))
        Column(GlanceModifier.fillMaxWidth().widgetPrimaryCard().padding(10.dp)) {
            Text(
                watchTimeLabel(res, week.watchedMs),
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 22.sp, fontWeight = FontWeight.Bold),
            )
            Text(res.getString(R.string.widget_weekly_stats_time), maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 11.sp))
        }
        Spacer(GlanceModifier.height(8.dp))
        Row(GlanceModifier.fillMaxWidth()) {
            WeeklyCard(
                week.episodes.toString(), res.getString(R.string.widget_stats_episodes),
                GlanceModifier.defaultWeight().widgetSecondaryCard(), GlanceTheme.colors.onSecondaryContainer,
                18.sp, labelMaxLines = 2,
            )
            Spacer(GlanceModifier.width(8.dp))
            WeeklyCard(
                watchTimeLabel(res, week.watchedMs / 7), res.getString(R.string.widget_weekly_stats_avg),
                GlanceModifier.defaultWeight().widgetTertiaryCard(), GlanceTheme.colors.onTertiaryContainer,
                14.sp, labelMaxLines = 2,
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        Column(GlanceModifier.fillMaxWidth().defaultWeight().widgetCard().padding(6.dp)) {
            WeekBars(week.days, barHeight = 28.dp, modifier = GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.height(4.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                week.days.forEach { d ->
                    Text(
                        weekdayLabel(d.day, locale),
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 9.sp, textAlign = TextAlign.Center),
                        modifier = GlanceModifier.defaultWeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyCard(
    value: String,
    label: String,
    modifier: GlanceModifier,
    onContainer: ColorProvider,
    valueSize: TextUnit,
    labelMaxLines: Int,
) {
    Column(modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, maxLines = 2, style = TextStyle(color = onContainer, fontSize = valueSize, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.height(2.dp))
        Text(label, maxLines = labelMaxLines, style = TextStyle(color = onContainer, fontSize = 9.sp))
    }
}

/** Rounded bars for the last 7 days; today is primary, earlier days a muted outline tone. */
@Composable
private fun WeekBars(days: List<DayBar>, barHeight: Dp, modifier: GlanceModifier = GlanceModifier) {
    val max = (days.maxOfOrNull { it.minutes } ?: 0).coerceAtLeast(1)
    val today = days.lastOrNull()?.day
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        days.forEach { d ->
            Column(
                GlanceModifier.defaultWeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.Bottom,
            ) {
                if (d.minutes > 0) {
                    val h = (barHeight * (d.minutes.toFloat() / max)).coerceAtLeast(3.dp)
                    Box(
                        GlanceModifier.width(6.dp).height(h).cornerRadius(3.dp)
                            .background(if (d.day == today) GlanceTheme.colors.primary else GlanceTheme.colors.outline),
                    ) {}
                }
            }
        }
    }
}
