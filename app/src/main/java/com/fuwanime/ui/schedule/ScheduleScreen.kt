package com.fuwanime.ui.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fuwanime.ui.motion.sharedPosterKey
import com.fuwanime.ui.motion.sharedKey
import com.fuwanime.ui.motion.OpenAnime
import com.fuwanime.data.Anime
import com.fuwanime.data.AnimeRepository
import com.fuwanime.ui.components.ErrorState
import com.fuwanime.ui.components.MessageState
import com.fuwanime.ui.components.NetImage
import com.fuwanime.ui.components.Pill
import com.fuwanime.ui.components.SkeletonBox
import com.fuwanime.util.Day
import com.fuwanime.util.airTime
import com.fuwanime.util.compactNumber
import com.fuwanime.util.friendlyMessage
import com.fuwanime.util.fuwaViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DaySchedule(
    val items: List<Anime> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
)

class ScheduleViewModel(private val repo: AnimeRepository) : ViewModel() {
    private val _days = MutableStateFlow(Day.entries.associateWith { DaySchedule() })
    val days: StateFlow<Map<Day, DaySchedule>> = _days.asStateFlow()

    fun ensureLoaded(day: Day, force: Boolean = false) {
        val current = _days.value.getValue(day)
        if (current.loading || (current.loaded && !force)) return
        _days.update { it + (day to current.copy(loading = true, error = null)) }
        viewModelScope.launch {
            val result = repo.schedule(day.apiValue)
            _days.update { map ->
                map + (day to DaySchedule(
                    // Earliest release first; unknown times sink to the bottom.
                    items = result.getOrDefault(emptyList()).sortedBy { airTime(it.nextAirTime).ifBlank { "99" } },
                    loading = false,
                    loaded = result.isSuccess,
                    error = result.exceptionOrNull()?.friendlyMessage(),
                ))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleRoute(onAnimeClick: OpenAnime) {
    val vm: ScheduleViewModel = fuwaViewModel { c, _ -> ScheduleViewModel(c.animeRepository) }
    val days by vm.days.collectAsStateWithLifecycle()
    val today = Day.today()
    val pager = rememberPagerState(initialPage = today.ordinal) { Day.entries.size }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pager.currentPage) {
        vm.ensureLoaded(Day.entries[pager.currentPage])
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            "Jadwal Tayang",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp),
        )
        Text(
            "Rilis episode baru setiap minggu",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
        )
        PrimaryScrollableTabRow(selectedTabIndex = pager.currentPage, edgePadding = 12.dp) {
            Day.entries.forEachIndexed { i, day ->
                Tab(
                    selected = pager.currentPage == i,
                    onClick = { scope.launch { pager.animateScrollToPage(i) } },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(day.label)
                            if (day == today) {
                                Spacer(Modifier.width(6.dp))
                                Badge(containerColor = MaterialTheme.colorScheme.primary) { Text("Hari ini") }
                            }
                        }
                    },
                )
            }
        }
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            val day = Day.entries[page]
            val schedule = days.getValue(day)
            PullToRefreshBox(
                isRefreshing = schedule.loading && schedule.loaded,
                onRefresh = { vm.ensureLoaded(day, force = true) },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    !schedule.loaded && schedule.error == null -> ScheduleSkeleton()
                    schedule.error != null && schedule.items.isEmpty() ->
                        ErrorState(schedule.error, { vm.ensureLoaded(day, force = true) })
                    schedule.items.isEmpty() -> MessageState(
                        Icons.Rounded.EventBusy,
                        "Tidak ada jadwal",
                        message = "Belum ada anime yang dijadwalkan hari ${day.label}.",
                    )
                    else -> LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(schedule.items, key = { it.id }) { anime ->
                            ScheduleRow(anime, origin = "schedule-${day.apiValue}", onClick = { onAnimeClick(anime, "schedule-${day.apiValue}") })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleRow(anime: Anime, origin: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NetImage(
            anime.poster,
            Modifier.width(76.dp).aspectRatio(2f / 3f).sharedKey(sharedPosterKey(anime.id, origin)).clip(MaterialTheme.shapes.small),
            anime.title,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            val time = airTime(anime.nextAirTime)
            if (time.isNotBlank()) {
                Pill(
                    time,
                    leading = Icons.Rounded.Schedule,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(anime.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                anime.genres.take(3).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Pill(
                compactNumber(anime.views),
                leading = Icons.Rounded.Visibility,
                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScheduleSkeleton() {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(5) {
            Row {
                SkeletonBox(Modifier.width(76.dp).aspectRatio(2f / 3f), MaterialTheme.shapes.small)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBox(Modifier.width(60.dp).height(18.dp))
                    SkeletonBox(Modifier.fillMaxWidth().height(16.dp))
                    SkeletonBox(Modifier.fillMaxWidth(0.6f).height(12.dp))
                }
            }
        }
    }
}
