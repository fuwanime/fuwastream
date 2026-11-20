package com.fuwanime.widget

import android.content.Context
import android.content.Intent
import android.content.res.Resources
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
import com.fuwanime.data.local.FavoriteEntity
import com.fuwanime.data.local.MangaFavoriteEntity
import com.fuwanime.ui.navigation.TAB_EXPLORE_SEARCH
import com.fuwanime.util.loadBitmap
import com.fuwanime.util.localizedContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** Poster bitmaps are decoded at this size; keeps the RemoteViews bitmap budget small. */
private const val POSTER_PX = 240

/** One favorite: an anime or a manga, newest added first. */
private sealed interface FavoriteEntry {
    val key: String
    val title: String
    val imageUrl: String
    val addedAt: Long

    data class Anime(val row: FavoriteEntity) : FavoriteEntry {
        override val key get() = "a:" + row.animeId
        override val title get() = row.title
        override val imageUrl get() = row.poster.ifBlank { row.cover }
        override val addedAt get() = row.addedAt
    }

    data class Manga(val row: MangaFavoriteEntity) : FavoriteEntry {
        override val key get() = "m:" + row.mangaId
        override val title get() = row.title
        override val imageUrl get() = row.coverUrl.orEmpty()
        override val addedAt get() = row.addedAt
    }
}

/** Newest first, at most [limit]; both sources are already one row per title. */
private fun mergeFavorites(anime: List<FavoriteEntity>, manga: List<MangaFavoriteEntity>, limit: Int): List<FavoriteEntry> =
    (anime.map(FavoriteEntry::Anime) + manga.map(FavoriteEntry::Manga))
        .sortedByDescending { it.addedAt }
        .take(limit)

/** Home-screen widget: favorite anime and manga merged; a tap opens the anime or manga detail. */
class FavoritesWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(widgetSmall, widgetWide, widgetTall))
    override val stateDefinition: GlanceStateDefinition<*> = WidgetStateDefinition("favorites")

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as FuwaApp).container
        // favoriteList is the reactive form of allFavorites() (same DAO query, addedAt DESC).
        val entriesFlow = combine(container.libraryRepository.favoriteList, container.mangaLibrary.favorites) { a, m ->
            mergeFavorites(a, m, limit = 4)
        }
        val initial = entriesFlow.first()
        val initialPosters = loadPosters(context, initial)
        provideContent {
            // Collected inside the session: favorites added while it is alive only recompose, they don't
            // re-run provideGlance, so a list captured outside would go stale.
            val entries by entriesFlow.collectAsState(initial)
            val posters by produceState(if (entries == initial) initialPosters else emptyMap(), entries) {
                value = loadPosters(context, entries)
            }
            WidgetTheme {
                WidgetBody(context, entries, posters)
            }
        }
    }

    private suspend fun loadPosters(context: Context, entries: List<FavoriteEntry>): Map<String, Bitmap?> =
        entries.associate { it.key to loadBitmap(context, it.imageUrl, POSTER_PX)?.let(::roundPosterCorners) }
}

class FavoritesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FavoritesWidget()
}

private fun openApp(context: Context, vararg extras: Pair<String, String>) =
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        // Distinct data keeps each item's PendingIntent from being merged with another's.
        data = Uri.parse("fuwanime://widget/favorites/" + extras.joinToString("/") { it.second })
        extras.forEach { (key, value) -> putExtra(key, value) }
    }

private fun FavoriteEntry.target(context: Context) = when (this) {
    is FavoriteEntry.Anime -> openApp(context, MainActivity.EXTRA_OPEN_ANIME_ID to row.animeId)
    is FavoriteEntry.Manga -> openApp(context, MainActivity.EXTRA_OPEN_MANGA_ID to row.mangaId)
}

private fun FavoriteEntry.typeLabel(res: Resources) = when (this) {
    is FavoriteEntry.Anime -> res.getString(R.string.widget_favorites_anime)
    is FavoriteEntry.Manga -> res.getString(R.string.widget_favorites_manga)
}

@Composable
private fun WidgetBody(context: Context, entries: List<FavoriteEntry>, posters: Map<String, Bitmap?>) {
    val size = LocalSize.current
    val res = localizedContext(context).resources
    // The whole widget opens the app; cards and "Explore" override it with their own targets.
    Box(
        GlanceModifier.fillMaxSize().appWidgetBackground().widgetSurface()
            .clickable(actionStartActivity(openApp(context))),
    ) {
        when {
            entries.isEmpty() -> WidgetEmptyState(
                res.getString(R.string.widget_favorites_empty),
                res.getString(R.string.system_widget_explore),
                actionStartActivity(openApp(context, MainActivity.EXTRA_OPEN_TAB to TAB_EXPLORE_SEARCH)),
            )
            size.width < widgetWide.width -> Hero(context, entries.first(), posters[entries.first().key], res)
            size.height >= widgetTall.height -> Grid(context, entries, posters, res)
            else -> PairRow(context, entries.take(2), posters, res)
        }
    }
}

/** 2x2: one favorite on a tonal card, its poster shown whole beside the title. */
@Composable
private fun Hero(context: Context, entry: FavoriteEntry, poster: Bitmap?, res: Resources) {
    Column(
        GlanceModifier.fillMaxSize().padding(12.dp).clickable(actionStartActivity(entry.target(context))),
    ) {
        WidgetHeader(res.getString(R.string.widget_favorites_title), fontSize = 12.sp)
        Row(
            GlanceModifier.fillMaxWidth().defaultWeight().widgetCard().padding(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            WidgetPoster(entry.title, poster, GlanceModifier.size(32.dp, 48.dp))
            Spacer(GlanceModifier.width(8.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    entry.title,
                    maxLines = 2,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(entry.typeLabel(res), maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp))
            }
        }
    }
}

/** 4x2: two tonal cards side by side; anime on secondary, manga on tertiary so the two kinds read apart. */
@Composable
private fun PairRow(context: Context, entries: List<FavoriteEntry>, posters: Map<String, Bitmap?>, res: Resources) {
    Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
        WidgetHeader(res.getString(R.string.widget_favorites_title))
        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
            for (col in 0 until 2) {
                if (col > 0) Spacer(GlanceModifier.width(8.dp))
                if (col < entries.size) PairCard(context, entries[col], posters[entries[col].key], res, GlanceModifier.defaultWeight())
                else Spacer(GlanceModifier.defaultWeight())
            }
        }
    }
}

@Composable
private fun PairCard(context: Context, entry: FavoriteEntry, poster: Bitmap?, res: Resources, modifier: GlanceModifier) {
    val manga = entry is FavoriteEntry.Manga
    Row(
        modifier.then(if (manga) GlanceModifier.widgetTertiaryCard() else GlanceModifier.widgetSecondaryCard())
            .padding(6.dp).clickable(actionStartActivity(entry.target(context))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetPoster(entry.title, poster, GlanceModifier.size(24.dp, 36.dp))
        Spacer(GlanceModifier.width(6.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(
                entry.title,
                maxLines = 2,
                style = TextStyle(
                    color = if (manga) GlanceTheme.colors.onTertiaryContainer else GlanceTheme.colors.onSecondaryContainer,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                entry.typeLabel(res),
                maxLines = 1,
                style = TextStyle(
                    color = if (manga) GlanceTheme.colors.onTertiaryContainer else GlanceTheme.colors.onSecondaryContainer,
                    fontSize = 9.sp,
                ),
            )
        }
    }
}

/** 4x4: a 2x2 grid of poster cards; the container color tells anime from manga. */
@Composable
private fun Grid(context: Context, entries: List<FavoriteEntry>, posters: Map<String, Bitmap?>, res: Resources) {
    Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
        WidgetHeader(res.getString(R.string.widget_favorites_title))
        val cells = entries.take(4)
        for (row in 0 until 2) {
            if (row > 0) Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                for (col in 0 until 2) {
                    if (col > 0) Spacer(GlanceModifier.width(8.dp))
                    val i = row * 2 + col
                    if (i < cells.size) GridCard(context, cells[i], posters[cells[i].key], GlanceModifier.defaultWeight())
                    else Spacer(GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

@Composable
private fun GridCard(context: Context, entry: FavoriteEntry, poster: Bitmap?, modifier: GlanceModifier) {
    val manga = entry is FavoriteEntry.Manga
    Column(
        modifier.then(if (manga) GlanceModifier.widgetTertiaryCard() else GlanceModifier.widgetSecondaryCard())
            .padding(6.dp).clickable(actionStartActivity(entry.target(context))),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WidgetPoster(entry.title, poster, GlanceModifier.size(36.dp, 54.dp))
        Spacer(GlanceModifier.height(4.dp))
        Text(
            entry.title,
            maxLines = 2,
            style = TextStyle(
                color = if (manga) GlanceTheme.colors.onTertiaryContainer else GlanceTheme.colors.onSecondaryContainer,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}
