package com.fuwanime.ui.manga

import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import com.fuwanime.data.manga.isKomikuId
import com.fuwanime.data.manga.isShinigamiId
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.semantics
import java.text.DateFormat
import java.util.Locale
import com.fuwanime.data.manga.ChapterLang
import com.fuwanime.data.manga.availableLanguages
import com.fuwanime.data.manga.effectiveLanguage
import com.fuwanime.data.manga.mergeChapters
import com.fuwanime.data.manga.sourceRank
import java.util.Date
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
)

class MangaDetailViewModel(private val id: String, private val repo: MangaRepository, private val library: MangaLibrary) : ViewModel() {
    private data class Remote(
        val manga: Manga? = null,
        val chapters: List<MangaChapter>? = null,
        val error: UiText? = null,
        val chaptersError: UiText? = null,
    )

    private val remote = MutableStateFlow(Remote())

    val state: StateFlow<MangaDetailState> =
        combine(remote, library.historyFor(id), library.isFavorite(id)) { r, h, f ->
            MangaDetailState(r.manga, r.chapters, r.error, r.chaptersError, h, f)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MangaDetailState())

    init { load() }

    fun load() {
        remote.value = Remote()
        repo.clearMerged(id)
        viewModelScope.launch {
            val manga = try { repo.detail(id) } catch (e: CancellationException) { throw e } catch (e: Exception) {
                remote.value = Remote(error = e.friendlyText()); return@launch
            }
            remote.value = Remote(manga = manga)
            try {
                // Every source's chapters, every language; the screen picks one language and the best source per chapter.
                remote.value = remote.value.copy(chapters = repo.mergedChapters(manga))
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
fun MangaDetailRoute(mangaId: String, onBack: () -> Unit, onRead: (mangaId: String, chapterId: String) -> Unit, onGenre: (String) -> Unit) {
    val vm: MangaDetailViewModel = fuwaViewModel(key = "manga-$mangaId") { c, _ -> MangaDetailViewModel(mangaId, c.mangaRepository, c.mangaLibrary) }
    val state by vm.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        val manga = state.manga
        when {
            state.error != null -> Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) { ErrorState(state.error!!, vm::load) }
            manga == null -> Unit
            else -> {
                // History stays under the opened title, whichever source a chapter comes from.
                val readId = mangaId
                val readIds = state.history.mapTo(HashSet()) { it.chapterId }
                val available = remember(state.chapters) { state.chapters?.let(::availableLanguages).orEmpty() }
                var chosen by rememberSaveable(mangaId) { mutableStateOf<ChapterLang?>(null) }
                val lang = effectiveLanguage(chosen, available, Locale.getDefault().language)
                // Everything is read in the app: chapters that only exist on an external site (or have no pages) are left out.
                val inApp = remember(state.chapters, lang) {
                    state.chapters?.let { all -> lang?.let { mergeChapters(all, it) }.orEmpty().filter { it.externalUrl == null && it.pages > 0 } }
                }
                val target = inApp?.let { resumeTarget(it, state.history) }
                val resumed = target != null && target.id in readIds
                val sources = remember(state.chapters) {
                    state.chapters.orEmpty().map { sourceRank(it.id) }.distinct().sorted().map { SOURCE_NAMES[it] }
                }
                val ranges = remember(inApp) { inApp?.let { chapterRanges(it) }.orEmpty() }
                var picked by rememberSaveable(mangaId) { mutableIntStateOf(-1) }
                // Until the user picks a range, open the one holding the chapter "Lanjut" would read.
                val rangeIndex = if (picked in ranges.indices) picked else {
                    val at = inApp?.indexOfFirst { it.id == target?.id } ?: -1
                    ranges.indexOfFirst { at in it }.coerceAtLeast(0)
                }
                LazyColumn(
                    Modifier.fillMaxSize().readableWidth(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item(key = "header") { Header(manga, state.favorite, vm::toggleFavorite) }
                    item(key = "info") {
                        val own = when {
                            isShinigamiId(mangaId) -> "Shinigami"
                            isKomikuId(mangaId) -> "Komiku"
                            else -> "MangaDex"
                        }
                        InfoTable(manga, sources.ifEmpty { listOf(own) }.joinToString(" + "), inApp?.maxOfOrNull { it.publishAt }?.takeIf { it > 0 })
                    }
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
                                manga.tags.forEach { TagChip(it, onClick = { onGenre(it) }, selected = true) }
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
                    val chapters = inApp
                    when {
                        state.chaptersError != null -> item(key = "chErr") { ErrorState(state.chaptersError!!, vm::load) }
                        chapters == null -> Unit
                        chapters.isEmpty() -> item(key = "chEmpty") {
                            MessageState(Icons.AutoMirrored.Rounded.MenuBook, stringResource(R.string.manga_detail_no_chapters))
                        }
                        else -> {
                            if (available.size >= 2) {
                                item(key = "chLangs") {
                                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(available) { l ->
                                            TagChip(stringResource(LANG_LABELS.getValue(l)), onClick = { chosen = l; picked = -1 }, selected = l == lang)
                                        }
                                    }
                                }
                            }
                            if (ranges.isNotEmpty()) {
                                item(key = "chRanges") {
                                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        itemsIndexed(ranges) { i, r -> TagChip(rangeLabel(chapters, r), onClick = { picked = i }, selected = i == rangeIndex) }
                                    }
                                }
                            }
                            val shown = ranges.getOrNull(rangeIndex)?.let { chapters.slice(it) } ?: chapters
                            // Three boxed cells per row, like the chapter grids on Indonesian manga sites.
                            items(shown.chunked(3), key = { it.first().id }) { row ->
                                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { c ->
                                        ChapterCell(c, read = c.id in readIds, current = resumed && c.id == target?.id, Modifier.weight(1f)) { onRead(readId, c.id) }
                                    }
                                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                    item(key = "credit") {
                        Text(
                            if (sources.size > 1) {
                                stringResource(R.string.manga_credit_merged, sources.joinToString(" + "))
                            } else {
                                stringResource(
                                    when {
                                        isShinigamiId(mangaId) -> R.string.manga_credit_shinigami
                                        isKomikuId(mangaId) -> R.string.manga_credit_komiku
                                        else -> R.string.manga_credit_mangadex
                                    },
                                )
                            },
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

private val SOURCE_NAMES = listOf("Shinigami", "Komiku", "MangaDex") // indexed by sourceRank
private val LANG_LABELS = mapOf(ChapterLang.ID to R.string.manga_lang_id, ChapterLang.EN to R.string.manga_lang_en, ChapterLang.RAW to R.string.manga_lang_raw)

/** "Ch. 12", or the chapter title for a oneshot without a number. */
@Composable
internal fun chapterLabel(c: MangaChapter): String =
    c.number?.let { stringResource(R.string.manga_chapter_number, it) } ?: c.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.manga_chapter_oneshot)

@Composable
private fun Header(manga: Manga, favorite: Boolean, onToggleFavorite: () -> Unit) {
    Row(Modifier.statusBarsPadding().padding(start = 20.dp, end = 12.dp, top = 52.dp), verticalAlignment = Alignment.Bottom) {
        NetImage(
            manga.coverUrl.orEmpty(),
            Modifier.width(120.dp).aspectRatio(0.7f).clip(RoundedCornerShape(12.dp)),
            contentDescription = manga.title,
        )
        Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(manga.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 4, overflow = TextOverflow.Ellipsis)
            manga.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            manga.type?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
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

/** Status, type, year, author, source and last update in a framed two-column table. */
@Composable
private fun InfoTable(manga: Manga, source: String, updated: Long?) {
    val dates = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val rows = listOfNotNull(
        mangaStatusLabel()(manga.status)?.let { R.string.manga_info_status to it },
        manga.type?.let { R.string.manga_info_type to it },
        manga.year?.let { R.string.manga_info_year to it.toString() },
        manga.author?.let { R.string.manga_info_author to it },
        R.string.manga_info_source to source,
        updated?.let { R.string.manga_info_updated to dates.format(Date(it)) },
    )
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp)).border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp)),
    ) {
        rows.forEachIndexed { i, (label, value) ->
            if (i > 0) HorizontalDivider(color = colors.outlineVariant)
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(stringResource(label), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp))
                Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** One boxed chapter in the grid: number and date; read chapters are dimmed, the one "Lanjut" opens is outlined. */
@Composable
private fun ChapterCell(c: MangaChapter, read: Boolean, current: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dates = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = colors.surfaceContainerLow,
        border = BorderStroke(if (current) 1.5.dp else 1.dp, if (current) colors.primary else colors.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chapterLabel(c),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (read) colors.onSurfaceVariant else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (read) Icon(Icons.Rounded.CheckCircle, stringResource(R.string.manga_chapter_read), Modifier.size(14.dp), tint = colors.primary)
            }
            Text(
                if (c.publishAt > 0) dates.format(Date(c.publishAt)) else c.language.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** Index ranges of [size] chapters for the range chips; empty when everything fits on one page. */
fun chapterRanges(chapters: List<MangaChapter>, size: Int = 100): List<IntRange> =
    if (chapters.size <= size) emptyList() else chapters.indices.chunked(size).map { it.first()..it.last() }

/** "1–100" from the chapter numbers at both ends of [range] (oneshots without a number fall back to their position). */
fun rangeLabel(chapters: List<MangaChapter>, range: IntRange): String {
    fun n(i: Int) = chapters[i].number ?: (i + 1).toString()
    return "${n(range.first)}–${n(range.last)}"
}
