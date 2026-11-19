package com.fuwanime.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.fuwanime.R
import com.fuwanime.data.local.AppSettings
import com.fuwanime.data.local.ThemeMode
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme

/** Seed for the app's default Material 3 palette: a vivid, friendly blue. */
internal val BlueSeed = Color(0xFF2F6BFF)

/**
 * Generated Material 3 blue schemes (TonalSpot). Static so the Glance widgets can reuse them through
 * [androidx.glance.material3.ColorProviders]. The dark scheme gets deep navy tonal surfaces for the
 * expressive look; the light scheme stays a clean blue-tinted paper.
 */
internal val DarkScheme = dynamicColorScheme(seedColor = BlueSeed, isDark = true, style = PaletteStyle.TonalSpot).toNavy()
internal val LightScheme = dynamicColorScheme(seedColor = BlueSeed, isDark = false, style = PaletteStyle.TonalSpot)

/** Deep navy tonal surfaces (Material 3 Expressive depth cue) layered under the generated blue roles. */
private fun ColorScheme.toNavy() = copy(
    background = Color(0xFF0A1120),
    surface = Color(0xFF0A1120),
    surfaceDim = Color(0xFF070D1A),
    surfaceBright = Color(0xFF2A3A5C),
    surfaceContainerLowest = Color(0xFF050A14),
    surfaceContainerLow = Color(0xFF0E1626),
    surfaceContainer = Color(0xFF121C30),
    surfaceContainerHigh = Color(0xFF18243C),
    surfaceContainerHighest = Color(0xFF1F2D4A),
    surfaceVariant = Color(0xFF1E2A44),
    onSurfaceVariant = Color(0xFFB6C2DC),
    outlineVariant = Color(0xFF2A3A5C),
)

private fun ColorScheme.toAmoled() = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF060B16),
    surfaceContainer = Color(0xFF0B1220),
    surfaceContainerHigh = Color(0xFF121C30),
    surfaceContainerHighest = Color(0xFF1A2740),
)

val PlusJakarta = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
)

private val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = PlusJakarta),
        displayMedium = displayMedium.copy(fontFamily = PlusJakarta),
        displaySmall = displaySmall.copy(fontFamily = PlusJakarta, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.03).em),
        headlineLarge = headlineLarge.copy(fontFamily = PlusJakarta, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em),
        headlineMedium = headlineMedium.copy(fontFamily = PlusJakarta, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em),
        headlineSmall = headlineSmall.copy(fontFamily = PlusJakarta, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em),
        titleLarge = titleLarge.copy(fontFamily = PlusJakarta, fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontFamily = PlusJakarta, fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontFamily = PlusJakarta, fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(fontFamily = PlusJakarta),
        bodyMedium = bodyMedium.copy(fontFamily = PlusJakarta),
        bodySmall = bodySmall.copy(fontFamily = PlusJakarta),
        labelLarge = labelLarge.copy(fontFamily = PlusJakarta, fontWeight = FontWeight.SemiBold),
        labelMedium = labelMedium.copy(fontFamily = PlusJakarta),
        labelSmall = TextStyle(fontFamily = PlusJakarta, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
    )
}

/** Expressive shape scale: fully rounded buttons/chips come from component defaults; cards are generous. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Whether the app is currently in dark mode, so content-seeded subtrees (anime color) can match. */
val LocalFuwaDark = compositionLocalOf { true }

@Composable
fun FuwaTheme(settings: AppSettings = AppSettings(), content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val context = LocalContext.current
    val base = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }
    val scheme = if (dark && settings.amoledBlack) base.toAmoled() else base

    CompositionLocalProvider(LocalFuwaDark provides dark) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
