package com.fuwanime.ui.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fuwanime.R
import com.fuwanime.data.local.MangaFavoriteEntity
import com.fuwanime.data.local.MangaHistoryEntity
import com.fuwanime.data.manga.MangaLibrary
import com.fuwanime.ui.components.LocalNavBarPadding
import com.fuwanime.ui.components.MessageState
import com.fuwanime.ui.components.NetImage
import com.fuwanime.util.fuwaViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MangaShelfViewModel(private val library: MangaLibrary) : ViewModel() {
    val continueReading: StateFlow<List<MangaHistoryEntity>?> =
        library.continueReading.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val favorites: StateFlow<List<MangaFavoriteEntity>?> =
        library.favorites.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun remove(mangaId: String) = viewModelScope.launch { library.removeManga(mangaId) }
}

/** Koleksi › Manga: what the user is reading, then their favourite titles. */
@Composable
fun MangaLibraryTab(onOpen: (mangaId: String) -> Unit, onRead: (mangaId: String, chapterId: String) -> Unit) {
    val vm: MangaShelfViewModel = fuwaViewModel { c, _ -> MangaShelfViewModel(c.mangaLibrary) }
    val reading by vm.continueReading.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val r = reading ?: return
    val f = favorites ?: return
    if (r.isEmpty() && f.isEmpty()) {
        MessageState(Icons.AutoMirrored.Rounded.MenuBook, stringResource(R.string.manga_library_empty_title), message = stringResource(R.string.manga_library_empty_body))
        return
    }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 8.dp, bottom = 16.dp + LocalNavBarPadding.current)) {
        if (r.isNotEmpty()) {
            item(key = "readingTitle") { SectionTitle(stringResource(R.string.manga_home_continue)) }
            items(r, key = { "r-" + it.mangaId }) { h ->
                ShelfRow(h.mangaTitle, h.coverUrl, subtitle = stringResource(R.string.manga_library_progress, h.chapterLabel, h.page + 1, h.pageCount), onClick = { onRead(h.mangaId, h.chapterId) }) {
                    IconButton(onClick = { vm.remove(h.mangaId) }) { Icon(Icons.Rounded.Close, stringResource(R.string.manga_library_remove)) }
                }
            }
        }
        if (f.isNotEmpty()) {
            item(key = "favTitle") { SectionTitle(stringResource(R.string.manga_library_favorites)) }
            items(f, key = { "f-" + it.mangaId }) { fav -> ShelfRow(fav.title, fav.coverUrl, subtitle = null, onClick = { onOpen(fav.mangaId) }) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun ShelfRow(title: String, cover: String?, subtitle: String?, onClick: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NetImage(cover.orEmpty(), Modifier.width(52.dp).aspectRatio(0.7f).clip(RoundedCornerShape(8.dp)), contentDescription = null)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        }
        trailing()
    }
}
