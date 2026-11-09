package com.fuwanime.ui.settings

import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.rounded.Explicit
import androidx.compose.material.icons.rounded.DataSaverOn
import kotlinx.coroutines.flow.first
import android.content.res.Resources
import androidx.compose.ui.res.stringResource
import com.fuwanime.R
import com.fuwanime.data.local.BackupPrefs
import com.fuwanime.data.backup.BackupWorker
import com.fuwanime.data.backup.BackupRepository
import com.fuwanime.data.backup.BackupOutcome
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Autorenew
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.provider.DocumentsContract
import android.net.Uri
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxHeight
import com.fuwanime.ui.components.readableWidth
import com.fuwanime.ui.components.frostedStyle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeState
import android.os.Build
import android.app.Activity
import androidx.compose.material.icons.rounded.Language
import com.fuwanime.util.currentLanguageTag
import com.fuwanime.util.setAppLanguage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.ManageSearch
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fuwanime.BuildConfig
import com.fuwanime.data.DownloadRepository
import com.fuwanime.data.LibraryRepository
import com.fuwanime.data.local.AppSettings
import com.fuwanime.data.local.SettingsStore
import com.fuwanime.data.local.ThemeMode
import com.fuwanime.util.fuwaViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    val store: SettingsStore,
    private val library: LibraryRepository,
    private val downloads: DownloadRepository,
    val backup: BackupRepository,
    private val refreshHomeSurfaces: () -> Unit = {},
) : ViewModel() {
    val backupPrefs: StateFlow<BackupPrefs?> = store.backup.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val settings: StateFlow<AppSettings> = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun update(block: suspend SettingsStore.() -> Unit) = viewModelScope.launch { store.block() }
    fun clearHistory() = viewModelScope.launch { library.clearHistory() }
    fun clearSearches() = viewModelScope.launch { library.clearSearches() }

    /** Deletes favourites, history, searches, downloads and preferences — everything local. */
    fun deleteAllData(onDone: () -> Unit = {}) = viewModelScope.launch {
        downloads.clearAll()
        library.clearAll()
        store.clearUserData()
        refreshHomeSurfaces()
        onDone()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(onBack: () -> Unit, onOpenLegal: (String) -> Unit) {
    val vm: SettingsViewModel = fuwaViewModel { c, _ ->
        SettingsViewModel(c.settingsStore, c.libraryRepository, c.downloadRepository, c.backupRepository, c::refreshHomeSurfaces)
    }
    val s by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val backupPrefs by vm.backupPrefs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val res = context.resources
    val say: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }
    val restore = rememberRestoreFlow(vm.backup, onRestored = {}, onMessage = say)
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Keep access across reboots; without this the daily worker loses the folder.
        val granted = runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }.isSuccess
        if (!granted) {
            say(res.getString(R.string.mine_backup_folder_unusable))
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            vm.store.setBackupTree(uri.toString())
            if (vm.store.backup.first().auto) BackupWorker.schedule(context)
            say(backupMessage(res, vm.backup.backupNow()))
        }
    }

    val pickDownloadFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // The move happens later in the background, so access must survive restarts.
        val granted = runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }.isSuccess
        if (!granted) say(res.getString(R.string.mine_backup_folder_unusable))
        else scope.launch { vm.store.setDownloadFolder(uri.toString()) }
    }
    var chooseLocation by remember { mutableStateOf(false) }
    if (chooseLocation) {
        AlertDialog(
            onDismissRequest = { chooseLocation = false },
            title = { Text(stringResource(R.string.mine_download_location)) },
            text = {
                Column {
                    Text(stringResource(R.string.mine_download_location_desc))
                    val ask = s.downloadAsk
                    Row(Modifier.fillMaxWidth().clickable { scope.launch { vm.store.setDownloadAsk(!ask) } }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = ask, onCheckedChange = { scope.launch { vm.store.setDownloadAsk(it) } })
                        Text(stringResource(R.string.mine_download_ask))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { chooseLocation = false; pickDownloadFolder.launch(null) }) { Text(stringResource(R.string.mine_download_location_pick)) } },
            dismissButton = { TextButton(onClick = { chooseLocation = false; scope.launch { vm.store.setDownloadFolder(null) } }) { Text(stringResource(R.string.mine_download_location_app)) } },
        )
    }

    val hazeState = remember { HazeState() }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.mine_settings)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.mine_back)) } },
                scrollBehavior = scrollBehavior,
                // Transparent so the frosted layer shows the list scrolling underneath.
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                modifier = Modifier.hazeEffect(hazeState, frostedStyle()),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxHeight().readableWidth().hazeSource(hazeState),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp),
        ) {
            item { Group(stringResource(R.string.mine_group_display)) }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Rounded.DarkMode, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 16.dp, start = 8.dp))
                        Text(stringResource(R.string.mine_theme), style = MaterialTheme.typography.bodyLarge)
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.padding(top = 12.dp)) {
                        val modes = listOf(ThemeMode.SYSTEM to stringResource(R.string.mine_theme_system), ThemeMode.LIGHT to stringResource(R.string.mine_theme_light), ThemeMode.DARK to stringResource(R.string.mine_theme_dark))
                        modes.forEachIndexed { i, (mode, label) ->
                            SegmentedButton(
                                selected = s.themeMode == mode,
                                onClick = { vm.update { setThemeMode(mode) } },
                                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                            ) { Text(label) }
                        }
                    }
                }
            }
            item {
                var languageTag by remember { mutableStateOf(currentLanguageTag(context)) }
                // Language names stay in their own language on purpose.
                val languages = listOf(null to stringResource(R.string.mine_language_system), "id" to "Bahasa Indonesia", "ms" to "Bahasa Melayu", "en" to "English", "zh-CN" to "中文（简体）")
                ChoiceRow(Icons.Rounded.Language, stringResource(R.string.mine_language), languages.map { it.first }, languageTag, { tag -> languages.first { it.first == tag }.second }) { tag ->
                    languageTag = tag
                    (context as? Activity)?.let { setAppLanguage(it, tag) }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    SwitchRow(Icons.Rounded.Palette, stringResource(R.string.mine_dynamic_color), stringResource(R.string.mine_dynamic_color_desc), s.dynamicColor) {
                        vm.update { setDynamicColor(it) }
                    }
                }
            }
            item {
                SwitchRow(Icons.Rounded.Contrast, stringResource(R.string.mine_amoled), stringResource(R.string.mine_amoled_desc), s.amoledBlack) {
                    vm.update { setAmoledBlack(it) }
                }
            }

            item { Group(stringResource(R.string.mine_group_player)) }
            item {
                ChoiceRow(Icons.Rounded.HighQuality, stringResource(R.string.mine_default_quality), listOf(360, 480, 720, 1080), s.preferredQuality, { "${it}p" }) {
                    vm.update { setPreferredQuality(it) }
                }
            }
            item {
                ChoiceRow(
                    Icons.Rounded.SignalCellularAlt,
                    stringResource(R.string.mine_mobile_quality),
                    listOf(0, 360, 480, 720),
                    s.mobileDataQuality,
                    { if (it == 0) res.getString(R.string.mine_follow_default) else "${it}p" },
                ) {
                    vm.update { setMobileDataQuality(it) }
                }
            }
            item {
                SwitchRow(Icons.Rounded.SkipNext, stringResource(R.string.mine_autoplay), stringResource(R.string.mine_autoplay_desc), s.autoPlayNext) {
                    vm.update { setAutoPlayNext(it) }
                }
            }
            item {
                SwitchRow(Icons.Rounded.PictureInPictureAlt, stringResource(R.string.mine_auto_pip), stringResource(R.string.mine_auto_pip_desc), s.autoPip) {
                    vm.update { setAutoPip(it) }
                }
            }
            item {
                SwitchRow(Icons.Rounded.Headphones, stringResource(R.string.mine_bg_audio), stringResource(R.string.mine_bg_audio_desc), s.backgroundAudio) {
                    vm.update { setBackgroundAudio(it) }
                }
            }
            item {
                SwitchRow(
                    Icons.Rounded.FastForward,
                    stringResource(R.string.mine_skip_op),
                    stringResource(R.string.mine_skip_op_desc),
                    s.autoSkipOp,
                ) {
                    vm.update { setAutoSkipOp(it) }
                }
            }
            item {
                SwitchRow(
                    Icons.Rounded.FastForward,
                    stringResource(R.string.mine_jump85),
                    stringResource(R.string.mine_jump85_desc),
                    s.jumpAheadButton,
                ) {
                    vm.update { setJumpAheadButton(it) }
                }
            }
            item {
                ChoiceRow(Icons.Rounded.TouchApp, stringResource(R.string.mine_double_tap), listOf(5, 10, 15, 30), s.doubleTapSeekSeconds, { res.getString(R.string.mine_seconds_short, it) }) {
                    vm.update { setDoubleTapSeekSeconds(it) }
                }
            }

            item { Group(stringResource(R.string.mine_group_notifications)) }
            item {
                SwitchRow(
                    Icons.Rounded.NotificationsActive,
                    stringResource(R.string.mine_notify_new),
                    stringResource(R.string.mine_notify_new_desc),
                    s.notifyNewEpisodes,
                ) {
                    vm.update { setNotifyNewEpisodes(it) }
                }
            }

            item { Group(stringResource(R.string.mine_group_downloads)) }
            item {
                ChoiceRow(Icons.Rounded.Download, stringResource(R.string.mine_download_quality), listOf(360, 480, 720, 1080), s.downloadQuality, { "${it}p" }) {
                    vm.update { setDownloadQuality(it) }
                }
            }
            item {
                SwitchRow(Icons.Rounded.Wifi, stringResource(R.string.mine_wifi_only), stringResource(R.string.mine_wifi_only_desc), s.downloadWifiOnly) {
                    vm.update { setDownloadWifiOnly(it) }
                }
            }
            item {
                ActionRow(
                    Icons.Rounded.Folder,
                    stringResource(R.string.mine_download_location),
                    when {
                        s.downloadAsk -> stringResource(R.string.mine_download_location_ask)
                        s.downloadFolder != null -> folderName(res, Uri.parse(s.downloadFolder!!))
                        else -> stringResource(R.string.mine_download_location_app)
                    },
                ) { chooseLocation = true }
            }

            item { Group(stringResource(R.string.manga_settings_group)) }
            item {
                val saver by vm.store.mangaDataSaver.collectAsStateWithLifecycle(false)
                SwitchRow(Icons.Rounded.DataSaverOn, stringResource(R.string.manga_settings_data_saver), stringResource(R.string.manga_settings_data_saver_desc), saver) {
                    vm.update { setMangaDataSaver(it) }
                }
            }
            item {
                val mature by vm.store.mangaMature.collectAsStateWithLifecycle(false)
                SwitchRow(Icons.Rounded.Explicit, stringResource(R.string.manga_settings_mature), stringResource(R.string.manga_settings_mature_desc), mature) {
                    vm.update { setMangaMature(it) }
                }
            }

            item { Group(stringResource(R.string.mine_group_backup)) }
            backupPrefs?.let { b ->
                item {
                    ActionRow(Icons.Rounded.Folder, stringResource(R.string.mine_backup_folder), b.treeUri?.let { folderName(res, Uri.parse(it)) } ?: stringResource(R.string.mine_backup_folder_none)) {
                        pickFolder.launch(null)
                    }
                }
                if (b.treeUri != null) {
                    item {
                        SwitchRow(Icons.Rounded.Autorenew, stringResource(R.string.mine_backup_auto), stringResource(R.string.mine_backup_auto_desc), b.auto) { on ->
                            scope.launch { vm.store.setBackupAuto(on) }
                            if (on) BackupWorker.schedule(context) else BackupWorker.cancel(context)
                        }
                    }
                }
                item {
                    ActionRow(Icons.Rounded.Backup, stringResource(R.string.mine_backup_now), backupStatusLabel(res, b)) {
                        scope.launch { say(backupMessage(res, vm.backup.backupNow())) }
                    }
                }
                item { ActionRow(Icons.Rounded.Restore, stringResource(R.string.mine_restore_file), stringResource(R.string.mine_restore_file_desc), restore) }
            }

            item { Group(stringResource(R.string.mine_group_data)) }
            item {
                ActionRow(Icons.Rounded.DeleteSweep, stringResource(R.string.mine_clear_watch_history), stringResource(R.string.mine_clear_watch_history_desc)) {
                    confirm = res.getString(R.string.mine_clear_watch_history_confirm) to {
                        vm.clearHistory()
                        scope.launch { snackbar.showSnackbar(res.getString(R.string.mine_history_cleared)) }
                    }
                }
            }
            item {
                ActionRow(Icons.Rounded.ManageSearch, stringResource(R.string.mine_clear_searches), null) {
                    vm.clearSearches()
                    scope.launch { snackbar.showSnackbar(res.getString(R.string.mine_searches_cleared)) }
                }
            }
            item {
                ActionRow(
                    Icons.Rounded.DeleteForever,
                    stringResource(R.string.mine_delete_all),
                    stringResource(R.string.mine_delete_all_desc),
                ) {
                    confirm = res.getString(R.string.mine_delete_all_confirm) to {
                        vm.deleteAllData {
                            scope.launch { snackbar.showSnackbar(res.getString(R.string.mine_all_deleted)) }
                        }
                    }
                }
            }

            item { Group(stringResource(R.string.mine_group_legal)) }
            item {
                ActionRow(Icons.Rounded.PrivacyTip, stringResource(R.string.mine_privacy_policy), stringResource(R.string.mine_privacy_policy_desc)) {
                    onOpenLegal("privacy")
                }
            }
            item {
                ActionRow(Icons.Rounded.Description, stringResource(R.string.mine_terms), stringResource(R.string.mine_terms_desc)) {
                    onOpenLegal("terms")
                }
            }
            item {
                ActionRow(Icons.Rounded.Person, stringResource(R.string.mine_developer_info), stringResource(R.string.mine_developer_info_desc)) {
                    onOpenLegal("about")
                }
            }

            item { Group(stringResource(R.string.mine_group_about)) }
            item {
                ActionRow(Icons.Rounded.Info, stringResource(R.string.mine_version, BuildConfig.VERSION_NAME), stringResource(R.string.mine_version_desc), onClick = null)
            }
            item {
                ActionRow(Icons.Rounded.AutoAwesome, stringResource(R.string.mine_third_party), stringResource(R.string.mine_third_party_desc), onClick = null)
            }
        }
    }

    confirm?.let { (title, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(title) },
            confirmButton = { TextButton(onClick = { action(); confirm = null }) { Text(stringResource(R.string.mine_delete)) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.mine_cancel)) } },
        )
    }
}

@Composable
private fun Group(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) {
            { Text(subtitle) }
        } else null,
        leadingContent = { Icon(icon, null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onChange(!checked) }.padding(horizontal = 8.dp),
    )
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String?, onClick: (() -> Unit)?) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) {
            { Text(subtitle) }
        } else null,
        leadingContent = { Icon(icon, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = (if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 8.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(
    icon: ImageVector,
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, end = 16.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge)
        }
        FlowRow(Modifier.padding(start = 48.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { opt ->
                FilterChip(selected = opt == selected, onClick = { onSelect(opt) }, label = { Text(label(opt)) })
            }
        }
    }
}

private fun backupMessage(res: Resources, outcome: BackupOutcome): String = when (outcome) {
    BackupOutcome.Written -> res.getString(R.string.mine_backup_written)
    BackupOutcome.SkippedEmpty -> res.getString(R.string.mine_backup_skipped_empty)
    BackupOutcome.NoFolder -> res.getString(R.string.mine_backup_no_folder)
    is BackupOutcome.Failed -> backupErrorLabel(res, outcome.code)
}

/** "Download/Anime" from a tree id like "primary:Download/Anime". */
internal fun folderName(res: Resources, tree: Uri): String =
    runCatching { DocumentsContract.getTreeDocumentId(tree).substringAfterLast(':') }.getOrNull()?.ifEmpty { null } ?: res.getString(R.string.mine_backup_folder_storage)
