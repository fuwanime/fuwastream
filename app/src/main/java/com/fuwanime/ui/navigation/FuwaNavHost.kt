package com.fuwanime.ui.navigation

import androidx.compose.ui.res.stringResource
import com.fuwanime.R
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandHorizontally
import com.fuwanime.ui.components.frostedStyle
import com.fuwanime.ui.components.LocalNavBarPadding
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRail
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.fuwanime.ui.components.OfflinePill
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.background
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import com.fuwanime.ui.motion.passTouchesThrough
import com.fuwanime.ui.motion.sharedEpisodeKey
import com.fuwanime.FuwaApp
import com.fuwanime.data.Anime
import com.fuwanime.ui.motion.LocalNavScope
import com.fuwanime.ui.motion.LocalReduceMotion
import com.fuwanime.ui.motion.LocalSharedScope
import com.fuwanime.ui.motion.Motion
import com.fuwanime.ui.motion.rememberReduceMotion
import com.fuwanime.data.Episode
import com.fuwanime.ui.detail.DetailRoute
import com.fuwanime.ui.explore.ExploreRoute
import com.fuwanime.ui.home.HomeRoute
import com.fuwanime.ui.manga.ContentMode
import com.fuwanime.ui.library.LibraryRoute
import com.fuwanime.ui.legal.LegalRoute
import com.fuwanime.ui.player.PlayerRoute
import com.fuwanime.ui.schedule.ScheduleRoute
import com.fuwanime.ui.settings.SettingsRoute
import com.fuwanime.ui.stats.StatsRoute

internal object Routes {
    const val HOME = "home"
    const val EXPLORE = "explore?genre={genre}&focus={focus}"
    const val SCHEDULE = "schedule"
    const val LIBRARY = "library"
    const val DETAIL = "detail/{animeId}?from={from}"
    const val PLAYER = "player/{animeId}/{episodeId}?number={number}&thumb={thumb}&shared={shared}"
    const val SETTINGS = "settings"
    const val STATS = "stats"
    const val LEGAL = "legal/{type}"
    const val MANGA_DETAIL = "manga/{mangaId}"

    fun mangaDetail(id: String) = "manga/${Uri.encode(id)}"

    fun legal(type: String) = "legal/${Uri.encode(type)}"

    fun explore(genre: String? = null, focus: Boolean = false) =
        "explore?genre=${Uri.encode(genre.orEmpty())}&focus=$focus"

    /** [from] names the row the poster was tapped in; it keys the shared poster transition. */
    fun detail(animeId: String, from: String? = null) =
        "detail/${Uri.encode(animeId)}?from=${Uri.encode(from.orEmpty())}"

    /** [thumb] is shown in the video area until the first frame; [shared] is the key of the image that flies into it. */
    fun player(animeId: String, episodeId: String, number: String, thumb: String = "", shared: String? = null) =
        "player/${Uri.encode(animeId)}/${Uri.encode(episodeId)}?number=${Uri.encode(number)}" +
            "&thumb=${Uri.encode(thumb)}&shared=${Uri.encode(shared.orEmpty())}"
}

private enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME(Routes.HOME, R.string.common_tab_home, Icons.Outlined.Home, Icons.Rounded.Home),
    EXPLORE(Routes.EXPLORE, R.string.common_tab_explore, Icons.Outlined.Explore, Icons.Rounded.Explore),
    SCHEDULE(Routes.SCHEDULE, R.string.common_tab_schedule, Icons.Outlined.CalendarMonth, Icons.Rounded.CalendarMonth),
    LIBRARY(Routes.LIBRARY, R.string.common_tab_library, Icons.Outlined.VideoLibrary, Icons.Rounded.VideoLibrary),
}

/** Detail and player sit on top of the tabs; moving to or from them is a fade under a shared element. */
private fun NavBackStackEntry.isOverlay() = destination.route == Routes.DETAIL || destination.route == Routes.PLAYER

private val NavInactive = Color(0xFF8A8494)
private val NavHairline = Color(0xFF1F1C27)

private val FadeThroughIn = fadeIn(tween(Motion.Medium, 90, Motion.EmphasizedDecelerate)) +
    scaleIn(tween(Motion.Medium, 90, Motion.EmphasizedDecelerate), initialScale = 0.94f)
private val FadeThroughOut = fadeOut(tween(90, easing = Motion.EmphasizedAccelerate))
// The page underneath is always fully visible, so the flying poster never floats over an empty screen (the old
// fade-through left ~100 ms of blank). Opening: the new page slides in ~10% and covers it in 150 ms. Closing: the
// page beneath is there at once (slides back, no fade) while the top page slides off and fades in 150 ms.
private val PageIn = slideInHorizontally(tween(Motion.Page, easing = Motion.EmphasizedDecelerate)) { it / 10 } +
    fadeIn(tween(Motion.Cover, easing = LinearEasing))
private val PageOut = fadeOut(tween(Motion.Long), targetAlpha = 1f) // stays put and visible until the poster lands
private val PageBackIn = slideInHorizontally(tween(Motion.Page, easing = Motion.EmphasizedDecelerate)) { -it / 10 }
private val PageBackOut = slideOutHorizontally(tween(Motion.Page, easing = Motion.EmphasizedAccelerate)) { it / 10 } +
    fadeOut(tween(Motion.Cover, easing = LinearEasing))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.involvesOverlay() =
    initialState.isOverlay() || targetState.isOverlay()

/**
 * Exposes the destination's enter/exit scope to shared elements and entrance animations inside it, on an opaque
 * background so a page fading in covers the one beneath instead of mixing with it.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun AnimatedContentScope.Scoped(content: @Composable () -> Unit) {
    // A leaving page stays composed (its shared element may still be flying) but takes no touches, so the page
    // beneath is usable at once. Read from this destination's own transition: LocalNavScope is provided below.
    val exiting = transition.targetState == EnterExitState.PostExit
    CompositionLocalProvider(LocalNavScope provides this) {
        Box(
            Modifier
                .passTouchesThrough(exiting)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) { content() }
    }
}

private fun NavBackStackEntry.from(): String? = arguments?.getString("from")?.takeIf { it.isNotBlank() }

private fun NavHostController.navigateTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FuwaNavHost(initialTarget: LaunchTarget? = null, onInitialTargetConsumed: () -> Unit = {}) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val currentTab = Tab.entries.firstOrNull { tab -> destination?.hierarchy?.any { it.route == tab.route } == true }

    val container = (LocalContext.current.applicationContext as FuwaApp).container
    val animeRepository = container.animeRepository
    val netState by container.networkStatus.state.collectAsStateWithLifecycle()
    val openAnime: (Anime, String) -> Unit = { anime, from ->
        // The detail header needs the poster on its first frame to be the shared poster's landing spot.
        animeRepository.prime(anime)
        nav.navigate(Routes.detail(anime.id, from))
    }
    val play: (String, Episode, String?, String) -> Unit = { animeId, ep, shared, thumb ->
        nav.navigate(Routes.player(animeId, ep.id, ep.number, thumb, shared))
    }
    val reduceMotion = rememberReduceMotion()
    var contentMode by rememberSaveable { mutableStateOf(ContentMode.ANIME) }
    val openManga: (String) -> Unit = { nav.navigate(Routes.mangaDetail(it)) }
    // Fresh Explore entry (no restoreState) so the new genre/focus arguments take effect.
    val openExplore: (String?, Boolean) -> Unit = { genre, focus ->
        nav.navigate(Routes.explore(genre, focus)) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
        }
    }

    // Deep links from a notification, shortcut or widget.
    LaunchedEffect(initialTarget) {
        when (val target = initialTarget ?: return@LaunchedEffect) {
            is LaunchTarget.Anime -> nav.navigate(Routes.detail(target.id))
            LaunchTarget.Search -> openExplore(null, true)
            LaunchTarget.Schedule -> nav.navigateTab(Routes.SCHEDULE)
            is LaunchTarget.Resume -> resumeRoute(container.libraryRepository.historyEntry(target.episodeId))?.let { nav.navigate(it) }
        }
        onInitialTargetConsumed()
    }

    // Wide screens get a rail beside the content; phones get a bar floating over it, frosted, with lists padded by
    // LocalNavBarPadding so their last items scroll clear of it.
    val rail = useNavRail(LocalConfiguration.current.screenWidthDp)
    val hazeState = remember { HazeState() }
    val density = LocalDensity.current
    var barHeight by remember { mutableStateOf(0.dp) }
    val colors = MaterialTheme.colorScheme
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = colors.primary,
        selectedTextColor = colors.onSurface,
        indicatorColor = colors.secondaryContainer,
        unselectedIconColor = NavInactive,
        unselectedTextColor = NavInactive,
    )
    val railColors = NavigationRailItemDefaults.colors(
        selectedIconColor = colors.primary,
        selectedTextColor = colors.onSurface,
        indicatorColor = colors.secondaryContainer,
        unselectedIconColor = NavInactive,
        unselectedTextColor = NavInactive,
    )
    fun onTab(tab: Tab) {
        if (tab == Tab.EXPLORE) nav.navigateTab(Routes.explore()) else nav.navigateTab(tab.route)
    }
    // The Scaffold this replaced set the content colour; without it, plain Text falls back to black.
    CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
    Row(Modifier.fillMaxSize().background(colors.background)) {
        if (rail) {
            AnimatedVisibility(
                visible = currentTab != null,
                // Animate the width too, so the content beside the rail moves smoothly instead of jumping 80dp.
                enter = expandHorizontally(expandFrom = Alignment.Start) + slideInHorizontally { -it },
                exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + slideOutHorizontally { -it },
            ) {
                NavigationRail(containerColor = colors.surfaceContainerLow) {
                    Tab.entries.forEach { tab ->
                        val selected = tab == currentTab
                        NavigationRailItem(
                            selected = selected,
                            onClick = { onTab(tab) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, null) },
                            label = { NavLabel(stringResource(tab.label), selected) },
                            colors = railColors,
                        )
                    }
                }
            }
        }
        val systemNav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val navPad = navContentPadding(rail, currentTab != null, barHeight.value, systemNav.value).dp
        Box(Modifier.weight(1f).fillMaxHeight()) {
          CompositionLocalProvider(LocalNavBarPadding provides navPad) {
            Box(Modifier.fillMaxSize().hazeSource(hazeState)) {
            SharedTransitionLayout {
                CompositionLocalProvider(LocalSharedScope provides this, LocalReduceMotion provides reduceMotion) {
                    NavHost(
                        navController = nav,
                        startDestination = Routes.HOME,
                        // Tabs fade through; detail/player move along the X axis under their flying shared element.
                        enterTransition = { if (involvesOverlay()) PageIn else FadeThroughIn },
                        exitTransition = { if (involvesOverlay()) PageOut else FadeThroughOut },
                        popEnterTransition = { if (involvesOverlay()) PageBackIn else FadeThroughIn },
                        popExitTransition = { if (involvesOverlay()) PageBackOut else FadeThroughOut },
                    ) {
                        composable(Routes.HOME) {
                            Scoped {
                                HomeRoute(
                                    onAnimeClick = openAnime,
                                    onContinue = { e ->
                                        nav.navigate(
                                            Routes.player(
                                                e.animeId, e.episodeId, e.episodeNumber,
                                                e.episodeThumbnail.ifBlank { e.animeCover },
                                                shared = sharedEpisodeKey(e.episodeId, "home-cw"),
                                            ),
                                        )
                                    },
                                    onSearch = { openExplore(null, true) },
                                    onSettings = { nav.navigate(Routes.SETTINGS) },
                                    onSchedule = { nav.navigateTab(Routes.SCHEDULE) },
                                    onExplore = { g -> openExplore(g, false) },
                                    mode = contentMode,
                                    onModeChange = { contentMode = it },
                                    onMangaClick = openManga,
                                )
                            }
                        }
                        composable(
                            Routes.EXPLORE,
                            arguments = listOf(
                                navArgument("genre") { type = NavType.StringType; defaultValue = "" },
                                navArgument("focus") { type = NavType.BoolType; defaultValue = false },
                            ),
                        ) { entry ->
                            Scoped {
                                ExploreRoute(
                                    focusSearch = entry.arguments?.getBoolean("focus") == true,
                                    onAnimeClick = openAnime,
                                    mode = contentMode,
                                    onModeChange = { contentMode = it },
                                    onMangaClick = openManga,
                                )
                            }
                        }
                        // Placeholder until the manga detail screen replaces it.
                        composable(
                            Routes.MANGA_DETAIL,
                            arguments = listOf(navArgument("mangaId") { type = NavType.StringType }),
                        ) { entry ->
                            Scoped {
                                Column(Modifier.statusBarsPadding()) {
                                    IconButton(onClick = { nav.popBackStack() }) {
                                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.mine_back))
                                    }
                                    Text(
                                        stringResource(R.string.manga_detail_placeholder, entry.arguments?.getString("mangaId").orEmpty()),
                                        Modifier.padding(horizontal = 20.dp),
                                    )
                                }
                            }
                        }
                        composable(Routes.SCHEDULE) { Scoped { ScheduleRoute(onAnimeClick = openAnime) } }
                        composable(Routes.LIBRARY) {
                            Scoped {
                                LibraryRoute(
                                    onAnimeClick = openAnime,
                                    onResume = { e -> nav.navigate(playerRouteFor(e, "library")) },
                                    onPlayDownload = { d -> nav.navigate(Routes.player(d.animeId, d.episodeId, d.episodeNumber)) },
                                    onStats = { nav.navigate(Routes.STATS) },
                                    onSettings = { nav.navigate(Routes.SETTINGS) },
                                )
                            }
                        }
                        composable(
                            Routes.DETAIL,
                            arguments = listOf(
                                navArgument("animeId") { type = NavType.StringType },
                                navArgument("from") { type = NavType.StringType; defaultValue = "" },
                            ),
                        ) { entry ->
                            Scoped {
                                DetailRoute(
                                    origin = entry.from(),
                                    entryKey = entry.id,
                                    onBack = { nav.popBackStack() },
                                    onPlay = { anime, ep, shared, thumb -> play(anime.id, ep, shared, thumb) },
                                    onAnimeClick = openAnime,
                                    onGenre = { g -> openExplore(g, false) },
                                )
                            }
                        }
                        composable(
                            Routes.PLAYER,
                            arguments = listOf(
                                navArgument("animeId") { type = NavType.StringType },
                                navArgument("episodeId") { type = NavType.StringType },
                                navArgument("number") { type = NavType.StringType; defaultValue = "" },
                                navArgument("thumb") { type = NavType.StringType; defaultValue = "" },
                                navArgument("shared") { type = NavType.StringType; defaultValue = "" },
                            ),
                        ) { entry ->
                            Scoped {
                                PlayerRoute(
                                    sharedKey = entry.arguments?.getString("shared")?.takeIf { it.isNotBlank() },
                                    onBack = { nav.popBackStack() },
                                    onOpenAnime = { a ->
                                        nav.popBackStack()
                                        openAnime(a, "player")
                                    },
                                )
                            }
                        }
                        composable(Routes.STATS) { Scoped { StatsRoute(onBack = { nav.popBackStack() }, onExplore = { nav.navigateTab(Routes.explore()) }) } }
                        composable(Routes.SETTINGS) {
                            Scoped {
                                SettingsRoute(
                                    onBack = { nav.popBackStack() },
                                    onOpenLegal = { type -> nav.navigate(Routes.legal(type)) },
                                )
                            }
                        }
                        composable(
                            Routes.LEGAL,
                            arguments = listOf(navArgument("type") { type = NavType.StringType; defaultValue = "privacy" }),
                        ) { entry ->
                            Scoped {
                                LegalRoute(
                                    type = entry.arguments?.getString("type") ?: "privacy",
                                    onBack = { nav.popBackStack() },
                                )
                            }
                        }
                    }
                }
            }
            }
            // Floats over the tab content just above the bar, so showing it never shifts the layout.
            if (currentTab != null) {
                OfflinePill(netState, reduceMotion, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp + navPad))
            }
          }
            if (!rail) {
                // Unscoped overload: inside the Row, the RowScope one would be picked otherwise.
                androidx.compose.animation.AnimatedVisibility(
                    visible = currentTab != null,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    NavigationBar(
                        modifier = Modifier
                            .onSizeChanged { barHeight = with(density) { it.height.toDp() } }
                            .hazeEffect(hazeState, frostedStyle())
                            // Hairline on top separates the bar from posters scrolling under it.
                            .drawBehind { drawLine(NavHairline, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) },
                        containerColor = Color.Transparent,
                    ) {
                        Tab.entries.forEach { tab ->
                            val selected = tab == currentTab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { onTab(tab) },
                                icon = { Icon(if (selected) tab.selectedIcon else tab.icon, null) },
                                label = { NavLabel(stringResource(tab.label), selected) },
                                colors = itemColors,
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun NavLabel(text: String, selected: Boolean) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, navLabelFontScale(d.fontScale))) {
        Text(text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
    }
}
