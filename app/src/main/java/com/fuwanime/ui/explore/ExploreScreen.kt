package com.fuwanime.ui.explore

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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.fuwanime.data.Anime
import com.fuwanime.data.SortOrder
import com.fuwanime.ui.components.ErrorState
import com.fuwanime.ui.components.MessageState
import com.fuwanime.ui.components.PosterCard
import com.fuwanime.ui.components.PosterSkeleton
import com.fuwanime.util.compactNumber
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
        TextField(
            value = state.filters.query,
            onValueChange = vm::onQueryChange,
            placeholder = { Text("Cari judul anime…") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (state.filters.query.isNotEmpty()) {
                    IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Rounded.Close, "Hapus") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(50),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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

        Spacer(Modifier.height(12.dp))
        FilterBar(state.filters, vm)
        Spacer(Modifier.height(4.dp))
        ResultsGrid(state, vm, onAnimeClick)
    }
}

@Composable
private fun RecentSearches(items: List<String>, onPick: (String) -> Unit, onRemove: (String) -> Unit, onClear: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(vertical = 12.dp)) {
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
    var sortOpen by remember { mutableStateOf(false) }
    var statusOpen by remember { mutableStateOf(false) }
    var typeOpen by remember { mutableStateOf(false) }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item {
            Box {
                FilterChip(
                    selected = false,
                    onClick = { sortOpen = true },
                    label = { Text(filters.sort.label) },
                    leadingIcon = { Icon(Icons.Rounded.Sort, null, Modifier.size(FilterChipDefaults.IconSize)) },
                    trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null) },
                )
                DropdownMenu(sortOpen, onDismissRequest = { sortOpen = false }) {
                    SortOrder.entries.forEach { s ->
                        DropdownMenuItem(text = { Text(s.label) }, onClick = { vm.setSort(s); sortOpen = false })
                    }
                }
            }
        }
        item {
            Box {
                FilterChip(
                    selected = filters.status != StatusFilter.ALL,
                    onClick = { statusOpen = true },
                    label = { Text(if (filters.status == StatusFilter.ALL) "Status" else filters.status.label) },
                    trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null) },
                )
                DropdownMenu(statusOpen, onDismissRequest = { statusOpen = false }) {
                    StatusFilter.entries.forEach { s ->
                        DropdownMenuItem(text = { Text(s.label) }, onClick = { vm.setStatus(s); statusOpen = false })
                    }
                }
            }
        }
        item {
            Box {
                FilterChip(
                    selected = filters.type != TypeFilter.ALL,
                    onClick = { typeOpen = true },
                    label = { Text(if (filters.type == TypeFilter.ALL) "Tipe" else filters.type.label) },
                    trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null) },
                )
                DropdownMenu(typeOpen, onDismissRequest = { typeOpen = false }) {
                    TypeFilter.entries.forEach { t ->
                        DropdownMenuItem(text = { Text(t.label) }, onClick = { vm.setType(t); typeOpen = false })
                    }
                }
            }
        }
        item { VerticalDivider(Modifier.height(24.dp)) }
        items(AllGenres, key = { it }) { g ->
            val selected = filters.genre == g
            FilterChip(
                selected = selected,
                onClick = { vm.setGenre(if (selected) null else g) },
                label = { Text(g) },
            )
        }
    }
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
            contentPadding = PaddingValues(16.dp),
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(state.results, key = { _, a -> a.id }) { index, anime ->
                PosterCard(
                    anime,
                    onClick = { onAnimeClick(anime, "explore") },
                    modifier = Modifier.animateItem().staggerIn(stagger, anime.id, index),
                    caption = "${compactNumber(anime.views)} ditonton",
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
