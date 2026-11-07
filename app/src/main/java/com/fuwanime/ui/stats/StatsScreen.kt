package com.fuwanime.ui.stats

import androidx.compose.foundation.layout.fillMaxHeight
import com.fuwanime.ui.components.readableWidth
import com.fuwanime.ui.components.frostedStyle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeState
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import java.text.DateFormatSymbols
import java.util.TimeZone
import kotlinx.coroutines.flow.map
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fuwanime.data.LibraryRepository
import com.fuwanime.ui.components.MessageState
import com.fuwanime.ui.components.TagChip
import com.fuwanime.ui.theme.PlusJakarta
import com.fuwanime.util.epochDay
import com.fuwanime.util.fuwaViewModel
import java.io.File
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StatsViewModel(library: LibraryRepository) : ViewModel() {
    /** Null while loading. */
    val stats: StateFlow<WatchStats?> =
        combine(library.allHistory, library.watchDaily, library.favoriteList) { history, daily, favorites ->
            computeStats(history, daily, favorites, epochDay(System.currentTimeMillis()))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val week: StateFlow<List<DayBar>> =
        library.watchDaily.map { lastNDays(it, epochDay(System.currentTimeMillis())) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** Short weekday name ("Sen", "Mon") for an epoch day, in the current locale. */
private fun weekdayLabel(day: Int): String {
    val dow = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = day * 86_400_000L }.get(Calendar.DAY_OF_WEEK)
    return DateFormatSymbols.getInstance().shortWeekdays[dow]
}

private val MONTH_NAMES = listOf(
    "Januari", "Februari", "Maret", "April", "Mei", "Juni", "Juli", "Agustus", "September", "Oktober", "November", "Desember",
)

/** "12 jam 5 mnt", or just minutes under an hour. */
fun watchTimeLabel(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) "${minutes / 60} jam ${minutes % 60} mnt" else "$minutes mnt"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StatsRoute(onBack: () -> Unit, onExplore: () -> Unit) {
    val vm: StatsViewModel = fuwaViewModel { c, _ -> StatsViewModel(c.libraryRepository) }
    val loaded by vm.stats.collectAsStateWithLifecycle()
    val week by vm.week.collectAsStateWithLifecycle()
    val columns = statColumns(LocalDensity.current.fontScale, LocalConfiguration.current.screenWidthDp)
    val explain: (String) -> Unit = { msg -> scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val recapLayer = rememberGraphicsLayer()
    val monthYear = remember {
        Calendar.getInstance().let { "${MONTH_NAMES[it.get(Calendar.MONTH)]} ${it.get(Calendar.YEAR)}" }
    }

    val hazeState = remember { HazeState() }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Statistik") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") } },
                scrollBehavior = scrollBehavior,
                // Transparent so the frosted layer shows the list scrolling underneath.
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                modifier = Modifier.hazeEffect(hazeState, frostedStyle()),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val stats = loaded ?: return@Scaffold
        if (!stats.hasData) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                MessageState(
                    Icons.Rounded.Insights,
                    "Belum ada statistik",
                    message = "Tonton satu episode dulu, nanti angkanya muncul di sini.",
                    actionLabel = "Cari anime",
                    onAction = onExplore,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxHeight().readableWidth().hazeSource(hazeState),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Total waktu nonton", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(watchTimeLabel(stats.totalWatchedMs), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    if (stats.isEstimated) Text("± perkiraan untuk data lama", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    maxItemsInEachRow = columns,
                ) {
                    val card = Modifier.weight(1f)
                    StatCard("Episode selesai", stats.episodesFinished.toString(), null, card) { explain("Episode yang ditonton sampai minimal 92%.") }
                    StatCard("Anime", stats.animeWatched.toString(), null, card) { explain("Jumlah judul berbeda yang pernah kamu tonton.") }
                    StatCard("Minggu aktif", stats.activeWeeks.toString(), "berturut-turut", card) { explain("Minggu berturut-turut kamu menonton, sampai minggu ini.") }
                }
            }
            if (week.isNotEmpty()) item { BarChart("7 hari terakhir", week.map { weekdayLabel(it.day) }, week.map { it.minutes }) }
            item { BarChart("6 bulan terakhir", stats.monthly.map { it.label }, stats.monthly.map { it.minutes }) }
            if (stats.topGenres.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel("Genre favorit")
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            stats.topGenres.forEach { TagChip(it, onClick = {}) }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("Rekap")
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .drawWithContent {
                                recapLayer.record { this@drawWithContent.drawContent() }
                                drawLayer(recapLayer)
                            },
                    ) { RecapCard(stats, monthYear) }
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                runCatching {
                                    val bitmap = recapLayer.toImageBitmap().asAndroidBitmap()
                                    shareRecap(context, bitmap)
                                }.onFailure { snackbar.showSnackbar("Gagal membuat gambar rekap") }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Share, null)
                        Spacer(Modifier.padding(4.dp))
                        Text("Bagikan rekap")
                    }
                }
            }
        }
    }
}

private suspend fun shareRecap(context: Context, bitmap: Bitmap) {
    val file = withContext(Dispatchers.IO) {
        File(context.cacheDir, "share").apply { mkdirs() }.resolve("rekap.png").also { f ->
            f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Bagikan rekap"))
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StatCard(label: String, value: String, caption: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        if (caption != null) Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Rounded bars with the latest one highlighted and each non-zero value printed above its bar. */
@Composable
private fun BarChart(title: String, labels: List<String>, minutes: List<Int>) {
    val bar = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    val max = (minutes.maxOrNull() ?: 0).coerceAtLeast(1)
    val valueStyle = MaterialTheme.typography.labelSmall
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(title)
        Row(Modifier.fillMaxWidth()) {
            minutes.forEach {
                Text(
                    if (it > 0) it.toString() else "",
                    style = valueStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .semantics { contentDescription = labels.zip(minutes).joinToString { (l, m) -> "$l $m menit" } },
        ) {
            val slot = size.width / minutes.size
            val barWidth = slot * 0.5f
            minutes.forEachIndexed { i, m ->
                val x = slot * i + (slot - barWidth) / 2
                drawRoundRect(track, Offset(x, 0f), Size(barWidth, size.height), CornerRadius(barWidth / 2))
                val h = size.height * m / max
                val color = if (i == minutes.lastIndex) bar else bar.copy(alpha = 0.45f)
                if (h > 0) drawRoundRect(color, Offset(x, size.height - h), Size(barWidth, h), CornerRadius(barWidth / 2))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

private val RecapBg = Color(0xFF0B0A0F)
private val RecapBgEnd = Color(0xFF1A1721)
private val RecapPrimary = Color(0xFFD4BBFF)
private val RecapPink = Color(0xFFFFB0C9)
private val RecapText = Color(0xFFE8E0EE)
private val RecapMuted = Color(0xFFA39DAE)

/** Shareable 4:5 card in the banner style; fixed palette so it looks the same in light mode. */
@Composable
private fun RecapCard(stats: WatchStats, monthYear: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 5f)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(RecapBg, RecapBgEnd)))
            .drawWithContent {
                drawCircle(Brush.radialGradient(listOf(RecapPrimary.copy(alpha = 0.20f), Color.Transparent), Offset(size.width * 0.8f, size.height * 0.25f), size.width * 0.6f), size.width * 0.6f, Offset(size.width * 0.8f, size.height * 0.25f))
                drawCircle(Brush.radialGradient(listOf(RecapPink.copy(alpha = 0.13f), Color.Transparent), Offset(size.width * 0.15f, size.height * 0.9f), size.width * 0.5f), size.width * 0.5f, Offset(size.width * 0.15f, size.height * 0.9f))
                drawContent()
            }
            .padding(24.dp),
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = RecapPrimary)) { append("fuwa") }
                withStyle(SpanStyle(color = RecapText)) { append("stream") }
            },
            fontFamily = PlusJakarta,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp,
            modifier = Modifier.align(Alignment.TopStart),
        )
        Column(Modifier.align(Alignment.CenterStart), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Waktu nonton", color = RecapMuted, fontSize = 14.sp, fontFamily = PlusJakarta, fontWeight = FontWeight.SemiBold)
            Text(watchTimeLabel(stats.totalWatchedMs), color = RecapPrimary, fontSize = 40.sp, lineHeight = 44.sp, fontFamily = PlusJakarta, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            RecapLine("${stats.episodesFinished} episode selesai")
            RecapLine("${stats.animeWatched} anime ditonton")
            stats.topGenres.firstOrNull()?.let { RecapLine("Paling suka: $it") }
        }
        Text(monthYear, color = RecapMuted, fontSize = 13.sp, fontFamily = PlusJakarta, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun RecapLine(text: String) {
    Text(text, color = RecapText, fontSize = 17.sp, fontFamily = PlusJakarta, fontWeight = FontWeight.Bold)
}
