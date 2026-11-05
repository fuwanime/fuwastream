package com.fuwanime

import android.Manifest
import android.app.PictureInPictureUiState
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuwanime.data.local.ThemeMode
import com.fuwanime.ui.legal.AgeGateScreen
import com.fuwanime.ui.navigation.FuwaNavHost
import com.fuwanime.ui.navigation.LaunchTarget
import com.fuwanime.ui.navigation.Shortcuts
import com.fuwanime.ui.navigation.TAB_EXPLORE_SEARCH
import com.fuwanime.ui.navigation.TAB_SCHEDULE
import com.fuwanime.ui.navigation.isFreshLaunch
import com.fuwanime.ui.navigation.parseLaunchTarget
import com.fuwanime.ui.player.LocalPipController
import com.fuwanime.ui.player.PipController
import com.fuwanime.ui.theme.FuwaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var pip: PipController

    /** Where a notification, shortcut or widget tap wants to land; consumed once by the nav host. */
    private val pendingTarget = mutableStateOf<LaunchTarget?>(null)

    private fun Intent.launchTarget() = parseLaunchTarget(
        getStringExtra(EXTRA_OPEN_ANIME_ID), getStringExtra(EXTRA_OPEN_TAB), getStringExtra(EXTRA_RESUME_EPISODE_ID),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pip = PipController(this)
        if (intent != null && isFreshLaunch(savedInstanceState != null, intent.flags)) pendingTarget.value = intent.launchTarget()
        val store = (application as FuwaApp).container.settingsStore
        val settingsFlow = store.settings
        val ageFlow = store.ageConfirmed

        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = null)
            val ageConfirmed by ageFlow.collectAsStateWithLifecycle(initialValue = null)
            // Wait for persisted settings so the first frame already uses the right theme.
            val current = settings ?: return@setContent
            val confirmed = ageConfirmed ?: return@setContent
            val scope = rememberCoroutineScope()

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
            // The notification prompt only appears after the age gate is cleared.
            LaunchedEffect(confirmed) {
                if (confirmed) requestNotificationPermissionIfNeeded()
            }

            FuwaTheme(current) {
                CompositionLocalProvider(LocalPipController provides pip) {
                    if (confirmed) {
                        FuwaNavHost(
                            initialTarget = pendingTarget.value,
                            onInitialTargetConsumed = { pendingTarget.value = null },
                        )
                    } else {
                        AgeGateScreen(
                            onConfirm = { scope.launch { store.confirmAge() } },
                            onExit = { finish() },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask: taps on notifications, shortcuts and the widget while running arrive here.
        intent.launchTarget()?.let { pendingTarget.value = it }
    }

    override fun onStart() {
        super.onStart()
        Shortcuts.publishFixed(this)
        (application as FuwaApp).container.refreshHomeSurfaces()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        pip.onUserLeaveHint()
    }

    override fun onPictureInPictureUiStateChanged(pipState: PictureInPictureUiState) {
        super.onPictureInPictureUiStateChanged(pipState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) pip.onPipUiState(pipState.isTransitioningToPip)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST)
        }
    }

    companion object {
        const val EXTRA_OPEN_ANIME_ID = "open_anime_id"
        /** [TAB_EXPLORE_SEARCH] or [TAB_SCHEDULE]. */
        const val EXTRA_OPEN_TAB = "open_tab"
        const val EXTRA_RESUME_EPISODE_ID = "resume_episode_id"
        private const val NOTIFICATION_REQUEST = 1001
    }
}
