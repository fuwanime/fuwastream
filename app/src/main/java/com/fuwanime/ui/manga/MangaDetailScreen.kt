package com.fuwanime.ui.manga

import com.fuwanime.data.manga.isKomikuId
import com.fuwanime.data.manga.isShinigamiId
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fuwanime.R
import com.fuwanime.data.local.MangaHistoryEntity
import com.fuwanime.data.manga.Manga
import com.fuwanime.data.manga.MangaChapter
import com.fuwanime.data.manga.MangaLibrary
import com.fuwanime.data.manga.MangaRepository
import com.fuwanime.data.manga.firstReadable
import com.fuwanime.ui.components.ErrorState
import com.fuwanime.ui.components.MessageState
import com.fuwanime.ui.components.NetImage
import com.fuwanime.ui.components.TagChip
import com.fuwanime.ui.components.readableWidth
import com.fuwanime.util.UiText
import com.fuwanime.util.friendlyText
import com.fuwanime.util.fuwaViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The chapter "Lanjut"/"Mulai baca" opens: the last one read if it is still listed, else the first readable. */
fun resumeTarget(chapters: List<MangaChapter>, history: List<MangaHistoryEntity>): MangaChapter? {
    val byId = chapters.associateBy { it.id }
    return history.sortedByDescending { it.updatedAt }.firstNotNullOfOrNull { byId[it.chapterId]?.takeIf { c -> c.externalUrl == null && c.pages > 0 } }
        ?: firstReadable(chapters)
}

data class MangaDetailState(
    val manga: Manga? = null,
    val chapters: List<MangaChapter>? = null,
    val error: UiText? = null,
    val chaptersError: UiText? = null,
    val history: List<MangaHistoryEntity> = emptyList(),
    val favorite: Boolean = false,
    /** Set when the chapters come from the same title on Shinigami (MangaDex had nothing readable here). */
    val readFrom: Manga? = null,
)

class MangaDetailViewModel(private val id: String, private val repo: MangaRepository, private val library: MangaLibrary) : ViewModel() {
    private data class Remote(
        val manga: Manga? = null,
        val chapters: List<MangaChapter>? = null,
        val error: UiText? = null,
        val chaptersError: UiText? = null,
        val readFrom: Manga? = null,
    )

    private val remote = MutableStateFlow(Remote())

    val state: StateFlow<MangaDetailState> =
        combine(remote, library.historyFor(id), library.isFavorite(id)) { r, h, f ->
            MangaDetailState(r.manga, r.chapters, r.error, r.chaptersError, h, f, r.readFrom)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MangaDetailState())

    init { load() }

    fun load() {
        remote.value = Remote()
        viewModelScope.launch {
            val manga = try { repo.detail(id) } catch (e: CancellationException) { throw e } catch (e: Exception) {
                remote.value = Remote(error = e.friendlyText()); return@launch
            }
            remote.value = Remote(manga = manga)
            try {
                val chapters = repo.chapters(id)
                val inApp = chapters.any { it.externalUrl == null && it.pages > 0 }
                // Nothing readable here (licensed or not uploaded): read the same title from Shinigami, still in the app.
                val alt = if (!inApp && !isShinigamiId(id) && !isKomikuId(id)) runCatching { repo.findOnShinigami(manga) }.getOrNull() else null
                remote.value = if (alt != null) {
                    remote.value.copy(chapters = repo.chapters(alt.id), readFrom = alt)
                } else {
                    remote.value.copy(chapters = chapters)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                remote.value = remote.value.copy(chaptersError = e.friendlyText())
            }
        }
    }

    fun toggleFavorite() {
        val manga = state.value.manga ?: return
        viewModelScope.launch { library.setFavorite(manga, !state.value.favorite) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MangaDetailRoute(mangaId: String, onBack: () -> Unit, onRead: (mangaId: String, chapterId: String) -> Unit) {
    val vm: MangaDetailViewModel = fuwaViewModel(key = "manga-$mangaId") { c, _ -> MangaDetailViewModel(mangaId, c.mangaRepository, c.mangaLibrary) }
    val state by vm.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        val manga = state.manga
        when {
            state.error != null -> Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) { ErrorState(state.error!!, vm::load) }
            manga == null -> Unit
            else -> {
                // Reading (and its history) belongs to the title the chapters came from.
                val readId = state.readFrom?.id ?: mangaId
                val readIds = state.history.mapTo(HashSet()) { it.chapterId }
                val target = state.chapters?.let { resumeTarget(it, state.history) }
                val resumed = target != null && target.id in readIds
                LazyColumn(
                    Modifier.fillMaxSize().readableWidth(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item(key = "header") { Header(manga, state.favorite, vm::toggleFavorite) }
                    item(key = "read") {
                        Button(
                            onClick = { target?.let { onRead(readId, it.id) } },
                            enabled = target != null,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.MenuBook, null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when {
                                    target == null -> stringResource(R.string.manga_detail_start)
                                    resumed -> stringResource(R.string.manga_detail_resume, chapterLabel(target))
                                    else -> stringResource(R.string.manga_detail_start)
                                },
                            )
                        }
                    }
                    item(key = "synopsis") { Synopsis(manga) }
                    if (manga.tags.isNotEmpty()) {
                        item(key = "tags") {
                            FlowRow(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                manga.tags.forEach { TagChip(it, onClick = {}) }
                            }
                        }
                    }
                    item(key = "chaptersTitle") {
                        Text(
                            state.chapters?.count { it.externalUrl == null && it.pages > 0 }?.let { pluralStringResource(R.plurals.manga_detail_chapters_count, it, it) } ?: stringResource(R.string.manga_detail_chapters),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    // Everything is read in the app: chapters that only exist on an external site (or have no pages) are left out.
                    val chapters = state.chapters?.filter { it.externalUrl == null && it.pages > 0 }
                    when {
                        state.chaptersError != null -> item(key = "chErr") { ErrorState(state.chaptersError!!, vm::load) }
                        chapters == null -> Unit
                        chapters.isEmpty() -> item(key = "chEmpty") {
                            MessageState(Icons.AutoMirrored.Rounded.MenuBook, stringResource(R.string.manga_detail_no_chapters))
                        }
                        else -> items(chapters, key = { it.id }) { c ->
                            ChapterRow(c, read = c.id in readIds) { onRead(readId, c.id) }
                        }
                    }
                    item(key = "credit") {
                        Text(
                            stringResource(
                                when {
                                    isShinigamiId(mangaId) -> R.string.manga_credit_shinigami
                                    isKomikuId(mangaId) || state.readFrom?.id?.let(::isKomikuId) == true -> R.string.manga_credit_komiku
                                    state.readFrom != null -> R.string.manga_credit_both
                                    else -> R.string.manga_credit_mangadex
                                },
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp).navigationBarsPadding(),
                        )
                    }
                }
            }
        }
        IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(4.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.mine_back))
        }
    }
}

/** "Ch. 12", or the chapter title for a oneshot without a number. */
@Composable
internal fun chapterLabel(c: MangaChapter): String =
    c.number?.let { stringResource(R.string.manga_chapter_number, it) } ?: c.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.manga_chapter_oneshot)

@Composable
private fun Header(manga: Manga, favorite: Boolean, onToggleFavorite: () -> Unit) {
    val status = mangaStatusLabel()(manga.status)
    Row(Modifier.statusBarsPadding().padding(start = 20.dp, end = 12.dp, top = 52.dp), verticalAlignment = Alignment.Bottom) {
        NetImage(
            manga.coverUrl.orEmpty(),
            Modifier.width(120.dp).aspectRatio(0.7f).clip(RoundedCornerShape(12.dp)),
            contentDescription = manga.title,
        )
        Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(manga.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis)
            manga.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            val meta = listOfNotNull(status, manga.year?.toString()).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                stringResource(if (favorite) R.string.manga_favorite_remove else R.string.manga_favorite_add),
                tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Synopsis(manga: Manga) {
    if (manga.description.isBlank()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).clickable { expanded = !expanded }) {
        Text(
            manga.description,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(if (expanded) R.string.manga_detail_less else R.string.manga_detail_more),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ChapterRow(c: MangaChapter, read: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(chapterLabel(c), c.title?.takeIf { it.isNotBlank() && c.number != null }).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge,
                color = if (read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(c.language.uppercase(), c.group).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            read -> Icon(Icons.Rounded.CheckCircle, stringResource(R.string.manga_chapter_read), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}
