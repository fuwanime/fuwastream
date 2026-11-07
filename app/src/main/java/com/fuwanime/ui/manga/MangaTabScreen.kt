package com.fuwanime.ui.manga

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
                mangaHomeItems(home, topPad = 0.dp, onRetry = homeVm::refresh, onMangaClick = onMangaClick, onRead = onRead)
            }
        }
    }
}
