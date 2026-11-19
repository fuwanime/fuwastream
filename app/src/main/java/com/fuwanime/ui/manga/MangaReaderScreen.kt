package com.fuwanime.ui.manga

import com.fuwanime.data.manga.isKomikuId
import com.fuwanime.data.manga.isShinigamiId
import com.fuwanime.data.manga.isKakaMangaId
import com.fuwanime.data.manga.isMangaParkId
import com.fuwanime.data.manga.isWebtoonId
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import android.view.WindowManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.fuwanime.R
import com.fuwanime.data.local.MangaDownloadState
import com.fuwanime.data.local.ReaderMode
import com.fuwanime.data.ProfileRepository
import com.fuwanime.data.ProfileRules
import com.fuwanime.data.local.SettingsStore
import com.fuwanime.data.manga.ChapterTranslationState
import com.fuwanime.data.manga.MangaChapterTranslator
import com.fuwanime.data.manga.MangaTranslateWorker
import com.fuwanime.data.manga.Manga
import com.fuwanime.data.manga.MangaChapter
import com.fuwanime.data.manga.MangaDownloadRepository
import com.fuwanime.data.manga.MangaLibrary
import com.fuwanime.data.manga.MangaRepository
import com.fuwanime.data.manga.MangaStatus
import com.fuwanime.data.manga.mangaPageCandidates
import com.fuwanime.data.manga.isKomikuId
import com.fuwanime.data.manga.isShinigamiId
import com.fuwanime.data.manga.mangaPageCandidates
import com.fuwanime.data.manga.mergeChapters
import com.fuwanime.data.manga.nextChapter
import com.fuwanime.data.manga.previousChapter
import com.fuwanime.data.manga.readerLanguage
import com.fuwanime.data.manga.withChapter
import com.fuwanime.ui.components.ErrorState
import com.fuwanime.ui.components.SkeletonBox
import com.fuwanime.util.UiText
import com.fuwanime.util.UiTextException
import com.fuwanime.util.friendlyText
import com.fuwanime.util.text
import com.fuwanime.util.fuwaViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive

fun readerChapter(chapters: List<MangaChapter>, chapterId: String, savedLabel: String?): MangaChapter =
    chapters.firstOrNull { it.id == chapterId }
        ?: MangaChapter(chapterId, number = null, title = savedLabel, language = "", group = null, pages = 0, externalUrl = null, publishAt = 0)

fun readerStartIndex(savedPage: Int?, pageCount: Int): Int =
    if (pageCount <= 0) 0 else (savedPage ?: 0).coerceIn(0, pageCount - 1)

fun shouldAdvanceReaderOnTap(currentPage: Int, pageCount: Int, hasNext: Boolean): Boolean =
    hasNext && pageCount > 0 && currentPage >= pageCount - 1

internal data class ProgressiveTranslationSnapshot(
    val pages: List<String>,
    val translatedCount: Int,
)

/** Keeps page positions stable while replacing only translations that are already cached. */
internal fun progressiveTranslationSnapshot(
    originalPages: List<String>,
    cachedPages: List<String?>,
): ProgressiveTranslationSnapshot {
    var translatedCount = 0
    val pages = originalPages.mapIndexed { index, original ->
        cachedPages.getOrNull(index)?.also { translatedCount++ } ?: original
    }
    return ProgressiveTranslationSnapshot(pages, translatedCount)
}

data class ReaderState(
    val manga: Manga? = null,
    val chapters: List<MangaChapter> = emptyList(),
    val chapter: MangaChapter? = null,
    val pages: List<String>? = null,
    val startPage: Int = 0,
    val error: UiText? = null,
    val requestedId: String = "",
    val generation: Int = 0,
)

class MangaReaderViewModel(
    private val mangaId: String,
    firstChapterId: String,
    private val repo: MangaRepository,
    private val library: MangaLibrary,
    private val settings: SettingsStore,
    private val downloads: MangaDownloadRepository,
    private val translator: MangaChapterTranslator,
    private val appScope: kotlinx.coroutines.CoroutineScope,
    private val cacheDir: java.io.File,
    private val profile: ProfileRepository? = null,
    /** Updates the launcher widget and shortcut after progress is stored (saves are debounced to about one a second). */
    private val refreshHomeSurfaces: () -> Unit = {},
    private val appContext: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    private val _translation = MutableStateFlow(ChapterTranslationState())
    val translation: StateFlow<ChapterTranslationState> = _translation.asStateFlow()

    private var translationWorkJob: kotlinx.coroutines.Job? = null

    val translationEnabled: StateFlow<Boolean> = settings.mangaTranslate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val autoScrollEnabled: StateFlow<Boolean> = settings.mangaAutoScroll
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val keepScreenOn: StateFlow<Boolean> = settings.mangaKeepScreenOn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setKeepScreenOn(enabled: Boolean) = viewModelScope.launch { settings.setMangaKeepScreenOn(enabled) }

    val readerMode: StateFlow<ReaderMode> = library.preference(mangaId).map { pref ->
        pref?.readerMode?.let { raw -> ReaderMode.entries.firstOrNull { it.name == raw } } ?: ReaderMode.WEBTOON
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderMode.WEBTOON)

    init { open(firstChapterId) }

    private fun errorText(e: Throwable): UiText = when {
        e is UiTextException -> e.text
        else -> e.friendlyText()
    }

    /** Translation failures are localised; raw IsManga/internal exception text must never reach the reader. */
    private fun translationErrorText(e: Throwable): UiText {
        if (e is UiTextException) return e.text
        val msg = e.message.orEmpty()
        return when {
            msg.contains("HTTP 4", ignoreCase = true) ||
                msg.contains("Gagal mengunduh halaman", ignoreCase = true) ||
                msg.contains("Halaman kosong", ignoreCase = true) ->
                UiText(R.string.manga_reader_translate_image_failed)
            msg.contains("decode", ignoreCase = true) ||
                msg.contains("membaca dimensi gambar", ignoreCase = true) ||
                msg.contains("mengompres gambar", ignoreCase = true) ->
                UiText(R.string.manga_reader_translate_decode_failed)
            msg.contains("Kuota habis", ignoreCase = true) ||
                msg.contains("Antrian", ignoreCase = true) ||
                msg.contains("throttle", ignoreCase = true) ||
                msg.contains("GPU", ignoreCase = true) ||
                msg.contains("Timeout", ignoreCase = true) ->
                UiText(R.string.manga_reader_translate_busy)
            else -> UiText(R.string.manga_reader_translate_failed)
        }
    }

    private fun progressivePages(chapterId: String, originalPages: List<String>): ProgressiveTranslationSnapshot =
        progressiveTranslationSnapshot(
            originalPages = originalPages,
            cachedPages = translator.cachedChapterPageSlots(cacheDir, chapterId, originalPages.size),
        )

    fun open(chapterId: String) {
        if (_state.value.requestedId != chapterId) {
            translationWorkJob?.cancel()
            _translation.value = ChapterTranslationState()
        }
        _state.value = _state.value.copy(chapter = null, pages = null, error = null, requestedId = chapterId)
        viewModelScope.launch {
            try {
                val pagesJob = async { runCatching { downloads.localPages(chapterId) ?: repo.pages(chapterId) } }
                val (manga, chapters) = try {
                    val manga = _state.value.manga ?: repo.detail(mangaId)
                    manga to _state.value.chapters.ifEmpty {
                        val all = repo.mergedChapters(manga)
                        val merged = readerLanguage(all, chapterId, repo.language())?.let { mergeChapters(all, it) } ?: all
                        all.firstOrNull { it.id == chapterId }?.let { withChapter(merged, it) } ?: merged
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val row = downloads.row(chapterId)?.takeIf { it.state == MangaDownloadState.DONE.name } ?: throw e
                    (_state.value.manga ?: Manga(row.mangaId, row.mangaTitle, row.coverUrl, "", emptyList(), MangaStatus.UNKNOWN, null, null)) to
                        downloads.offline(mangaId)
                }
                val saved = library.historyFor(mangaId).first().firstOrNull { it.chapterId == chapterId }
                val chapter = readerChapter(chapters, chapterId, saved?.chapterLabel)
                val pages = pagesJob.await().getOrThrow()
                _state.value = ReaderState(manga, chapters, chapter, pages, readerStartIndex(saved?.page, pages.size), requestedId = chapterId, generation = _state.value.generation)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = errorText(e))
            }
        }
    }

    fun refreshPages() {
        val chapter = _state.value.chapter ?: return
        _translation.value = ChapterTranslationState()
        viewModelScope.launch {
            try {
                val pages = repo.pages(chapter.id)
                _state.value = _state.value.copy(pages = pages, generation = _state.value.generation + 1)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = errorText(e))
            }
        }
    }

    fun saveProgress(chapter: MangaChapter, page: Int, pageCount: Int, chapterLabel: String) {
        val manga = _state.value.manga ?: return
        appScope.launch {
            library.saveProgress(manga, chapter, chapterLabel, page, pageCount)
            refreshHomeSurfaces()
            // Saved on every page turn; the quest counts each chapter id only once.
            if (ProfileRules.chapterRead(page, pageCount)) profile?.onChapterRead(chapter.id)
        }
    }

    fun setReaderMode(mode: ReaderMode) = viewModelScope.launch {
        library.updatePreference(mangaId) { it.copy(readerMode = mode.name) }
    }

    fun setTranslationEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setMangaTranslate(enabled)
        if (!enabled) _translation.value = ChapterTranslationState()
    }

    fun setAutoScrollEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setMangaAutoScroll(enabled)
    }

    fun translateCurrentChapter() {
        val chapter = _state.value.chapter ?: return
        if (_translation.value.running) return
        viewModelScope.launch {
            val pages = _state.value.pages ?: return@launch
            val cached = translator.cachedChapterPages(cacheDir, chapter.id, pages.size)
            if (cached != null) {
                _translation.value = ChapterTranslationState(running = false, done = cached.size, total = cached.size, pages = cached)
                settings.setMangaTranslate(true)
                return@launch
            }
            val progressive = progressivePages(chapter.id, pages)
            _translation.value = ChapterTranslationState(
                running = true,
                done = progressive.translatedCount,
                total = pages.size,
                pages = progressive.pages,
            )
            MangaTranslateWorker.enqueue(
                appContext,
                chapter.id,
                _state.value.manga?.title.orEmpty(),
                chapter.number?.let { appContext.getString(R.string.manga_chapter_number, it) }
                    ?: chapter.title.orEmpty(),
            )
            settings.setMangaTranslate(true)
            watchTranslationWork(chapter.id, pages.size)
        }
    }

    /** Mirrors the foreground notification's WorkManager state while the reader is open. */
    private fun watchTranslationWork(chapterId: String, pageCount: Int) {
        translationWorkJob?.cancel()
        translationWorkJob = viewModelScope.launch {
            var observedWorkId: java.util.UUID? = null
            WorkManager.getInstance(appContext)
                .getWorkInfosForUniqueWorkFlow(MangaTranslateWorker.uniqueName(chapterId))
                .collect { infos ->
                    val active = infos.firstOrNull {
                        it.state == WorkInfo.State.ENQUEUED ||
                            it.state == WorkInfo.State.BLOCKED ||
                            it.state == WorkInfo.State.RUNNING
                    }
                    if (active != null) observedWorkId = active.id
                    val work = active ?: observedWorkId?.let { id -> infos.firstOrNull { it.id == id } }
                        ?: return@collect
                    if (_state.value.chapter?.id != chapterId) return@collect
                    val originalPages = _state.value.pages ?: return@collect
                    val done = work.progress.getInt(MangaTranslateWorker.KEY_DONE, 0)
                    val total = work.progress.getInt(MangaTranslateWorker.KEY_TOTAL, pageCount)
                    val progressive = progressivePages(chapterId, originalPages)
                    val ready = maxOf(done, progressive.translatedCount)
                    when (work.state) {
                        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, WorkInfo.State.RUNNING ->
                            _translation.update {
                                it.copy(
                                    running = true,
                                    done = ready,
                                    total = total,
                                    pages = progressive.pages,
                                    error = null,
                                )
                            }
                        WorkInfo.State.SUCCEEDED -> {
                            val cached = translator.cachedChapterPages(cacheDir, chapterId, pageCount)
                            if (cached != null) {
                                _translation.value = ChapterTranslationState(
                                    running = false,
                                    done = cached.size,
                                    total = cached.size,
                                    pages = cached,
                                )
                            } else {
                                _translation.update {
                                    it.copy(running = false, done = ready, total = total, pages = progressive.pages)
                                }
                            }
                            translationWorkJob?.cancel()
                        }
                        WorkInfo.State.FAILED -> {
                            _translation.update {
                                it.copy(
                                    running = false,
                                    done = ready,
                                    total = total,
                                    pages = progressive.pages,
                                    error = if (ready > 0) {
                                        UiText(R.string.manga_reader_translate_partial_failed, listOf(ready, total))
                                    } else {
                                        UiText(R.string.manga_reader_translate_failed)
                                    },
                                )
                            }
                            translationWorkJob?.cancel()
                        }
                        WorkInfo.State.CANCELLED -> {
                            _translation.update {
                                it.copy(running = false, done = ready, total = total, pages = progressive.pages)
                            }
                            translationWorkJob?.cancel()
                        }
                    }
                }
        }
    }

    fun showOriginal() { _translation.value = ChapterTranslationState() }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(FlowPreview::class)
@Composable
fun MangaReaderRoute(mangaId: String, chapterId: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val vm: MangaReaderViewModel = fuwaViewModel(key = "reader-$mangaId-$chapterId") { c, _ ->
        MangaReaderViewModel(mangaId, chapterId, c.mangaRepository, c.mangaLibrary, c.settingsStore, c.mangaDownloads, c.mangaTranslator, c.appScope, ctx.cacheDir, c.profileRepository, c::refreshHomeSurfaces, ctx.applicationContext)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val readerMode by vm.readerMode.collectAsStateWithLifecycle()
    val translation by vm.translation.collectAsStateWithLifecycle()
    val translationEnabled by vm.translationEnabled.collectAsStateWithLifecycle()
    val autoScrollEnabled by vm.autoScrollEnabled.collectAsStateWithLifecycle()
    val keepScreenOn by vm.keepScreenOn.collectAsStateWithLifecycle()
    var controls by remember { mutableStateOf(ReaderControlsState(visible = true, lastInteractionMs = System.currentTimeMillis())) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val showControlsLabel = stringResource(R.string.manga_reader_show_controls)
    fun touch() { controls = controls.touched(System.currentTimeMillis()) }

    // Auto-hide the reader bars after a short idle period.
    LaunchedEffect(Unit) {
        while (true) {
            delay(250)
            controls = controls.tick(System.currentTimeMillis())
        }
    }

    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity, controls.visible) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (controls.visible) controller?.show(WindowInsetsCompat.Type.systemBars()) else controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // Keep the screen awake while reading, when the user opted in.
    DisposableEffect(activity, keepScreenOn) {
        val window = activity?.window
        if (keepScreenOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val chapter = state.chapter
        val originalPages = state.pages
        when {
            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ErrorState(state.error!!, onRetry = { vm.open(state.requestedId.ifEmpty { chapterId }) })
            }
            chapter == null || originalPages == null -> Column(Modifier.fillMaxSize().padding(top = 64.dp)) {
                repeat(3) {
                    SkeletonBox(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).aspectRatio(1f / 1.4f),
                        shape = RoundedCornerShape(8.dp),
                    )
                }
            }
            else -> key(chapter.id) {
                val label = chapterLabel(chapter)
                val translationAvailable = !isShinigamiId(chapter.id) && !isKomikuId(chapter.id)
                val showingTranslated = translation.pages != null
                val pages = translation.pages ?: originalPages
                val startPage = state.startPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                val readable = remember(state.chapters) { state.chapters.filter { it.externalUrl == null && it.pages > 0 } }
                val next = nextChapter(readable, chapter.id)
                val previous = previousChapter(readable, chapter.id)
                val listState = rememberLazyListState(initialFirstVisibleItemIndex = if (startPage == 0) 0 else startPage + 1)
                val pagerState = rememberPagerState(initialPage = startPage) { pages.size + if (next != null) 1 else 0 }
                var openingNext by remember(chapter.id) { mutableStateOf(false) }
                val openNext = {
                    if (!openingNext) {
                        next?.let {
                            openingNext = true
                            vm.open(it.id)
                        }
                    }
                }
                val openNextState = rememberUpdatedState(openNext)
                val endScrollConnection = remember(listState, next?.id) {
                    object : NestedScrollConnection {
                        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                            if (
                                source == NestedScrollSource.UserInput &&
                                available.y < -1f &&
                                !listState.canScrollForward
                            ) openNextState.value()
                            return Offset.Zero
                        }
                    }
                }
                val currentPage by remember(readerMode, pages.size) {
                    derivedStateOf {
                        if (readerMode == ReaderMode.WEBTOON) {
                            (listState.firstVisibleItemIndex - 1).coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                        } else pagerState.currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                    }
                }
                var lastPage by remember { mutableIntStateOf(startPage) }
                var modeInitialized by remember { mutableStateOf(false) }
                LaunchedEffect(readerMode) {
                    if (modeInitialized) {
                        if (readerMode == ReaderMode.WEBTOON) listState.scrollToItem(lastPage + 1)
                        else pagerState.scrollToPage(lastPage)
                    }
                    modeInitialized = true
                    snapshotFlow {
                        if (readerMode == ReaderMode.WEBTOON) {
                            (listState.firstVisibleItemIndex - 1).coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                        } else pagerState.currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                    }.drop(1).collect { lastPage = it }
                }
                LaunchedEffect(chapter.id, readerMode) {
                    snapshotFlow { currentPage }.distinctUntilChanged().debounce(1_000).collect { vm.saveProgress(chapter, it, pages.size, label) }
                }
                val latestPage by rememberUpdatedState(currentPage)
                DisposableEffect(Unit) { onDispose { vm.saveProgress(chapter, latestPage, pages.size, label) } }

                // Auto-translate on chapter open when enabled
                LaunchedEffect(chapter.id, translationAvailable, translationEnabled) {
                    if (translationAvailable && translationEnabled && !showingTranslated && !translation.running) {
                        vm.translateCurrentChapter()
                    }
                }

                val autoScrollPxPerSecond = with(LocalDensity.current) { 42.dp.toPx() }
                LaunchedEffect(
                    chapter.id,
                    readerMode,
                    autoScrollEnabled,
                    settingsOpen,
                    pickerOpen,
                    translation.running,
                    next?.id,
                ) {
                    if (
                        readerMode == ReaderMode.WEBTOON &&
                        autoScrollEnabled &&
                        !settingsOpen &&
                        !pickerOpen &&
                        !translation.running
                    ) {
                        var previousFrame = withFrameNanos { it }
                        while (currentCoroutineContext().isActive) {
                            val frame = withFrameNanos { it }
                            val seconds = ((frame - previousFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
                            previousFrame = frame
                            listState.scrollBy(autoScrollPxPerSecond * seconds)
                            if (!listState.canScrollForward) {
                                if (next != null) {
                                    delay(700)
                                    openNext()
                                }
                                break
                            }
                        }
                    }
                }

                LaunchedEffect(chapter.id, readerMode, pagerState.settledPage, pages.size, next?.id) {
                    if (readerMode != ReaderMode.WEBTOON && pagerState.settledPage >= pages.size) openNext()
                }

                var zoom by remember { mutableStateOf(ZoomState()) }
                Box(
                    Modifier.fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoom.scale
                            scaleY = zoom.scale
                            translationX = zoom.offsetX
                            translationY = zoom.offsetY
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    if (readerMode == ReaderMode.WEBTOON) {
                                        if (next != null && !listState.canScrollForward) openNext()
                                        else controls = controls.toggled(System.currentTimeMillis())
                                    } else {
                                        if (shouldAdvanceReaderOnTap(currentPage, pages.size, next != null)) openNext()
                                        else controls = controls.toggled(System.currentTimeMillis())
                                    }
                                },
                                onDoubleTap = { offset ->
                                    zoom = doubleTapZoom(zoom, offset.x, offset.y, size.width.toFloat(), size.height.toFloat())
                                },
                            )
                        }
                        .pointerInput(zoom.scale > READER_MIN_ZOOM) {
                            if (zoom.scale > READER_MIN_ZOOM) {
                                detectTransformGestures { centroid, pan, zoomChange, _ ->
                                    zoom = applyPinch(zoom, zoomChange, pan.x, pan.y, centroid.x, centroid.y, size.width.toFloat(), size.height.toFloat())
                                }
                            }
                        }
                        .semantics {
                            customActions = listOf(
                                CustomAccessibilityAction(showControlsLabel) {
                                    controls = controls.shown(System.currentTimeMillis())
                                    true
                                },
                            )
                        },
                ) {
                    if (readerMode == ReaderMode.WEBTOON) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().nestedScroll(endScrollConnection),
                        ) {
                            item(key = "credit-top") { Credit(chapter, Modifier.statusBarsPadding().padding(top = 56.dp, bottom = 8.dp)) }
                            itemsIndexed(pages, key = { i, _ -> "${chapter.id}-$i" }) { i, url ->
                                Page(url = url, index = i, generation = state.generation, onFailed = vm::refreshPages)
                            }
                            item(key = "end") { EndCard(label, chapter, next, onNext = openNext) }
                        }
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            reverseLayout = readerMode == ReaderMode.RIGHT_TO_LEFT,
                            modifier = Modifier.fillMaxSize(),
                            key = { "${chapter.id}-$it" },
                        ) { page ->
                            if (page >= pages.size) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    EndCard(label, chapter, next, onNext = openNext)
                                }
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    ReaderPage(url = pages[page], index = page, generation = state.generation, onFailed = vm::refreshPages)
                                }
                            }
                        }
                    }
                }

                // Compact progress banner: reading can continue while completed pages appear in place.
                AnimatedVisibility(
                    visible = translation.running && (controls.visible || translation.done == 0),
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    val percent = if (translation.total > 0) {
                        (translation.done * 100 / translation.total.coerceAtLeast(1)).coerceIn(0, 100)
                    } else 0
                    Column(
                        Modifier.statusBarsPadding().padding(top = 58.dp)
                            .fillMaxWidth(0.9f).clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.86f)).padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.manga_reader_translating_chapter), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        if (translation.total > 0) {
                            LinearProgressIndicator(
                                progress = { (translation.done.toFloat() / translation.total.coerceAtLeast(1)).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                stringResource(
                                    R.string.manga_reader_translate_ready_progress,
                                    translation.done,
                                    translation.total,
                                    percent,
                                ),
                                color = Color.White.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            stringResource(
                                if (translation.done > 0) R.string.manga_reader_translate_live_hint
                                else R.string.manga_reader_translate_preparing_detail,
                            ),
                            color = Color.White.copy(alpha = 0.68f),
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                if (translation.error != null) {
                    val partialFailure = translation.done > 0
                    val errorPosition = if (partialFailure) {
                        Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 58.dp)
                    } else {
                        Modifier.align(Alignment.Center)
                    }
                    Column(
                        errorPosition.fillMaxWidth(if (partialFailure) 0.9f else 0.85f).clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.85f)).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(translation.error!!.text(), color = Color.White, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Row {
                            TextButton(onClick = { vm.translateCurrentChapter() }) { Text(stringResource(R.string.manga_reader_translate_retry), color = Color.White) }
                            TextButton(onClick = { vm.showOriginal() }) { Text(stringResource(R.string.manga_reader_show_original), color = Color.White.copy(alpha = 0.8f)) }
                        }
                    }
                }

                AnimatedVisibility(controls.visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
                    Row(
                        Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.75f)).statusBarsPadding().padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { touch(); onBack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.mine_back), tint = Color.White) }
                        Column(Modifier.weight(1f)) {
                            Text(state.manga?.title.orEmpty(), color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(
                                Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = stringResource(R.string.manga_reader_pick)) { touch(); pickerOpen = true }.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val translationSuffix = when {
                                    translation.running && translation.total > 0 -> " · ID ${translation.done}/${translation.total}"
                                    showingTranslated -> " · ID"
                                    else -> ""
                                }
                                Text(label + translationSuffix, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                Icon(Icons.Rounded.ArrowDropDown, null, tint = Color.White)
                            }
                        }
                        IconButton(onClick = { touch(); previous?.let { vm.open(it.id) } }, enabled = previous != null) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(R.string.manga_reader_prev), tint = if (previous != null) Color.White else Color.White.copy(alpha = 0.3f))
                        }
                        IconButton(onClick = { touch(); next?.let { vm.open(it.id) } }, enabled = next != null) {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.manga_reader_next), tint = if (next != null) Color.White else Color.White.copy(alpha = 0.3f))
                        }
                        if (translationAvailable) {
                            IconButton(onClick = { if (showingTranslated) vm.showOriginal() else vm.translateCurrentChapter() }, enabled = !translation.running) {
                                Icon(Icons.Rounded.Translate, stringResource(R.string.manga_reader_translate), tint = if (showingTranslated) MaterialTheme.colorScheme.primary else Color.White)
                            }
                        }
                        IconButton(onClick = { touch(); settingsOpen = true }) { Icon(Icons.Rounded.Settings, stringResource(R.string.ux_reader_settings), tint = Color.White) }
                    }
                }
                if (pickerOpen) ChapterPicker(readable, chapter.id, onPick = { pickerOpen = false; if (it != chapter.id) vm.open(it) }, onDismiss = { pickerOpen = false })
                AnimatedVisibility(controls.visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                    Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.75f)).navigationBarsPadding().padding(4.dp)) {
                        if (translationAvailable && !showingTranslated && !translation.running) {
                            TextButton(onClick = { vm.translateCurrentChapter() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                                Icon(Icons.Rounded.Translate, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Text(stringResource(R.string.manga_reader_translate_chapter), color = Color.White, modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { touch(); previous?.let { vm.open(it.id) } }, enabled = previous != null) {
                                Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.manga_reader_prev), tint = if (previous != null) Color.White else Color.White.copy(alpha = 0.3f))
                            }
                            Text(stringResource(R.string.manga_reader_page, currentPage + 1, pages.size), color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                            IconButton(onClick = { touch(); next?.let { vm.open(it.id) } }, enabled = next != null) {
                                Icon(Icons.Rounded.SkipNext, stringResource(R.string.manga_reader_next), tint = if (next != null) Color.White else Color.White.copy(alpha = 0.3f))
                            }
                        }
                        if (pages.size > 1) {
                            Slider(
                                value = currentPage.toFloat(),
                                onValueChange = { page -> scope.launch { if (readerMode == ReaderMode.WEBTOON) listState.scrollToItem(page.toInt() + 1) else pagerState.scrollToPage(page.toInt()) } },
                                valueRange = 0f..(pages.size - 1).toFloat(),
                                steps = (pages.size - 2).coerceAtLeast(0),
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                }
                if (settingsOpen) ReaderSettingsSheet(
                    selected = readerMode, onSelect = vm::setReaderMode,
                    autoScrollEnabled = autoScrollEnabled,
                    onAutoScrollChange = vm::setAutoScrollEnabled,
                    keepScreenOn = keepScreenOn,
                    onKeepScreenOnChange = vm::setKeepScreenOn,
                    translationAvailable = translationAvailable, translationEnabled = translationEnabled,
                    showingTranslated = showingTranslated,
                    translationRunning = translation.running,
                    onTranslate = { vm.translateCurrentChapter() }, onShowOriginal = { vm.showOriginal() },
                    onTranslationPrefChange = vm::setTranslationEnabled,
                    onDismiss = { settingsOpen = false },
                )
            }
        }
        if (state.error != null || state.chapter == null || state.pages == null) {
            IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(4.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.mine_back), tint = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterPicker(chapters: List<MangaChapter>, currentId: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val at = chapters.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (at - 3).coerceAtLeast(0))
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.manga_reader_pick), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        LazyColumn(state = listState, modifier = Modifier.navigationBarsPadding()) {
            items(chapters, key = { it.id }) { c ->
                val current = c.id == currentId
                Row(Modifier.fillMaxWidth().clickable { onPick(c.id) }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        listOfNotNull(chapterLabel(c), c.title?.takeIf { it.isNotBlank() && c.number != null }).joinToString(" · "),
                        style = MaterialTheme.typography.bodyLarge, fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                        color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    if (current) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun Credit(chapter: MangaChapter, modifier: Modifier = Modifier) {
    Text(
        when {
            isShinigamiId(chapter.id) -> stringResource(R.string.manga_reader_source_shinigami)
            isKomikuId(chapter.id) -> stringResource(R.string.manga_reader_source_komiku)
            isWebtoonId(chapter.id) -> stringResource(R.string.manga_reader_source_webtoon)
            isKakaMangaId(chapter.id) -> stringResource(R.string.manga_reader_source_kakamanga)
            isMangaParkId(chapter.id) -> stringResource(R.string.manga_reader_source_mangapark)
            chapter.group != null -> stringResource(R.string.manga_reader_scanlation, chapter.group)
            else -> chapter.group ?: stringResource(R.string.manga_reader_source_mangapark)
        },
        color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium,
        textAlign = TextAlign.Center, modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
}

@Composable
private fun Page(url: String, index: Int, generation: Int, onFailed: () -> Unit) {
    val candidates = remember(url) { mangaPageCandidates(url).ifEmpty { listOf(url) } }
    var candidateIndex by remember(url, generation) { mutableIntStateOf(0) }
    val requestIndex = candidateIndex
    var status by remember(url, generation) { mutableStateOf<AsyncImagePainter.State?>(null) }
    val description = stringResource(R.string.manga_reader_page_cd, index + 1)
    Box(
        Modifier.fillMaxWidth().then(if (status is AsyncImagePainter.State.Success) Modifier else Modifier.aspectRatio(1f / 1.4f)),
        contentAlignment = Alignment.Center,
    ) {
        key(generation, requestIndex) {
            AsyncImage(
                model = candidates[requestIndex], contentDescription = description, contentScale = ContentScale.FillWidth,
                onState = { next ->
                    if (next is AsyncImagePainter.State.Error && requestIndex < candidates.lastIndex) { status = null; candidateIndex = requestIndex + 1 }
                    else status = next
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        when (status) {
            is AsyncImagePainter.State.Error -> TextButton(onClick = onFailed, modifier = Modifier.semantics { contentDescription = description }) {
                Icon(Icons.Rounded.Refresh, null, tint = Color.White)
                Text(stringResource(R.string.manga_reader_reload), color = Color.White, modifier = Modifier.padding(start = 6.dp))
            }
            is AsyncImagePainter.State.Success -> Unit
            else -> SkeletonBox(Modifier.fillMaxWidth().aspectRatio(1f / 1.4f), shape = RoundedCornerShape(4.dp))
        }
    }
}

@Composable
private fun ReaderPage(url: String, index: Int, generation: Int, onFailed: () -> Unit) {
    val candidates = remember(url) { mangaPageCandidates(url).ifEmpty { listOf(url) } }
    var candidateIndex by remember(url, generation) { mutableIntStateOf(0) }
    val requestIndex = candidateIndex
    var status by remember(url, generation) { mutableStateOf<AsyncImagePainter.State?>(null) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        key(generation, requestIndex) {
            AsyncImage(
                model = candidates[requestIndex], contentDescription = stringResource(R.string.manga_reader_page_cd, index + 1),
                contentScale = ContentScale.Fit,
                onState = { next ->
                    if (next is AsyncImagePainter.State.Error && requestIndex < candidates.lastIndex) { status = null; candidateIndex = requestIndex + 1 }
                    else status = next
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        when (status) {
            is AsyncImagePainter.State.Error -> TextButton(onClick = onFailed) { Text(stringResource(R.string.manga_reader_reload), color = Color.White) }
            is AsyncImagePainter.State.Success -> Unit
            else -> SkeletonBox(Modifier.fillMaxWidth(0.85f).aspectRatio(1f / 1.4f), shape = RoundedCornerShape(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSettingsSheet(
    selected: ReaderMode, onSelect: (ReaderMode) -> Unit,
    autoScrollEnabled: Boolean, onAutoScrollChange: (Boolean) -> Unit,
    keepScreenOn: Boolean, onKeepScreenOnChange: (Boolean) -> Unit,
    translationAvailable: Boolean, translationEnabled: Boolean, showingTranslated: Boolean,
    translationRunning: Boolean,
    onTranslate: () -> Unit, onShowOriginal: () -> Unit, onTranslationPrefChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.ux_reader_mode), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(20.dp))
        ReaderMode.entries.forEach { mode ->
            val label = when (mode) {
                ReaderMode.WEBTOON -> R.string.ux_reader_webtoon
                ReaderMode.SINGLE_PAGE -> R.string.ux_reader_single
                ReaderMode.RIGHT_TO_LEFT -> R.string.ux_reader_rtl
            }
            Row(Modifier.fillMaxWidth().clickable { onSelect(mode); onDismiss() }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(label), modifier = Modifier.weight(1f))
                if (mode == selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp))
        val autoScrollAvailable = selected == ReaderMode.WEBTOON
        Row(
            Modifier.fillMaxWidth().clickable(enabled = autoScrollAvailable) { onAutoScrollChange(!autoScrollEnabled) }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.manga_reader_auto_scroll),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (autoScrollAvailable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.manga_reader_auto_scroll_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = autoScrollEnabled && autoScrollAvailable,
                onCheckedChange = onAutoScrollChange,
                enabled = autoScrollAvailable,
            )
        }
        Row(
            Modifier.fillMaxWidth().clickable { onKeepScreenOnChange(!keepScreenOn) }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.manga_settings_keep_screen_on), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.manga_settings_keep_screen_on_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = keepScreenOn, onCheckedChange = onKeepScreenOnChange)
        }
        if (translationAvailable) {
            HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            Text(stringResource(R.string.manga_reader_translate_settings), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            Row(
                Modifier.fillMaxWidth().clickable(enabled = !translationRunning) {
                    if (showingTranslated) onShowOriginal() else onTranslate()
                }.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(if (showingTranslated) R.string.manga_reader_show_original else R.string.manga_reader_translate_chapter),
                    color = if (translationRunning) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Rounded.Translate,
                    null,
                    tint = when {
                        translationRunning -> MaterialTheme.colorScheme.onSurfaceVariant
                        showingTranslated -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.manga_reader_translate_auto), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.manga_reader_translate_auto_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = translationEnabled, onCheckedChange = onTranslationPrefChange)
            }
        }
    }
}

@Composable
private fun EndCard(label: String, chapter: MangaChapter, next: MangaChapter?, onNext: () -> Unit) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.manga_reader_end, label), color = Color.White, style = MaterialTheme.typography.titleMedium)
        Credit(chapter)
        if (next != null) {
            Button(onClick = onNext) { Text(stringResource(R.string.manga_reader_next)) }
            Text(
                stringResource(R.string.manga_reader_continue_hint),
                color = Color.White.copy(alpha = 0.65f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
        else Text(stringResource(R.string.manga_reader_last), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
    }
}
