package com.fuwanime.ui.explore

import com.fuwanime.ui.components.GenreSheet
import com.fuwanime.ui.components.GenreChip
import com.fuwanime.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.AssistChip
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuwanime.data.SortOrder
import com.fuwanime.ui.components.ErrorState
import com.fuwanime.ui.components.LocalNavBarPadding
import com.fuwanime.ui.components.MessageState
import com.fuwanime.ui.components.PosterCard
import com.fuwanime.ui.components.PosterSkeleton
import com.fuwanime.ui.components.TagChip
import com.fuwanime.ui.motion.Motion
import com.fuwanime.ui.motion.OpenAnime
import com.fuwanime.ui.motion.rememberStaggerState
import com.fuwanime.ui.motion.staggerIn
import com.fuwanime.util.fuwaViewModel
import kotlin.math.roundToInt

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

    val showRecent = searchFocused && state.filters.query.isBlank() && recent.isNotEmpty()
    // The header (title, search, filters) slides away with the grid and returns on the first scroll up, so at large
    // font sizes the grid isn't left with a thin strip.
    var headerPx by remember { mutableIntStateOf(0) }
    var headerOffset by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state.filters, showRecent, searchFocused) { headerOffset = 0f }
    // Recent searches scroll below a header that must stay put: the search field is focused.
    val pinned by rememberUpdatedState(showRecent)
    val collapse = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!pinned) headerOffset = headerOffsetAfterScroll(headerOffset, consumed.y, headerPx.toFloat())
                return Offset.Zero
            }
        }
    }
    val headerDp = with(LocalDensity.current) { headerPx.toDp() }

    Box(Modifier.fillMaxSize().statusBarsPadding().clipToBounds().nestedScroll(collapse)) {
        if (showRecent) {
            RecentSearches(
                recent,
                topPad = headerDp,
                onPick = {
                    vm.submitSearch(it)
                    focusManager.clearFocus()
                },
                onRemove = { vm.removeRecent(it) },
                onClear = { vm.clearRecent() },
            )
        } else {
            ResultsGrid(state, vm, onAnimeClick, topPad = headerDp)
        }
    Column(
        Modifier
            .offset { IntOffset(0, headerOffset.roundToInt()) }
            .background(MaterialTheme.colorScheme.background)
            .onSizeChanged { headerPx = it.height },
    ) {
        Text(
            stringResource(R.string.common_tab_explore),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 12.dp),
        )
        OutlinedTextField(
            value = state.filters.query,
            onValueChange = vm::onQueryChange,
            placeholder = { Text(stringResource(R.string.browse_search_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (state.filters.query.isNotEmpty()) {
                    IconButton(onClick = { vm.onQueryChange("") }) { Icon(Icons.Rounded.Close, stringResource(R.string.browse_clear)) }
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

        if (!showRecent) {
            Spacer(Modifier.height(14.dp))
            FilterBar(state.filters, vm)
            ResultsHeader(state.filters, vm, onRandom = state.results.takeIf { it.isNotEmpty() }?.let { r -> { pickRandom(r)?.let { onAnimeClick(it, "explore") } } })
        }
    }
    }
}

@Composable
private fun RecentSearches(items: List<String>, topPad: Dp, onPick: (String) -> Unit, onRemove: (String) -> Unit, onClear: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(top = topPad + 12.dp, bottom = 12.dp + LocalNavBarPadding.current)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.browse_recent_searches), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onClear) { Text(stringResource(R.string.browse_clear_all)) }
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
                IconButton(onClick = { onRemove(q) }) { Icon(Icons.Rounded.Close, stringResource(R.string.browse_clear), Modifier.size(18.dp)) }
                Icon(Icons.Rounded.NorthWest, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
            }
        }
    }
}

@Composable
private fun FilterBar(filters: ExploreFilters, vm: ExploreViewModel) {
    // One row: the genre picker (a sheet) then the content types, instead of a second endless row of genres.
    var genresOpen by remember { mutableStateOf(false) }
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "genre") { GenreChip(filters.genres.size) { genresOpen = true } }
        items(TypeFilter.entries, key = { it.name }) { t ->
            TagChip(stringResource(t.label), onClick = { vm.setType(t) }, selected = t == filters.type)
        }
    }
    if (genresOpen) {
        GenreSheet(
            genres = AllGenres.map { it to it },
            selected = filters.genres,
            onToggle = vm::toggleGenre,
            onClear = { filters.genres.forEach(vm::toggleGenre) },
            onDismiss = { genresOpen = false },
        )
    }
}

/** Title of what is shown, plus one menu for sort order and airing status. */
@Composable
/** [onRandom] is null while there is nothing to pick from. */
private fun ResultsHeader(filters: ExploreFilters, vm: ExploreViewModel, onRandom: (() -> Unit)?) {
    var menuOpen by remember { mutableStateOf(false) }
    val randomCd = stringResource(R.string.browse_random_cd)
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            filters.query.trim().takeIf { it.isNotEmpty() }?.let { stringResource(R.string.browse_results_for, it) }
                ?: filters.genres.takeIf { it.isNotEmpty() }?.joinToString(" + ") ?: filters.type.takeIf { it != TypeFilter.ALL }?.let { stringResource(it.label) } ?: stringResource(R.string.browse_all_anime),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        AssistChip(
            onClick = { onRandom?.invoke() },
            enabled = onRandom != null,
            label = { Text(stringResource(R.string.browse_random)) },
            leadingIcon = { Icon(Icons.Rounded.Casino, null, Modifier.size(18.dp)) },
            modifier = Modifier.padding(end = 8.dp).semantics { contentDescription = randomCd },
        )
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
                    if (filters.status == StatusFilter.ALL) stringResource(filters.sort.label) else "${stringResource(filters.sort.label)} · ${stringResource(filters.status.label)}",
                    style = MaterialTheme.typography.labelLarge,
                )
                Icon(Icons.Rounded.ArrowDropDown, null)
            }
            DropdownMenu(menuOpen, onDismissRequest = { menuOpen = false }) {
                MenuLabel(stringResource(R.string.browse_sort_menu))
                SortOrder.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(stringResource(s.label)) },
                        onClick = { vm.setSort(s); menuOpen = false },
                        trailingIcon = { if (s == filters.sort) Icon(Icons.Rounded.Check, null) },
                    )
                }
                HorizontalDivider()
                MenuLabel(stringResource(R.string.browse_status_menu))
                StatusFilter.entries.forEach { st ->
                    DropdownMenuItem(
                        text = { Text(stringResource(st.label)) },
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
private fun ResultsGrid(state: ExploreUiState, vm: ExploreViewModel, onAnimeClick: OpenAnime, topPad: Dp) {
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
        state.results.isEmpty() && !state.loadingMore && !state.kuramanimeLoading -> ResultsBranch.EMPTY
        else -> ResultsBranch.RESULTS
    }        else -> ResultsBranch.RESULTS
    }
    Crossfade(branch, animationSpec = tween(Motion.Medium), label = "exploreResults") { shown ->
    when (shown) {
        ResultsBranch.LOADING -> LazyVerticalGrid(
            columns = GridCells.Adaptive(108.dp),
            contentPadding = PaddingValues(start = 16.dp, top = topPad + 16.dp, end = 16.dp, bottom = 16.dp + LocalNavBarPadding.current),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            userScrollEnabled = false,
        ) { items(12) { PosterSkeleton() } }

        ResultsBranch.ERROR -> Box(Modifier.padding(top = topPad)) { state.error?.let { ErrorState(it, vm::reload) } }

        ResultsBranch.EMPTY -> Box(Modifier.padding(top = topPad)) { MessageState(
            icon = Icons.Rounded.SearchOff,
            title = stringResource(R.string.browse_no_results),
            message = stringResource(R.string.browse_no_results_hint),
            actionLabel = if (state.filters.activeCount > 0) stringResource(R.string.browse_clear_filters) else null,
            onAction = if (state.filters.activeCount > 0) vm::clearFilters else null,
        ) }

        ResultsBranch.RESULTS -> LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(108.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topPad + 12.dp, bottom = 24.dp + LocalNavBarPadding.current),
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
            if (state.kuramanimeLoading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
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
                        TextButton(onClick = vm::loadMore) { Text(stringResource(R.string.browse_load_failed_retry)) }
                    }
                }
            }
        }
    }
    }
}

private enum class ResultsBranch { LOADING, ERROR, EMPTY, RESULTS }
