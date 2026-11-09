package com.fuwanime.ui.manga

import com.fuwanime.ui.components.rememberDownloadGate
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import com.fuwanime.data.local.MangaDownloadEntity
import com.fuwanime.data.local.MangaDownloadState
import com.fuwanime.data.manga.MangaDownloadRepository
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.semantics
import java.text.DateFormat
import com.fuwanime.data.manga.ChapterLang
import com.fuwanime.data.manga.availableLanguages
import com.fuwanime.data.manga.effectiveLanguage
import com.fuwanime.data.manga.mergeChapters
import com.fuwanime.data.manga.sourceRank
import com.fuwanime.data.manga.numberKey
import com.fuwanime.data.manga.languageGroup
import androidx.compose.ui.platform.LocalConfiguration
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

/** Chapter numbers read so far, whichever source the read copy came from. */
fun readKeys(history: List<MangaHistoryEntity>, all: List<MangaChapter>): Set<String> {
    val byId = all.associateBy { it.id }
    return history.mapNotNullTo(HashSet()) { e -> byId[e.chapterId]?.let(::numberKey) }
}

/** The language of the most recently read chapter that is still listed, or null. */
fun historyLanguage(history: List<MangaHistoryEntity>, all: List<MangaChapter>): ChapterLang? {
    val byId = all.associateBy { it.id }
    return history.sortedByDescending { it.updatedAt }.firstNotNullOfOrNull { byId[it.chapterId] }?.let { languageGroup(it.language) }
}

/**
 * The chapter "Lanjut"/"Mulai baca" opens: the last one read if it is still listed, or the listed chapter with the same
 * number (it may now come from a better source; [all] knows the old one's number), else the first readable.
 */
fun resumeTarget(chapters: List<MangaChapter>, history: List<MangaHistoryEntity>, all: List<MangaChapter> = chapters): MangaChapter? {
    val readable = chapters.filter { it.externalUrl == null && it.pages > 0 }
    val byId = readable.associateBy { it.id }
    val byNumber = readable.associateBy { numberKey(it) }
    val allById = all.associateBy { it.id }
    return history.sortedByDescending { it.updatedAt }.firstNotNullOfOrNull { e ->
        byId[e.chapterId] ?: allById[e.chapterId]?.let(::numberKey)?.let { byNumber[it] }
    } ?: firstReadable(chapters)
}

data class MangaDetailState(
    val manga: Manga? = null,
    val chapters: List<MangaChapter>? = null,
    val error: UiText? = null,
    val chaptersError: UiText? = null,
    val history: List<MangaHistoryEntity> = emptyList(),
    val favorite: Boolean = false,
)

class MangaDetailViewModel(
    private val id: String,
    private val repo: MangaRepository,
    private val library: MangaLibrary,
    private val downloadRepo: MangaDownloadRepository,
) : ViewModel() {
    val downloads: StateFlow<Map<String, MangaDownloadEntity>> =
        downloadRepo.forManga(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun download(chapters: List<MangaChapter>, label: (MangaChapter) -> String, onQueued: (Int) -> Unit) {
        val manga = state.value.manga ?: return
        viewModelScope.launch { onQueued(downloadRepo.enqueue(manga, chapters, label)) }
    }

    fun deleteDownload(chapterId: String) = viewModelScope.launch { downloadRepo.delete(chapterId) }

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
                // Offline: a title with downloads still opens, listing the downloaded chapters.
                val saved = downloadRepo.offlineManga(id)
                remote.value = if (saved != null) Remote(manga = saved, chapters = downloadRepo.offline(id)) else Remote(error = e.friendlyText())
                return@launch
            }
            remote.value = Remote(manga = manga)
            try {
                // Every source's chapters, every language; the screen picks one language and the best source per chapter.
                remote.value = remote.value.copy(chapters = repo.mergedChapters(manga))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val offline = downloadRepo.offline(id)
                remote.value = if (offline.isNotEmpty()) remote.value.copy(chapters = offline) else remote.value.copy(chaptersError = e.friendlyText())
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
    val vm: MangaDetailViewModel = fuwaViewModel(key = "manga-$mangaId") { c, _ -> MangaDetailViewModel(mangaId, c.mangaRepository, c.mangaLibrary, c.mangaDownloads) }
    val state by vm.state.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    // Plain-context label: the download queue stores it for Library and the notification.
    val labelOf: (MangaChapter) -> String = { c ->
        c.number?.let { ctx.getString(R.string.manga_chapter_number, it) } ?: c.title?.takeIf { it.isNotBlank() } ?: ctx.getString(R.string.manga_chapter_oneshot)
    }
    val gate = rememberDownloadGate()
    val queue: (List<MangaChapter>) -> Unit = { list ->
        gate {
            vm.download(list, labelOf) { n ->
                if (n > 0) scope.launch { snackbar.showSnackbar(ctx.resources.getQuantityString(R.plurals.manga_download_queued, n, n)) }
            }
        }
    }

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
                val appLang = LocalConfiguration.current.locales[0].language
                // Until a chip is tapped, follow the language last read (else the app language).
                val lang = effectiveLanguage(chosen ?: historyLanguage(state.history, state.chapters.orEmpty()), available, appLang)
                // Everything is read in the app: chapters that only exist on an external site (or have no pages) are left out.
                val inApp = remember(state.chapters, lang) {
                    state.chapters?.let { all -> lang?.let { mergeChapters(all, it) }.orEmpty().filter { it.externalUrl == null && it.pages > 0 } }
                }
                val target = inApp?.let { resumeTarget(it, state.history, state.chapters.orEmpty()) }
                // Read marks also cover the same chapter number read from another source.
                val readNumbers = remember(state.history, state.chapters) { readKeys(state.history, state.chapters.orEmpty()) }
                fun isRead(c: MangaChapter) = c.id in readIds || numberKey(c) in readNumbers
                val resumed = target != null && isRead(target)
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
                            inApp?.size?.let { pluralStringResource(R.plurals.manga_detail_chapters_count, it, it) } ?: stringResource(R.string.manga_detail_chapters),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    val chapters = inApp
                    when {
                        state.chaptersError != null -> item(key = "chErr") { ErrorState(state.chaptersError!!, vm::load) }
                        // Searching the other sources and loading their chapter lists can take a few seconds.
                        chapters == null -> item(key = "chLoading") {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp).semantics(mergeDescendants = true) {},
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(stringResource(R.string.manga_detail_finding_sources), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
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
                            item(key = "chDownload") {
                                Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp), contentAlignment = Alignment.CenterEnd) {
                                    TextButton(onClick = { queue(shown) }) {
                                        Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            ranges.getOrNull(rangeIndex)?.let { stringResource(R.string.manga_download_range, rangeLabel(chapters, it)) }
                                                ?: stringResource(R.string.manga_download_all),
                                        )
                                    }
                                }
                            }
                            // Three boxed cells per row, like the chapter grids on Indonesian manga sites.
                            items(shown.chunked(3), key = { it.first().id }) { row ->
                                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { c ->
                                        ChapterCell(
                                            c, read = isRead(c), current = resumed && c.id == target?.id, Modifier.weight(1f),
                                            download = downloads[c.id],
                                            onDownload = { queue(listOf(c)) },
                                            onDelete = { vm.deleteDownload(c.id) },
                                        ) { onRead(readId, c.id) }
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
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
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

@OptIn(ExperimentalFoundationApi::class)
/** One boxed chapter in the grid: number and date; read chapters are dimmed, the one "Lanjut" opens is outlined. */
@Composable
private fun ChapterCell(
    c: MangaChapter,
    read: Boolean,
    current: Boolean,
    modifier: Modifier,
    download: MangaDownloadEntity?,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dates = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    var menuOpen by remember { mutableStateOf(false) }
    val canDownload = download == null || download.state == MangaDownloadState.FAILED.name
    val actionLabel = stringResource(if (canDownload) R.string.manga_download else R.string.manga_download_delete)
    val act = { if (canDownload) onDownload() else onDelete() }
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
            // TalkBack gets the long-press action too.
            .semantics { customActions = listOf(CustomAccessibilityAction(actionLabel) { act(); true }) },
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (c.publishAt > 0) dates.format(Date(c.publishAt)) else c.language.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                download?.let { DownloadMark(it) }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text(actionLabel) }, onClick = { menuOpen = false; act() })
        }
    }
}

/** Small download state mark in a chapter cell. */
@Composable
private fun DownloadMark(d: MangaDownloadEntity) {
    val tint = MaterialTheme.colorScheme.primary
    val m = Modifier.size(14.dp)
    when (d.state) {
        MangaDownloadState.RUNNING.name -> {
            val running = stringResource(R.string.manga_download_state_running)
            CircularProgressIndicator(
                progress = { if (d.pageCount > 0) d.pagesDone.toFloat() / d.pageCount else 0f },
                modifier = m.semantics { contentDescription = running },
                strokeWidth = 2.dp,
            )
        }
        MangaDownloadState.DONE.name -> Icon(Icons.Rounded.DownloadDone, stringResource(R.string.manga_download_state_done), m, tint = tint)
        MangaDownloadState.FAILED.name -> Icon(Icons.Rounded.ErrorOutline, stringResource(R.string.manga_download_state_failed), m, tint = MaterialTheme.colorScheme.error)
        else -> Icon(Icons.Rounded.Download, stringResource(R.string.manga_download_state_queued), m, tint = tint)
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
