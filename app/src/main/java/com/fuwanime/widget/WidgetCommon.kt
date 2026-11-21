package com.fuwanime.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProviders
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.fuwanime.R
import com.fuwanime.ui.theme.DarkScheme
import com.fuwanime.ui.theme.LightScheme
import java.io.File

/** Responsive breakpoints shared by every widget: 2x2, 4x2 and 4x4. */
internal val widgetSmall = DpSize(110.dp, 110.dp)
internal val widgetWide = DpSize(250.dp, 110.dp)
internal val widgetTall = DpSize(250.dp, 250.dp)

/**
 * Per-widget state storage. Glance keys its stores by appWidgetId alone; prefixing the widget type
 * keeps two different widgets from sharing a store if a launcher ever reuses an id without
 * broadcasting the deletion (the schedule widget once showed continue content on such a reuse).
 */
internal class WidgetStateDefinition(private val widgetName: String) : GlanceStateDefinition<Preferences> {
    override fun getLocation(context: Context, fileKey: String): File =
        PreferencesGlanceStateDefinition.getLocation(context, "$widgetName-$fileKey")

    override suspend fun getDataStore(context: Context, fileKey: String): DataStore<Preferences> =
        PreferencesGlanceStateDefinition.getDataStore(context, "$widgetName-$fileKey")
}

/** The app's own palette where Material You is unavailable (Android 11 and lower). */
private val AppColors = ColorProviders(LightScheme, DarkScheme)

/**
 * GlanceTheme using the app's own blue Material 3 palette on EVERY Android version. Widgets must look
 * like the app (navy/blue tonal), not follow the launcher wallpaper — dynamic Material You color made
 * widgets "change" appearance on Android 12+ (e.g. Android 16) and clash with the app design.
 */
@Composable
internal fun WidgetTheme(content: @Composable () -> Unit) {
    GlanceTheme(colors = AppColors) {
        content()
    }
}

// Glance 1.1.1 exposes only the original M3 color roles (surfaceContainer* do not exist yet), so the
// tonal layering uses surface as the base and surfaceVariant/primaryContainer/secondaryContainer cards.
//
// cornerRadius() is a no-op below Android 12, so on older versions the rounded backgrounds come from
// shape drawables in the app's static palette (res/values-night mirrors DarkScheme). On 12+ the theme
// roles keep following Material You and cornerRadius clips the views.

/** The widget's tonal base, matching the app's surface background. */
@Composable
internal fun GlanceModifier.widgetSurface(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        background(GlanceTheme.colors.surface).cornerRadius(R.dimen.widget_radius)
    } else {
        background(ImageProvider(R.drawable.widget_bg_surface))
    }

/** A tonal inner card, the widget stand-in for the app's surfaceContainer cards. */
@Composable
internal fun GlanceModifier.widgetCard(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        background(GlanceTheme.colors.surfaceVariant).cornerRadius(R.dimen.widget_radius_card)
    } else {
        background(ImageProvider(R.drawable.widget_bg_card))
    }

@Composable
internal fun GlanceModifier.widgetPrimaryCard(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        background(GlanceTheme.colors.primaryContainer).cornerRadius(R.dimen.widget_radius_card)
    } else {
        background(ImageProvider(R.drawable.widget_bg_primary))
    }

@Composable
internal fun GlanceModifier.widgetSecondaryCard(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        background(GlanceTheme.colors.secondaryContainer).cornerRadius(R.dimen.widget_radius_card)
    } else {
        background(ImageProvider(R.drawable.widget_bg_secondary))
    }

@Composable
internal fun GlanceModifier.widgetTertiaryCard(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        background(GlanceTheme.colors.tertiaryContainer).cornerRadius(R.dimen.widget_radius_card)
    } else {
        background(ImageProvider(R.drawable.widget_bg_tertiary))
    }

/** Poster placeholder with the app's softer thumbnail corners. */
@Composable
internal fun GlanceModifier.widgetPosterBackground(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        background(GlanceTheme.colors.surfaceVariant).cornerRadius(R.dimen.widget_radius_poster)
    } else {
        background(ImageProvider(R.drawable.widget_bg_poster))
    }

/** Fully rounded pills, like the app's Pill component. */
@Composable
internal fun GlanceModifier.widgetPillPrimary(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        background(GlanceTheme.colors.primaryContainer).cornerRadius(R.dimen.widget_radius_pill)
    } else {
        background(ImageProvider(R.drawable.widget_bg_pill_primary))
    }

@Composable
internal fun GlanceModifier.widgetPillSecondary(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        background(GlanceTheme.colors.secondaryContainer).cornerRadius(R.dimen.widget_radius_pill)
    } else {
        background(ImageProvider(R.drawable.widget_bg_pill_secondary))
    }

/** Fully rounded primary button for empty-state actions. */
@Composable
internal fun GlanceModifier.widgetButton(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        background(GlanceTheme.colors.primary).cornerRadius(R.dimen.widget_radius_button)
    } else {
        background(ImageProvider(R.drawable.widget_bg_button))
    }

/** The small primary accent bar before section titles, mirroring the app's SectionHeader. */
@Composable
internal fun GlanceModifier.widgetAccentBar(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        background(GlanceTheme.colors.primary).cornerRadius(2.dp)
    } else {
        background(ImageProvider(R.drawable.widget_bg_accent))
    }

/** Section header mirroring the app's SectionHeader: a primary accent bar before the bold title. */
@Composable
internal fun WidgetHeader(text: String, modifier: GlanceModifier = GlanceModifier, fontSize: TextUnit = 13.sp) {
    Row(modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.width(4.dp).height(16.dp).widgetAccentBar()) {}
        Spacer(GlanceModifier.width(8.dp))
        Text(text, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.primary, fontSize = fontSize, fontWeight = FontWeight.Bold))
    }
}

/** Fully rounded tonal pill for times and type labels; [primary] picks the primaryContainer pair. */
@Composable
internal fun WidgetPill(text: String, primary: Boolean = false, fontSize: TextUnit = 11.sp) {
    Text(
        text,
        maxLines = 1,
        style = TextStyle(
            color = if (primary) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSecondaryContainer,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
        ),
        modifier = (if (primary) GlanceModifier.widgetPillPrimary() else GlanceModifier.widgetPillSecondary())
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** Poster thumbnail; the bitmap's corners are rounded at load time so the art stays rounded below Android 12. */
@Composable
internal fun WidgetPoster(title: String, poster: Bitmap?, modifier: GlanceModifier) {
    Box(modifier.widgetPosterBackground(), contentAlignment = Alignment.Center) {
        if (poster != null) {
            Image(ImageProvider(poster), title, GlanceModifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}

/** Rounds the poster's corners in the bitmap itself; Glance cannot clip views below Android 12. */
internal fun roundPosterCorners(bitmap: Bitmap): Bitmap {
    val radius = bitmap.height * 0.2f
    val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
    Canvas(out).drawRoundRect(RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()), radius, radius, paint)
    return out
}

/** Primary progress bar on a tonal track, like the app's episode progress. */
@Composable
internal fun WidgetProgress(value: Float, height: Dp, track: ColorProvider) = LinearProgressIndicator(
    progress = value,
    modifier = GlanceModifier.fillMaxWidth().height(height).cornerRadius(height / 2),
    color = GlanceTheme.colors.primary,
    backgroundColor = track,
)

/** Centered message with a fully rounded primary action pill, matching the app's empty states. */
@Composable
internal fun WidgetEmptyState(message: String, actionLabel: String, action: Action, hint: String? = null) {
    Column(
        GlanceModifier.fillMaxSize().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            message,
            maxLines = 2,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
        )
        if (hint != null) {
            Spacer(GlanceModifier.height(4.dp))
            Text(hint, maxLines = 2, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp, textAlign = TextAlign.Center))
        }
        Spacer(GlanceModifier.height(12.dp))
        Text(
            actionLabel,
            style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            modifier = GlanceModifier.widgetButton().padding(horizontal = 16.dp, vertical = 10.dp).clickable(action),
        )
    }
}
