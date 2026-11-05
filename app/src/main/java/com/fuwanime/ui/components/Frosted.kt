package com.fuwanime.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

/** Height of the bottom bar floating over tab content; tab lists add it to their bottom padding. */
val LocalNavBarPadding = compositionLocalOf<Dp> { 0.dp }

/** Frosted glass for bars over scrolling content: blur on API 31+, a translucent scrim below that. */
@Composable
fun frostedStyle(): HazeStyle {
    val bg = MaterialTheme.colorScheme.background
    return HazeStyle(
        backgroundColor = bg,
        tints = listOf(HazeTint(bg.copy(alpha = 0.72f))),
        blurRadius = 24.dp,
        noiseFactor = 0f,
    )
}

/** Caps reading screens at 840dp, centred, so tablets don't stretch lines across the whole width. */
fun Modifier.readableWidth(): Modifier = fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 840.dp)
