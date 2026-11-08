package com.fuwanime.ui.manga

import com.fuwanime.ui.components.SectionHeader
import com.fuwanime.data.manga.MangaTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuwanime.R
import com.fuwanime.ui.components.LocalNavBarPadding
import com.fuwanime.util.fuwaViewModel

/** The Manga tab: search and filters on top; shelves (Lanjut baca, Populer, Terbaru) until a search or filter is active. */
@Composable
fun MangaTabScreen(onMangaClick: (String) -> Unit, onRead: (mangaId: String, chapterId: String) -> Unit) {
    val homeVm: MangaHomeViewModel = fuwaViewModel { c, _ -> MangaHomeViewModel(c.mangaRepository, c.mangaLibrary) }
    val exploreVm: MangaExploreViewModel = fuwaViewModel { c, _ -> MangaExploreViewModel(c.mangaRepository) }
    val home by homeVm.state.collectAsStateWithLifecycle()
    val explore by exploreVm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        homeVm.start()
        exploreVm.start()
    }
    val genre by pendingMangaGenre.collectAsStateWithLifecycle()
    LaunchedEffect(genre) {
        genre?.let { exploreVm.applyGenre(it); pendingMangaGenre.value = null }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            stringResource(R.string.manga_tab),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
        )
        OutlinedTextField(
            value = explore.query,
            onValueChange = exploreVm::onQueryChange,
            placeholder = { Text(stringResource(R.string.manga_search_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (explore.query.isNotEmpty()) {
                    IconButton(onClick = { exploreVm.onQueryChange("") }) { Icon(Icons.Rounded.Clear, stringResource(R.string.browse_clear)) }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Column(Modifier.padding(top = 12.dp)) { MangaFilterBar(explore, exploreVm) }
        if (explore.filtered) {
            MangaResultsGrid(explore, exploreVm, onMangaClick, topPad = 0.dp)
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp + LocalNavBarPadding.current)) {
                mangaHomeItems(
                    home, topPad = 0.dp, onRetry = homeVm::refresh, onMangaClick = onMangaClick, onRead = onRead,
                    middle = { GenreTiles(explore.tags, onPick = exploreVm::toggleTag) },
                )
            }
        }
    }
}

/** Shortcuts for the genres people browse most; a tap applies that genre filter (MangaDex tag names are English). */
private val GENRE_TILES = listOf(
    "Action" to R.string.manga_genre_action, "Romance" to R.string.manga_genre_romance, "Horror" to R.string.manga_genre_horror,
    "Comedy" to R.string.manga_genre_comedy, "Fantasy" to R.string.manga_genre_fantasy, "Isekai" to R.string.manga_genre_isekai,
)

@Composable
private fun GenreTiles(tags: List<MangaTag>, onPick: (String) -> Unit) {
    val byName = tags.associateBy { it.name.lowercase() }
    val tiles = GENRE_TILES.mapNotNull { (name, label) -> byName[name.lowercase()]?.let { it.id to label } }
    if (tiles.isEmpty()) return
    val colors = listOf(Color(0xFF3B2A5C), Color(0xFF5C2A44), Color(0xFF2A2F5C), Color(0xFF2A5C4E), Color(0xFF5C4A2A), Color(0xFF4A2A5C))
    Column(Modifier.padding(top = 24.dp)) {
        SectionHeader(stringResource(R.string.manga_home_genres))
        Spacer(Modifier.height(12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(tiles, key = { _, t -> t.first }) { i, (id, label) ->
                Box(
                    Modifier.size(width = 132.dp, height = 72.dp).clip(RoundedCornerShape(16.dp)).background(colors[i % colors.size]).clickable { onPick(id) }.padding(12.dp),
                    contentAlignment = Alignment.BottomStart,
                ) { Text(stringResource(label), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White) }
            }
        }
    }
}
