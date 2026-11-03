package com.fuwanime.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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

// "Yozakura" palette: lavender primary, sakura secondary, sky tertiary on a near-black cinematic night.
private val DarkScheme = darkColorScheme(
    primary = Color(0xFFD4BBFF),
    onPrimary = Color(0xFF3A1B76),
    primaryContainer = Color(0xFF52348E),
    onPrimaryContainer = Color(0xFFEBDCFF),
    inversePrimary = Color(0xFF6A4BC4),
    secondary = Color(0xFFFFB0C9),
    onSecondary = Color(0xFF5E1133),
    secondaryContainer = Color(0xFF2A2238),
    onSecondaryContainer = Color(0xFFFFD9E3),
    tertiary = Color(0xFF8FD3F2),
    onTertiary = Color(0xFF003548),
    tertiaryContainer = Color(0xFF004D67),
    onTertiaryContainer = Color(0xFFC1E8FF),
    background = Color(0xFF0B0A0F),
    onBackground = Color(0xFFE8E0EE),
    surface = Color(0xFF0B0A0F),
    onSurface = Color(0xFFE8E0EE),
    surfaceVariant = Color(0xFF2B2734),
    onSurfaceVariant = Color(0xFFA39DAE),
    surfaceTint = Color(0xFFD4BBFF),
    inverseSurface = Color(0xFFE8E0EE),
    inverseOnSurface = Color(0xFF322F38),
    outline = Color(0xFF958EA1),
    outlineVariant = Color(0xFF2A2633),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceBright = Color(0xFF36323D),
    surfaceDim = Color(0xFF0B0A0F),
    surfaceContainerLowest = Color(0xFF08070B),
    surfaceContainerLow = Color(0xFF16141C),
    surfaceContainer = Color(0xFF1A1721),
    surfaceContainerHigh = Color(0xFF1F1C27),
    surfaceContainerHighest = Color(0xFF312D39),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF6A4BC4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEBDCFF),
    onPrimaryContainer = Color(0xFF240061),
    inversePrimary = Color(0xFFD4BBFF),
    secondary = Color(0xFF9C3E5F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD9E3),
    onSecondaryContainer = Color(0xFF3E001C),
    tertiary = Color(0xFF006686),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC1E8FF),
    onTertiaryContainer = Color(0xFF001E2B),
    background = Color(0xFFFDF7FF),
    onBackground = Color(0xFF1D1A22),
    surface = Color(0xFFFDF7FF),
    onSurface = Color(0xFF1D1A22),
    surfaceVariant = Color(0xFFE8E0F0),
    onSurfaceVariant = Color(0xFF4A4555),
    surfaceTint = Color(0xFF6A4BC4),
    inverseSurface = Color(0xFF322F38),
    inverseOnSurface = Color(0xFFF5EFF7),
    outline = Color(0xFF6A6474),
    outlineVariant = Color(0xFFCBC3D7),
    surfaceBright = Color(0xFFFDF7FF),
    surfaceDim = Color(0xFFDED8E1),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F1FA),
    surfaceContainer = Color(0xFFF2ECF5),
    surfaceContainerHigh = Color(0xFFECE6EF),
    surfaceContainerHighest = Color(0xFFE6E0E9),
)

private fun ColorScheme.toAmoled() = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0A0F),
    surfaceContainer = Color(0xFF121017),
    surfaceContainerHigh = Color(0xFF1B1821),
    surfaceContainerHighest = Color(0xFF25212C),
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

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

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

    MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
}
