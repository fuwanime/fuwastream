package com.fuwanime.ui.motion

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.ui.layout.layout
import kotlinx.coroutines.delay

/** Motion tokens: between snappy and cinematic, on Material 3's emphasized curves. */
object Motion {
    const val Short = 200
    const val Medium = 350
    const val Long = 420

    /** Page slide when opening/closing detail and player, and how fast the top page covers/uncovers the one beneath. */
    const val Page = 300
    const val Cover = 150

    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

/** Opens an anime's detail page; [from] names the row it was tapped in, for the shared poster key. */
typealias OpenAnime = (anime: com.fuwanime.data.Anime, from: String) -> Unit

/** Shared-element keys carry the row a title was opened from, so the same anime in two rows never clashes. */
fun sharedPosterKey(animeId: String, origin: String) = "poster-$animeId-$origin"
fun sharedEpisodeKey(episodeId: String, origin: String) = "episode-$episodeId-$origin"

/** The detail header backdrop, which flies into the player when "Tonton"/"Lanjutkan" is pressed. */
fun sharedBackdropKey(animeId: String, origin: String) = "backdrop-$animeId-$origin"

private const val STAGGER_STEP_MS = 35
private const val STAGGER_MAX_STEPS = 8
private const val STAGGER_ITEMS = 12

/** Only items composed this soon after a list first shows count as "first screenful". */
private const val STAGGER_WINDOW_MS = 600L

fun staggerDelay(index: Int): Int = index.coerceIn(0, STAGGER_MAX_STEPS) * STAGGER_STEP_MS

/**
 * Whether a list's header is still on screen. A shared element only links to it then: flying to or from a header
 * scrolled off the top looks like the image chasing the list.
 */
fun headerStillShown(firstVisibleIndex: Int, firstVisibleOffsetPx: Int, maxOffsetPx: Int): Boolean =
    firstVisibleIndex == 0 && firstVisibleOffsetPx < maxOffsetPx

/** The system "remove animations" setting sets the animator scale to 0. */
fun isReduceMotion(animatorScale: Float): Boolean = animatorScale == 0f

/**
 * Remembers which list items already played their entrance, so scrolling back or returning from another
 * screen never replays it. Saveable, so it survives lazy-item disposal and back navigation.
 */
class StaggerState private constructor(private val played: HashSet<String>) {
    constructor() : this(HashSet())

    /** Start of this list's first appearance; items composed long after it come from scrolling. */
    val createdAtMs: Long = System.currentTimeMillis()

    /** True the first time an early item shows up within the first-appearance window. */
    fun shouldAnimate(key: String, index: Int, elapsedMs: Long): Boolean =
        index < STAGGER_ITEMS && elapsedMs < STAGGER_WINDOW_MS && played.add(key)

    fun save(): List<String> = played.toList()

    companion object {
        fun restore(saved: List<String>) = StaggerState(saved.toHashSet())

        val Saver: Saver<StaggerState, ArrayList<String>> = Saver(save = { ArrayList(it.save()) }, restore = { restore(it) })
    }
}

@Composable
fun rememberStaggerState(): StaggerState = rememberSaveable(saver = StaggerState.Saver) { StaggerState() }

@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        isReduceMotion(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The enclosing navigation destination's enter/exit scope. */
val LocalNavScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

val LocalReduceMotion = staticCompositionLocalOf { false }

@OptIn(ExperimentalSharedTransitionApi::class)
private val SharedBounds = BoundsTransform { _, _ -> tween(Motion.Long, easing = Motion.Emphasized) }

/**
 * Makes this element fly between screens that use the same [key]. A no-op without a key, outside the
 * navigation scopes (previews, PiP) or when animations are off.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.sharedKey(key: String?): Modifier = composed { Modifier.sharedKey(rememberSharedKey(key)) }

/** [sharedKey] with a state the caller keeps, so other parts of the card can tell when this element is flying. */
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.sharedKey(state: SharedTransitionScope.SharedContentState?): Modifier = composed {
    val shared = LocalSharedScope.current
    val nav = LocalNavScope.current
    if (state == null || shared == null || nav == null) return@composed Modifier
    with(shared) { Modifier.sharedElement(state, nav, boundsTransform = SharedBounds) }
}

/** The shared state for [key], or null wherever [sharedKey] would be a no-op. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun rememberSharedKey(key: String?): SharedTransitionScope.SharedContentState? {
    val shared = LocalSharedScope.current
    if (key == null || shared == null || LocalNavScope.current == null || LocalReduceMotion.current) return null
    return with(shared) { rememberSharedContentState(key) }
}

/**
 * Hides card labels while their own poster ([state]) is flying, then fades them in out of a blur once it lands, so
 * "Tayang"/type labels never sit on an empty slot ahead of the poster.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
fun Modifier.revealWhenLanded(state: SharedTransitionScope.SharedContentState?): Modifier = composed {
    val shared = LocalSharedScope.current
    if (state == null || shared == null) return@composed Modifier
    val flying = shared.isTransitionActive && state.isMatchFound
    val shown = animateFloatAsState(
        if (flying) 0f else 1f,
        if (flying) snap() else tween(Motion.Cover, easing = Motion.EmphasizedDecelerate),
        label = "revealWhenLanded",
    )
    Modifier.graphicsLayer {
        alpha = shown.value
        val radius = (1f - shown.value) * 6.dp.toPx()
        // Blur only shows on API 31+; older devices get the plain fade.
        renderEffect = if (radius > 0f) BlurEffect(radius, radius, TileMode.Decal) else null
    }
}

/** True while this destination is animating in (for dropping shared-element links the user scrolls away from). */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun rememberIsEntering(): Boolean {
    val transition = LocalNavScope.current?.transition ?: return false
    return transition.isRunning && transition.targetState == EnterExitState.Visible
}

/** True while this destination is leaving (closing, or being covered by the next page). */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun rememberIsExiting(): Boolean = LocalNavScope.current?.transition?.targetState == EnterExitState.PostExit

/**
 * Lets touches fall through to the page beneath while this one is leaving: it still draws (and its shared elements
 * still fly), but it reports no size, so hit testing skips it. Without this the fading page swallowed every tap
 * until its poster landed.
 */
fun Modifier.passTouchesThrough(enabled: Boolean): Modifier = if (!enabled) this else layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(0, 0) { placeable.place(0, 0) }
}

/**
 * Draws this element above any shared element in flight (e.g. a title next to the landing poster), so the poster
 * passes behind the text instead of covering it. No-op outside the shared transition scope.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalAnimationApi::class)
fun Modifier.aboveSharedElements(): Modifier = composed {
    val shared = LocalSharedScope.current ?: return@composed Modifier
    val nav = LocalNavScope.current
    with(shared) {
        Modifier.renderInSharedTransitionScopeOverlay(
            // Only on the page being shown: lifting the leaving page's labels drew them on top of the new page.
            renderInOverlay = { isTransitionActive && nav?.transition?.targetState != EnterExitState.PostExit },
            zIndexInOverlay = 1f,
        )
    }
}

/** Destination content that fades (and with [rise], rises) into place after a shared element starts flying. */
fun Modifier.navEnter(delayMs: Int = 120, rise: Boolean = true): Modifier = composed {
    val nav = LocalNavScope.current
    if (nav == null || LocalReduceMotion.current) return@composed Modifier
    val risePx = if (rise) with(LocalDensity.current) { 24.dp.roundToPx() } else 0
    with(nav) {
        Modifier.animateEnterExit(
            enter = fadeIn(tween(Motion.Medium, delayMs, Motion.EmphasizedDecelerate)) +
                slideInVertically(tween(Motion.Medium, delayMs, Motion.EmphasizedDecelerate)) { risePx },
            exit = fadeOut(tween(Motion.Short, easing = Motion.EmphasizedAccelerate)),
        )
    }
}

/** First-appearance fade + rise for list items, staggered by [index] and played once per [state]. */
fun Modifier.staggerIn(state: StaggerState, key: String, index: Int): Modifier = composed {
    val reduce = LocalReduceMotion.current
    val animate = remember(key) {
        !reduce && state.shouldAnimate(key, index, System.currentTimeMillis() - state.createdAtMs)
    }
    val progress = remember(key) { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(key) {
        if (animate) {
            delay(staggerDelay(index).toLong())
            progress.animateTo(1f, tween(Motion.Medium, easing = Motion.EmphasizedDecelerate))
        }
    }
    val rise = with(LocalDensity.current) { 16.dp.toPx() }
    Modifier.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * rise
    }
}

/** Shrinks slightly while pressed; pass the same [interactionSource] to the clickable. */
fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(Motion.Short, easing = Motion.Emphasized), label = "pressScale")
    Modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
