package com.fuwanime.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.fuwanime.data.LibraryRepository
import com.fuwanime.ui.navigation.Shortcuts
import kotlinx.coroutines.flow.first

/** Launcher surfaces that mirror "continue watching": the dynamic shortcut and the widget. */
object HomeSurfaces {
    suspend fun refresh(context: Context, library: LibraryRepository) {
        Shortcuts.pushContinue(context, library.continueWatching(1).first().firstOrNull())
        runCatching { ContinueWidget().updateAll(context) }
    }
}
