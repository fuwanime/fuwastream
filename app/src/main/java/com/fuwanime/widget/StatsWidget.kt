package com.fuwanime.widget

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
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
import androidx.glance.unit.ColorProvider
import com.fuwanime.FuwaApp
import com.fuwanime.MainActivity
import com.fuwanime.R
import com.fuwanime.ui.navigation.TAB_EXPLORE_SEARCH
import com.fuwanime.ui.stats.WatchStats
import com.fuwanime.ui.stats.computeStats
import com.fuwanime.ui.stats.watchTimeLabel
import com.fuwanime.ui.theme.DarkScheme
import com.fuwanime.ui.theme.LightScheme
import com.fuwanime.util.epochDay
import com.fuwanime.util.localizedContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

private val SMALL = DpSize(110.dp, 110.dp)
private val WIDE = DpSize(250.dp, 110.dp)
private val TALL = DpSize(250.dp, 250.dp)

/** The app's own palette where Material You is unavailable (Android 11 and lower). */
private val AppColors = ColorProviders(LightScheme, DarkScheme)

/**
 * The stats route exists (Routes.STATS) but parseLaunchTarget has no stats tab case yet. This extra is
 * forward-compatible: once the nav host maps "stats", the tap lands on the stats screen; until then it
 * simply opens the app.
 */
private const val TAB_STATS = "stats"

/** Home-screen widget: watch-time summary from the same computeStats the stats screen uses. */
class StatsWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as FuwaApp).container
        val library = container.libraryRepository
        // Same combination as StatsViewModel.
        val statsFlow = combine(library.allHistory, library.watchDaily, library.favoriteList) { history, daily, favorites ->
            computeStats(history, daily, favorites, epochDay(System.currentTimeMillis()))
        }
        val initial = statsFlow.first()
        provideContent {
            // Collected inside the session so progress recorded while it is alive refreshes the numbers.
            val stats by statsFlow.collectAsState(initial)
            GlanceTheme(colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) DynamicThemeColorProviders else AppColors) {
                WidgetBody(context, stats)
            }
        }
    }
}

class StatsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatsWidget()
}

private fun openApp(context: Context, vararg extras: Pair<String, String>) =
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        // Distinct data keeps each target's PendingIntent from being merged with another's.
        data = Uri.parse("fuwanime://widget/stats/" + extras.joinToString("/") { it.second })
        extras.forEach { (key, value) -> putExtra(key, value) }
    }

/** "12 jam 5 mnt" in the app language; older history without daily records is an estimate. */
private fun totalLabel(stats: WatchStats, res: Resources): String {
    val label = watchTimeLabel(res, stats.totalWatchedMs)
    return if (stats.isEstimated) "± $label" else label
}

@Composable
private fun WidgetBody(context: Context, stats: WatchStats) {
    val size = LocalSize.current
    val res = localizedContext(context).resources
    Box(
        GlanceModifier.fillMaxSize().appWidgetBackground().background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(R.dimen.widget_radius)
            .clickable(actionStartActivity(openApp(context, MainActivity.EXTRA_OPEN_TAB to TAB_STATS))),
    ) {
        if (!stats.hasData) {
            EmptyState(context, res)
        } else when {
            size.width < WIDE.width -> Small(stats, res)
            size.height >= TALL.height -> Tall(stats, res)
            else -> Wide(stats, res)
        }
    }
}

@Composable
private fun Header(res: Resources) {
    Text(
        res.getString(R.string.widget_stats_title),
        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
        modifier = GlanceModifier.padding(start = 4.dp, bottom = 6.dp),
    )
}

/** 2x2: the total on a tonal card, episodes and anime as one line below. */
@Composable
private fun Small(stats: WatchStats, res: Resources) {
    Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
        Text(
            res.getString(R.string.widget_stats_title),
            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(GlanceModifier.height(6.dp))
        Column(GlanceModifier.fillMaxWidth().cornerRadius(16.dp).background(GlanceTheme.colors.primaryContainer).padding(10.dp)) {
            Text(
                totalLabel(stats, res),
                maxLines = 2,
                style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            )
            Text(res.getString(R.string.widget_stats_total), maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 10.sp))
        }
        Spacer(GlanceModifier.height(6.dp))
        Text(
            res.getString(R.string.widget_stats_summary_line, stats.episodesFinished, stats.animeWatched),
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
        )
    }
}

/** 4x2: three tonal stat cards side by side. */
@Composable
private fun Wide(stats: WatchStats, res: Resources) {
    Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
        Header(res)
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            StatCard(
                totalLabel(stats, res), res.getString(R.string.widget_stats_total),
                GlanceTheme.colors.primaryContainer, GlanceTheme.colors.onPrimaryContainer,
                GlanceModifier.defaultWeight(), 14.sp,
            )
            Spacer(GlanceModifier.width(8.dp))
            StatCard(
                stats.episodesFinished.toString(), res.getString(R.string.widget_stats_episodes),
                GlanceTheme.colors.secondaryContainer, GlanceTheme.colors.onSecondaryContainer,
                GlanceModifier.defaultWeight(), 18.sp,
            )
            Spacer(GlanceModifier.width(8.dp))
            StatCard(
                stats.animeWatched.toString(), res.getString(R.string.widget_stats_anime),
                GlanceTheme.colors.tertiaryContainer, GlanceTheme.colors.onTertiaryContainer,
                GlanceModifier.defaultWeight(), 18.sp,
            )
        }
    }
}

/** 4x4: a hero total card, the two counts, and the favorite genres. */
@Composable
private fun Tall(stats: WatchStats, res: Resources) {
    Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
        Header(res)
        Column(GlanceModifier.fillMaxWidth().cornerRadius(20.dp).background(GlanceTheme.colors.primaryContainer).padding(12.dp)) {
            Text(
                totalLabel(stats, res),
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 22.sp, fontWeight = FontWeight.Bold),
            )
            Text(res.getString(R.string.widget_stats_total), maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 11.sp))
        }
        Spacer(GlanceModifier.height(8.dp))
        Row(GlanceModifier.fillMaxWidth()) {
            StatCard(
                stats.episodesFinished.toString(), res.getString(R.string.widget_stats_episodes),
                GlanceTheme.colors.secondaryContainer, GlanceTheme.colors.onSecondaryContainer,
                GlanceModifier.defaultWeight(), 18.sp,
            )
            Spacer(GlanceModifier.width(8.dp))
            StatCard(
                stats.animeWatched.toString(), res.getString(R.string.widget_stats_anime),
                GlanceTheme.colors.tertiaryContainer, GlanceTheme.colors.onTertiaryContainer,
                GlanceModifier.defaultWeight(), 18.sp,
            )
        }
        if (stats.topGenres.isNotEmpty()) {
            Spacer(GlanceModifier.height(8.dp))
            Column(GlanceModifier.fillMaxWidth().cornerRadius(16.dp).background(GlanceTheme.colors.surfaceVariant).padding(10.dp)) {
                Text(
                    res.getString(R.string.widget_stats_genres),
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                )
                Text(
                    stats.topGenres.joinToString(" • "),
                    maxLines = 2,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, container: ColorProvider, onContainer: ColorProvider, modifier: GlanceModifier, valueSize: TextUnit) {
    Column(
        modifier.cornerRadius(16.dp).background(container).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, maxLines = 2, style = TextStyle(color = onContainer, fontSize = valueSize, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.height(2.dp))
        Text(label, maxLines = 2, style = TextStyle(color = onContainer, fontSize = 9.sp))
    }
}

@Composable
private fun EmptyState(context: Context, res: Resources) {
    Column(GlanceModifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(res.getString(R.string.widget_stats_empty), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.height(4.dp))
        Text(res.getString(R.string.widget_stats_empty_hint), maxLines = 2, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp))
        Spacer(GlanceModifier.height(8.dp))
        Text(
            res.getString(R.string.system_widget_explore),
            style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.cornerRadius(20.dp).background(GlanceTheme.colors.primary).padding(horizontal = 16.dp, vertical = 10.dp)
                .clickable(actionStartActivity(openApp(context, MainActivity.EXTRA_OPEN_TAB to TAB_EXPLORE_SEARCH))),
        )
    }
}
