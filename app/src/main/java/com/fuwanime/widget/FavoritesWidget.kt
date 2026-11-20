package com.fuwanime.widget

import android.content.Context
import android.content.Intent
import android.content.res.Resources
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
import com.fuwanime.data.local.FavoriteEntity
import com.fuwanime.data.local.MangaFavoriteEntity
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

/** Poster bitmaps are decoded at this size; keeps the RemoteViews bitmap budget small. */
private const val POSTER_PX = 240

/** The app's own palette where Material You is unavailable (Android 11 and lower). */
private val AppColors = ColorProviders(LightScheme, DarkScheme)

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

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, TALL))

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
            GlanceTheme(colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) DynamicThemeColorProviders else AppColors) {
                WidgetBody(context, entries, posters)
            }
        }
    }

    private suspend fun loadPosters(context: Context, entries: List<FavoriteEntry>): Map<String, Bitmap?> =
        entries.associate { it.key to loadBitmap(context, it.imageUrl, POSTER_PX) }
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
        GlanceModifier.fillMaxSize().appWidgetBackground().background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(R.dimen.widget_radius)
            .clickable(actionStartActivity(openApp(context))),
    ) {
        when {
            entries.isEmpty() -> EmptyState(context, res.getString(R.string.widget_favorites_empty))
            size.width < WIDE.width -> Hero(context, entries.first(), posters[entries.first().key], res)
            size.height >= TALL.height -> Grid(context, entries, posters, res)
            else -> Rows(context, entries.take(2), posters, res)
        }
    }
}

@Composable
private fun Header(res: Resources) {
    Text(
        res.getString(R.string.widget_favorites_title),
        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
        modifier = GlanceModifier.padding(start = 4.dp, bottom = 6.dp),
    )
}

/** 2x2: one favorite, its poster shown whole beside the title so nothing of the art is cropped. */
@Composable
private fun Hero(context: Context, entry: FavoriteEntry, poster: Bitmap?, res: Resources) {
    Column(
        GlanceModifier.fillMaxSize().padding(12.dp).clickable(actionStartActivity(entry.target(context))),
    ) {
        Text(
            res.getString(R.string.widget_favorites_title),
            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(GlanceModifier.height(6.dp))
        Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.Top) {
            Poster(entry, poster, GlanceModifier.size(44.dp, 66.dp))
            Spacer(GlanceModifier.width(8.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    entry.title,
                    maxLines = 3,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                )
                Text(entry.typeLabel(res), maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp))
            }
        }
    }
}

/** 4x2: two tonal rows; anime on secondary, manga on tertiary so the two kinds read apart. */
@Composable
private fun Rows(context: Context, entries: List<FavoriteEntry>, posters: Map<String, Bitmap?>, res: Resources) {
    Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
        Header(res)
        entries.forEachIndexed { i, entry ->
            if (i > 0) Spacer(GlanceModifier.height(6.dp))
            val manga = entry is FavoriteEntry.Manga
            Row(
                GlanceModifier.fillMaxWidth().height(52.dp).cornerRadius(14.dp)
                    .background(if (manga) GlanceTheme.colors.tertiaryContainer else GlanceTheme.colors.secondaryContainer)
                    .padding(6.dp).clickable(actionStartActivity(entry.target(context))),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Poster(entry, posters[entry.key], GlanceModifier.size(32.dp, 48.dp))
                Spacer(GlanceModifier.width(10.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text(
                        entry.title,
                        maxLines = 1,
                        style = TextStyle(
                            color = if (manga) GlanceTheme.colors.onTertiaryContainer else GlanceTheme.colors.onSecondaryContainer,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(entry.typeLabel(res), maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp))
                }
            }
        }
    }
}

/** 4x4: a 2x2 grid of poster cards. */
@Composable
private fun Grid(context: Context, entries: List<FavoriteEntry>, posters: Map<String, Bitmap?>, res: Resources) {
    Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
        Header(res)
        val cells = entries.take(4)
        for (row in 0 until 2) {
            if (row > 0) Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                for (col in 0 until 2) {
                    if (col > 0) Spacer(GlanceModifier.width(8.dp))
                    val i = row * 2 + col
                    if (i < cells.size) GridCard(context, cells[i], posters[cells[i].key], res, GlanceModifier.defaultWeight())
                    else Spacer(GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

@Composable
private fun GridCard(context: Context, entry: FavoriteEntry, poster: Bitmap?, res: Resources, modifier: GlanceModifier) {
    val manga = entry is FavoriteEntry.Manga
    Column(
        modifier.cornerRadius(16.dp)
            .background(if (manga) GlanceTheme.colors.tertiaryContainer else GlanceTheme.colors.secondaryContainer)
            .padding(8.dp).clickable(actionStartActivity(entry.target(context))),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Poster(entry, poster, GlanceModifier.size(52.dp, 78.dp))
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
        Text(entry.typeLabel(res), maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 9.sp))
    }
}

@Composable
private fun Poster(entry: FavoriteEntry, poster: Bitmap?, modifier: GlanceModifier) {
    Box(modifier.cornerRadius(8.dp).background(GlanceTheme.colors.surfaceVariant), contentAlignment = Alignment.Center) {
        if (poster != null) {
            Image(ImageProvider(poster), entry.title, GlanceModifier.fillMaxSize().cornerRadius(8.dp), contentScale = ContentScale.Fit)
        }
    }
}

@Composable
private fun EmptyState(context: Context, message: String) {
    Column(GlanceModifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.height(8.dp))
        Text(
            localizedContext(context).getString(R.string.system_widget_explore),
            style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.cornerRadius(20.dp).background(GlanceTheme.colors.primary).padding(horizontal = 16.dp, vertical = 10.dp)
                .clickable(actionStartActivity(openApp(context, MainActivity.EXTRA_OPEN_TAB to TAB_EXPLORE_SEARCH))),
        )
    }
}
