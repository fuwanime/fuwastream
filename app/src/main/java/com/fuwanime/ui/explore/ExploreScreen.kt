package com.fuwanime.ui.explore

import androidx.compose.foundation.layout.heightIn
import com.fuwanime.ui.components.LocalNavBarPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.fuwanime.ui.components.TagChip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import com.fuwanime.ui.motion.Motion
import com.fuwanime.ui.motion.staggerIn
import com.fuwanime.ui.motion.rememberStaggerState
import com.fuwanime.ui.motion.OpenAnime
import com.fuwanime.data.SortOrder
import com.fuwanime.ui.components.ErrorState
import com.fuwanime.ui.components.MessageState
import com.fuwanime.ui.components.PosterCard
import com.fuwanime.ui.components.PosterSkeleton
import com.fuwanime.util.fuwaViewModel

val AllGenres = listOf(
    "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Isekai", "Romance", "School",
    "Slice of Life", "Supernatural", "Mystery", "Psychological", "Sci-Fi", "Mecha", "Sports",
    "Magic", "Super Power", "Martial Arts", "Historical", "Military", "Seinen", "Shounen",
    "Shoujo", "Harem", "Horror", "Thriller", "Game", "Parody", "Demons",
)

@Composable
fun ExploreRoute(focusSearch: Boolean, onAnimeClick: OpenAnime) {
    val vm: ExploreViewModel = fuwaViewModel { c, h -> ExploreViewModel(c.animeRepository, c.libraryRepository, h) }
    val state by vm.state.collectAsStateWithLifecycle()
    val recent by vm.recentSearches.collectAsStateWithLifecycle()
    ExploreScreen(state, recent, focusSearch, vm, onAnimeClick)
}

@Composable
private fun ExploreScreen(
    state: ExploreUiState,
    recent: List<String>,
    focusSearch: Boolean,
    vm: ExploreViewModel,
    onAnimeClick: OpenAnime,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var searchFocused by remember { mutableStateOf(false) }
    LaunchedEffect(focusSearch) { if (focusSearch) focusRequester.requestFocus() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            "Jelajah",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 12.dp),
        )
        OutlinedTextField(
            value = state.filters.query,
            onValueChange = vm::onQueryChange,
            placeholder = { Text("Judul, genre, atau studio") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (state.filters.query.isNotEmpty()) {
                    IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Rounded.Close, "Hapus") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                vm.submitSearch()
                focusManager.clearFocus()
            }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { searchFocused = it.isFocused },
        )

        if (searchFocused && state.filters.query.isBlank() && recent.isNotEmpty()) {
            RecentSearches(
                recent,
                onPick = {
                    vm.submitSearch(it)
                    focusManager.clearFocus()
                },
                onRemove = { vm.removeRecent(it) },
                onClear = { vm.clearRecent() },
            )
            return@Column
        }

        Spacer(Modifier.height(14.dp))
        FilterBar(state.filters, vm)
        ResultsHeader(state.filters, vm, onRandom = state.results.takeIf { it.isNotEmpty() }?.let { r -> { pickRandom(r)?.let { onAnimeClick(it, "explore") } } })
        ResultsGrid(state, vm, onAnimeClick)
    }
}

@Composable
private fun RecentSearches(items: List<String>, onPick: (String) -> Unit, onRemove: (String) -> Unit, onClear: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp + LocalNavBarPadding.current)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Pencarian terakhir", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onClear) { Text("Hapus semua") }
            }
        }
        items(items, key = { it }) { q ->
            Row(
                Modifier.fillMaxWidth().clickable { onPick(q) }.padding(start = 20.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Text(q, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { onRemove(q) }) { Icon(Icons.Rounded.Close, "Hapus", Modifier.size(18.dp)) }
                Icon(Icons.Rounded.NorthWest, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
            }
        }
    }
}

@Composable
private fun FilterBar(filters: ExploreFilters, vm: ExploreViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TypeFilter.entries, key = { it.name }) { t ->
                TagChip(t.label, onClick = { vm.setType(t) }, selected = t == filters.type)
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AllGenres, key = { it }) { g ->
                TagChip(g, onClick = { vm.toggleGenre(g) }, selected = g in filters.genres)
            }
        }
    }
}

/** Title of what is shown, plus one menu for sort order and airing status. */
@Composable
/** [onRandom] is null while there is nothing to pick from. */
private fun ResultsHeader(filters: ExploreFilters, vm: ExploreViewModel, onRandom: (() -> Unit)?) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            filters.query.trim().takeIf { it.isNotEmpty() }?.let { "Hasil untuk “$it”" }
                ?: filters.genres.takeIf { it.isNotEmpty() }?.joinToString(" + ") ?: filters.type.takeIf { it != TypeFilter.ALL }?.label ?: "Semua anime",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onRandom?.invoke() }, enabled = onRandom != null) { Icon(Icons.Rounded.Casino, "Anime acak") }
        Box {
            FilledTonalButton(
                onClick = { menuOpen = true },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 8.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.heightIn(min = 36.dp),
            ) {
                Icon(Icons.Rounded.Sort, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (filters.status == StatusFilter.ALL) filters.sort.label else "${filters.sort.label} · ${filters.status.label}",
                    style = MaterialTheme.typography.labelLarge,
                )
                Icon(Icons.Rounded.ArrowDropDown, null)
            }
            DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                MenuLabel("Urutkan")
                SortOrder.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.label) },
                        onClick = { vm.setSort(s); menuOpen = false },
                        trailingIcon = { if (s == filters.sort) Icon(Icons.Rounded.Check, null) },
                    )
                }
                HorizontalDivider()
                MenuLabel("Status")
                StatusFilter.entries.forEach { st ->
                    DropdownMenuItem(
                        text = { Text(st.label) },
                        onClick = { vm.setStatus(st); menuOpen = false },
                        trailingIcon = { if (st == filters.status) Icon(Icons.Rounded.Check, null) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun ResultsGrid(state: ExploreUiState, vm: ExploreViewModel, onAnimeClick: OpenAnime) {
    val stagger = rememberStaggerState()
    val gridState = rememberLazyGridState()
    val nearEnd by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= gridState.layoutInfo.totalItemsCount - 9
        }
    }
    LaunchedEffect(nearEnd, state.results.size) {
        if (nearEnd && state.results.isNotEmpty() && !state.loading) vm.loadMore()
    }

    val branch = when {
        state.loading && state.results.isEmpty() -> ResultsBranch.LOADING
        state.error != null && state.results.isEmpty() -> ResultsBranch.ERROR
        state.results.isEmpty() && !state.loadingMore -> ResultsBranch.EMPTY
        else -> ResultsBranch.RESULTS
    }
    Crossfade(branch, animationSpec = tween(Motion.Medium), label = "exploreResults") { shown ->
    when (shown) {
        ResultsBranch.LOADING -> LazyVerticalGrid(
            columns = GridCells.Adaptive(108.dp),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + LocalNavBarPadding.current),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            userScrollEnabled = false,
        ) { items(12) { PosterSkeleton() } }

        ResultsBranch.ERROR -> ErrorState(state.error.orEmpty(), vm::reload)

        ResultsBranch.EMPTY -> MessageState(
            icon = Icons.Rounded.SearchOff,
            title = "Tidak ada hasil",
            message = "Coba kata kunci lain atau longgarkan filter.",
            actionLabel = if (state.filters.activeCount > 0) "Hapus filter" else null,
            onAction = if (state.filters.activeCount > 0) vm::clearFilters else null,
        )

        ResultsBranch.RESULTS -> LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(108.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp + LocalNavBarPadding.current),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(state.results, key = { _, a -> a.id }) { index, anime ->
                PosterCard(
                    anime,
                    onClick = { onAnimeClick(anime, "explore") },
                    modifier = Modifier.animateItem().staggerIn(stagger, anime.id, index),
                    sharedOrigin = "explore",
                )
            }
            if (state.loadingMore || (!state.endReached && state.error == null)) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    }
                }
            }
            if (state.error != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = vm::loadMore) { Text("Gagal memuat. Coba lagi") }
                    }
                }
            }
        }
    }
    }
}

private enum class ResultsBranch { LOADING, ERROR, EMPTY, RESULTS }
