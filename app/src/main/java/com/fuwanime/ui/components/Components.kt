package com.fuwanime.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fuwanime.data.Anime
import com.fuwanime.ui.motion.LocalReduceMotion
import com.fuwanime.ui.motion.Motion
import com.fuwanime.ui.motion.aboveSharedElements
import com.fuwanime.ui.motion.rememberSharedKey
import com.fuwanime.ui.motion.revealWhenLanded
import com.fuwanime.ui.motion.pressScale
import com.fuwanime.ui.motion.rememberStaggerState
import com.fuwanime.ui.motion.sharedEpisodeKey
import com.fuwanime.ui.motion.sharedKey
import com.fuwanime.ui.motion.sharedPosterKey
import com.fuwanime.ui.motion.staggerIn
import com.fuwanime.data.local.WatchHistoryEntity
import com.fuwanime.util.statusLabel

/** Network image with a tonal placeholder so empty slots never flash white. */
@Composable
fun NetImage(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        if (url.isNotBlank()) {
            AsyncImage(
                // Reuse whatever size of this image is already in memory, so shared elements never fly as a grey box.
                model = ImageRequest.Builder(context).data(url).placeholderMemoryCacheKey(url).crossfade(250).build(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

fun Modifier.shimmer(): Modifier = composed {
    // A static placeholder when the system has animations turned off.
    if (LocalReduceMotion.current) background(MaterialTheme.colorScheme.surfaceContainerHigh) else background(shimmerBrush())
}

@Composable
private fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -600f,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX",
    )
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    return Brush.linearGradient(listOf(base, highlight, base), start = Offset(x, 0f), end = Offset(x + 500f, 300f))
}

@Composable
fun SkeletonBox(modifier: Modifier, shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.medium) {
    Box(modifier.clip(shape).shimmer())
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    leading: ImageVector? = null,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, null, Modifier.size(12.dp), tint = content)
            Spacer(Modifier.width(3.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = content, maxLines = 1)
    }
}

@Composable
fun LiveDot(modifier: Modifier = Modifier) {
    Box(modifier.size(6.dp).clip(CircleShape).background(Color(0xFF4ADE80)))
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PosterCard(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    caption: String? = null,
    /** Row the card sits in; when set, its poster flies into the detail header. */
    sharedOrigin: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        (if (width != null) modifier.width(width) else modifier)
            .pressScale(interaction)
            .clip(MaterialTheme.shapes.medium)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick),
    ) {
        val poster = rememberSharedKey(sharedOrigin?.let { sharedPosterKey(anime.id, it) })
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(MaterialTheme.shapes.medium),
        ) {
            // Only the image is shared. Scrim and badges render above shared elements, so while a transition is
            // active the image (drawn in the transition overlay) never covers them.
            NetImage(
                anime.poster,
                // Clip after the shared key so the flying copy keeps its rounded corners.
                Modifier.fillMaxSize().sharedKey(poster).clip(MaterialTheme.shapes.medium),
                anime.title,
            )
            // Bottom scrim keeps badges legible on bright posters.
            Box(
                Modifier
                    .fillMaxSize()
                    .aboveSharedElements()
                    .revealWhenLanded(poster)
                    .background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f))),
            )
            if (anime.isOngoing) {
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .aboveSharedElements()
                        .revealWhenLanded(poster)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LiveDot()
                    Spacer(Modifier.width(4.dp))
                    Text("Tayang", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
            val meta = listOf(anime.type, anime.displayYear).filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.align(Alignment.BottomStart).aboveSharedElements().revealWhenLanded(poster).padding(8.dp),
                )
            }
        }
        Text(
            anime.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
        )
        if (caption != null) {
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
fun PosterSkeleton(modifier: Modifier = Modifier, width: Dp? = null) {
    Column(if (width != null) modifier.width(width) else modifier) {
        SkeletonBox(Modifier.fillMaxWidth().aspectRatio(2f / 3f))
        Spacer(Modifier.height(8.dp))
        SkeletonBox(Modifier.fillMaxWidth(0.85f).height(12.dp), RoundedCornerShape(4.dp))
        Spacer(Modifier.height(6.dp))
        SkeletonBox(Modifier.fillMaxWidth(0.5f).height(12.dp), RoundedCornerShape(4.dp))
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onSeeAll: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onSeeAll != null) {
            TextButton(onClick = onSeeAll) {
                Text("Lihat semua")
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun PosterRow(
    items: List<Anime>,
    loading: Boolean,
    onClick: (Anime) -> Unit,
    caption: (Anime) -> String? = { null },
    /** Shared-poster origin for this row, e.g. "home-popular"; null disables the poster transition. */
    origin: String? = null,
) {
    val stagger = rememberStaggerState()
    Crossfade(loading && items.isEmpty(), animationSpec = tween(Motion.Medium), label = "posterRow") { skeleton ->
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = !skeleton,
        ) {
            if (skeleton) {
                items(6) { PosterSkeleton(width = 124.dp) }
            } else {
                itemsIndexed(items, key = { _, a -> a.id }) { index, anime ->
                    PosterCard(
                        anime,
                        onClick = { onClick(anime) },
                        modifier = Modifier.staggerIn(stagger, anime.id, index),
                        width = 124.dp,
                        caption = caption(anime),
                        sharedOrigin = origin,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ContinueWatchingCard(
    entry: WatchHistoryEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** When set, the thumbnail flies into the player's video area. */
    sharedOrigin: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier.width(240.dp)
            .pressScale(interaction)
            .clip(MaterialTheme.shapes.medium)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick),
    ) {
        val thumb = rememberSharedKey(sharedOrigin?.let { sharedEpisodeKey(entry.episodeId, it) })
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.medium)) {
            NetImage(
                entry.episodeThumbnail.ifBlank { entry.animeCover },
                Modifier.fillMaxSize().sharedKey(thumb).clip(MaterialTheme.shapes.medium),
            )
            Box(Modifier.fillMaxSize().aboveSharedElements().revealWhenLanded(thumb).background(Color.Black.copy(alpha = 0.25f)))
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier.align(Alignment.Center).aboveSharedElements().revealWhenLanded(thumb).size(44.dp),
            ) {
                Icon(Icons.Rounded.PlayArrow, "Lanjutkan", tint = Color.White, modifier = Modifier.padding(8.dp))
            }
            Pill(
                "EP ${entry.episodeNumber}",
                Modifier.align(Alignment.TopStart).aboveSharedElements().revealWhenLanded(thumb).padding(8.dp),
                container = MaterialTheme.colorScheme.primary,
                content = MaterialTheme.colorScheme.onPrimary,
            )
            LinearProgressIndicator(
                progress = { entry.progress },
                modifier = Modifier.align(Alignment.BottomCenter).aboveSharedElements().revealWhenLanded(thumb).fillMaxWidth().height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.25f),
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
        }
        Text(
            entry.animeTitle,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
        )
        Text(
            entry.episodeTitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

@Composable
fun MessageState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(onClick = onAction) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    MessageState(
        icon = Icons.Rounded.CloudOff,
        title = "Gagal memuat",
        message = message,
        actionLabel = "Coba lagi",
        onAction = onRetry,
        modifier = modifier,
    )
}

/** Bottom-to-background fade used under hero and detail backdrops. */
fun Modifier.fadeToBackground(color: Color): Modifier = drawWithContent {
    drawContent()
    drawRect(Brush.verticalGradient(0.35f to Color.Transparent, 1f to color))
}

@Composable
fun StatusPill(status: String, modifier: Modifier = Modifier) {
    if (status.isBlank()) return
    val ongoing = status.equals("ONGOING", true)
    Pill(
        statusLabel(status),
        modifier,
        container = if (ongoing) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        content = if (ongoing) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
