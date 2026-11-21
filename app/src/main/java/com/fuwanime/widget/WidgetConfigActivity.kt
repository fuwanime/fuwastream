package com.fuwanime.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.SizeF
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuwanime.FuwaApp
import com.fuwanime.R
import com.fuwanime.data.local.ThemeMode
import com.fuwanime.ui.theme.FuwaTheme
import com.fuwanime.util.localizedContext
import kotlinx.coroutines.launch

/**
 * Shown by the launcher when a widget is added (android:configure in each *_widget_info.xml): the
 * user picks a size preset instead of being forced into the default. Confirming applies the size,
 * triggers the Glance update and returns RESULT_OK; backing out returns RESULT_CANCELED so the
 * launcher removes the half-added widget.
 */
class WidgetConfigActivity : ComponentActivity() {

    override fun attachBaseContext(base: Context) = super.attachBaseContext(localizedContext(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Cancel until confirmed: the launcher drops the widget if the activity finishes without OK.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || info == null) {
            finish()
            return
        }
        val widgetName = resources.getString(widgetNameRes(info))
        val settingsFlow = (application as FuwaApp).container.settingsStore.settings
        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = null)
            val current = settings ?: return@setContent
            val dark = when (current.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }
            val view = LocalView.current
            LaunchedEffect(dark) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            FuwaTheme(current) {
                ConfigScreen(
                    widgetName = widgetName,
                    onConfirm = { preset -> confirm(appWidgetId, info, preset) },
                    onCancel = { finish() },
                )
            }
        }
    }

    /** Applies the chosen preset, refreshes the Glance composition, then hands the widget back to the launcher. */
    private fun confirm(appWidgetId: Int, info: AppWidgetProviderInfo, preset: SizePreset) {
        val manager = AppWidgetManager.getInstance(this)
        // OPTION_APPWIDGET_SIZES is the platform's responsive-size list (Android 12+ launchers snap the
        // widget to it); the min/max bounds point Glance's pre-12 size picker at the same preset.
        val options = Bundle().apply {
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(preset.width.value, preset.height.value)))
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, preset.width.value.toInt())
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, preset.width.value.toInt())
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, preset.height.value.toInt())
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, preset.height.value.toInt())
        }
        manager.updateAppWidgetOptions(appWidgetId, options)
        val widget = when (info.provider.className) {
            ContinueWidgetReceiver::class.java.name -> ContinueWidget()
            FavoritesWidgetReceiver::class.java.name -> FavoritesWidget()
            ScheduleWidgetReceiver::class.java.name -> ScheduleWidget()
            StatsWidgetReceiver::class.java.name -> StatsWidget()
            WeeklyStatsWidgetReceiver::class.java.name -> WeeklyStatsWidget()
            else -> null
        }
        // The app scope outlives this activity: finish() below would cancel a lifecycleScope update.
        val app = application as FuwaApp
        val glanceId = GlanceAppWidgetManager(app).getGlanceIdBy(appWidgetId)
        widget?.let { w -> app.container.appScope.launch { runCatching { w.update(app, glanceId) } } }
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }

    @StringRes
    private fun widgetNameRes(info: AppWidgetProviderInfo): Int = when (info.provider.className) {
        ContinueWidgetReceiver::class.java.name -> R.string.widget_continue_name
        FavoritesWidgetReceiver::class.java.name -> R.string.widget_favorites_name
        ScheduleWidgetReceiver::class.java.name -> R.string.widget_schedule_name
        StatsWidgetReceiver::class.java.name -> R.string.widget_stats_name
        WeeklyStatsWidgetReceiver::class.java.name -> R.string.widget_weekly_stats_name
        else -> R.string.app_name
    }
}

/** The responsive breakpoints, offered as friendly presets; Sedang matches the providers' default. */
private enum class SizePreset(val width: Dp, val height: Dp, @StringRes val label: Int, @StringRes val desc: Int) {
    SMALL(110.dp, 110.dp, R.string.widget_config_small, R.string.widget_config_small_desc),
    MEDIUM(250.dp, 110.dp, R.string.widget_config_medium, R.string.widget_config_medium_desc),
    LARGE(250.dp, 250.dp, R.string.widget_config_large, R.string.widget_config_large_desc),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigScreen(widgetName: String, onConfirm: (SizePreset) -> Unit, onCancel: () -> Unit) {
    var selected by remember { mutableStateOf(SizePreset.MEDIUM) }
    val colors = MaterialTheme.colorScheme
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.widget_config_title)) }) },
        containerColor = colors.background,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.widget_config_subtitle, widgetName),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            SizePreset.entries.forEach { preset ->
                PresetCard(preset, selected = preset == selected, onClick = { selected = preset })
            }
            Text(
                stringResource(R.string.widget_config_hint),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.widget_config_cancel))
                }
                Button(onClick = { onConfirm(selected) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.widget_config_confirm))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Selectable tonal card, matching the app's filled-card look; the selected preset gets primaryContainer. */
@Composable
private fun PresetCard(preset: SizePreset, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) colors.primaryContainer else colors.surfaceContainerLow,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        border = if (selected) null else BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(preset.label), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(preset.desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                )
            }
        }
    }
}
