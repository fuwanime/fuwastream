package com.fuwanime.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fuwanime.data.remote.NetState
import com.fuwanime.ui.motion.LocalReduceMotion
import com.fuwanime.ui.motion.Motion
import com.fuwanime.util.offlineLabel
import kotlinx.coroutines.delay

/** 2 dp line under a top bar while data already on screen is refreshed quietly. Pull-to-refresh keeps its own indicator. */
@Composable
fun RefreshHairline(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible && !LocalReduceMotion.current,
        enter = fadeIn(tween(Motion.Short)),
        exit = fadeOut(tween(Motion.Short)),
        modifier = modifier,
    ) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Transparent,
        )
    }
}

/** Floating pill above the tab bar: "Offline · data 2 jam lalu", then "Kembali online" for a moment when it returns. */
@Composable
fun OfflinePill(state: NetState, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    var wasOffline by remember { mutableStateOf(!state.online) }
    var backOnline by remember { mutableStateOf(false) }
    LaunchedEffect(state.online) {
        if (!state.online) {
            wasOffline = true
            backOnline = false
        } else if (wasOffline) {
            wasOffline = false
            backOnline = true
            delay(BACK_ONLINE_MS)
            backOnline = false
        }
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.online) {
        while (!state.online) {
            now = System.currentTimeMillis()
            delay(60_000)
        }
    }

    AnimatedVisibility(
        visible = !state.online || backOnline,
        enter = if (reduceMotion) EnterTransition.None else slideInVertically(tween(Motion.Short)) { it / 2 } + fadeIn(tween(Motion.Short)),
        exit = if (reduceMotion) ExitTransition.None else slideOutVertically(tween(Motion.Short)) { it / 2 } + fadeOut(tween(Motion.Short)),
        // TalkBack announces going offline and coming back.
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        val colors = MaterialTheme.colorScheme
        Surface(
            shape = RoundedCornerShape(50),
            color = colors.surfaceContainerHigh.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, colors.outlineVariant),
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.online) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(colors.primary))
                } else {
                    Icon(Icons.Rounded.CloudOff, null, Modifier.size(14.dp), tint = colors.onSurfaceVariant)
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    if (state.online) "Kembali online" else offlineLabel(state.cacheServedAt, now),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

private const val BACK_ONLINE_MS = 1_500L
