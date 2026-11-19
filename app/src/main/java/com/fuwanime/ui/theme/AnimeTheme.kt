package com.fuwanime.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import com.fuwanime.util.loadBitmap
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Overrides the Material 3 scheme with one generated from an anime's poster seed colour, so detail /
 * player / manga screens wear the title's own palette ("Material You from content"). Falls through
 * unchanged when no seed is known yet.
 */
@Composable
fun AnimeTheme(seed: Color?, content: @Composable () -> Unit) {
    if (seed == null) {
        content()
        return
    }
    val dark = LocalFuwaDark.current
    val scheme = remember(seed, dark) {
        dynamicColorScheme(seedColor = seed, isDark = dark, style = PaletteStyle.TonalSpot)
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}

/**
 * Extracts a vibrant seed colour from a poster/cover URL (decoded small, off the main thread) to feed
 * [AnimeTheme]. Null until the image loads or when the URL is blank, so the app palette shows first.
 */
@Composable
fun rememberAnimeSeed(imageUrl: String?): State<Color?> {
    val context = LocalContext.current
    return produceState<Color?>(null, imageUrl) {
        value = if (imageUrl.isNullOrBlank()) {
            null
        } else {
            loadBitmap(context, imageUrl, 64)?.let { bitmap ->
                val palette = withContext(Dispatchers.Default) { Palette.from(bitmap).generate() }
                val rgb = palette.vibrantSwatch?.rgb ?: palette.darkVibrantSwatch?.rgb ?: palette.dominantSwatch?.rgb
                rgb?.let { Color(it) }
            }
        }
    }
}
