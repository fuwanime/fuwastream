package com.fuwanime.ui.schedule

import com.fuwanime.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.fuwanime.ui.components.LocalNavBarPadding
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
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import java.util.Calendar
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
import com.fuwanime.util.UiText
import com.fuwanime.util.friendlyText
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
    val error: UiText? = null,
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
                    error = result.exceptionOrNull()?.friendlyText(),
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
    // Pages 0–6 are the days; the last page is "Musim ini".
    val pager = rememberPagerState(initialPage = today.ordinal) { Day.entries.size + 1 }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pager.currentPage) {
        Day.entries.getOrNull(pager.currentPage)?.let(vm::ensureLoaded) ?: Day.entries.forEach { vm.ensureLoaded(it) }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            stringResource(R.string.browse_schedule_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 20.dp, top = 12.dp),
        )
        Text(
            stringResource(R.string.browse_schedule_subtitle),
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
                            Text(stringResource(day.full))
                            if (day == today) {
                                Spacer(Modifier.width(6.dp))
                                Badge(containerColor = MaterialTheme.colorScheme.primary) { Text(stringResource(R.string.browse_schedule_today_badge)) }
                            }
                        }
                    },
                )
            }
            Tab(
                selected = pager.currentPage == Day.entries.size,
                onClick = { scope.launch { pager.animateScrollToPage(Day.entries.size) } },
                text = { Text(stringResource(R.string.browse_schedule_season_tab)) },
            )
        }
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            val day = Day.entries.getOrNull(page) ?: run {
                SeasonPage(
                    state = seasonTabState(days.values),
                    onRetry = { Day.entries.forEach { vm.ensureLoaded(it, force = true) } },
                    onAnimeClick = onAnimeClick,
                )
                return@HorizontalPager
            }
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
                        stringResource(R.string.browse_schedule_empty_title),
                        message = stringResource(R.string.browse_schedule_empty_message, stringResource(day.full)),
                    )
                    else -> LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + LocalNavBarPadding.current),
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

/** Everything airing this week, split into what premiered this season and what continues from before. */
@Composable
private fun SeasonPage(state: SeasonTab, onRetry: () -> Unit, onAnimeClick: OpenAnime) {
    when (state) {
        SeasonTab.Loading -> ScheduleSkeleton()
        is SeasonTab.Error -> ErrorState(state.message, onRetry)
        is SeasonTab.Ready -> {
            val season = remember {
                val c = Calendar.getInstance()
                seasonOf(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
            }
            val (fresh, continuing) = remember(state.all, season) { splitBySeason(state.all, season) }
            val sections = listOf(stringResource(R.string.browse_schedule_new_season) to fresh, stringResource(R.string.browse_schedule_continuing) to continuing)
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + LocalNavBarPadding.current),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "season-title") {
                    Text("${stringResource(season.name)} ${season.year}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                sections.forEach { (title, list) ->
                    if (list.isNotEmpty()) {
                        item(key = "h-$title") {
                            Text(
                                stringResource(R.string.browse_schedule_section, title, list.size),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        items(list, key = { "$title-${it.id}" }) { anime ->
                            ScheduleRow(anime, origin = "schedule-season", onClick = { onAnimeClick(anime, "schedule-season") })
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
            // The clickable row merges its texts for TalkBack; an image description would replace them.
            null,
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
                compactNumber(LocalContext.current.resources, anime.views),
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
