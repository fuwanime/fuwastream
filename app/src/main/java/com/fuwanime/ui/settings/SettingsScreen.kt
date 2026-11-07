package com.fuwanime.ui.settings

import kotlinx.coroutines.flow.first
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
    val say: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }
    val restore = rememberRestoreFlow(vm.backup, onRestored = {}, onMessage = say)
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Keep access across reboots; without this the daily worker loses the folder.
        val granted = runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }.isSuccess
        if (!granted) {
            say("Folder ini tidak bisa dipakai untuk backup. Pilih folder lain.")
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            vm.store.setBackupTree(uri.toString())
            if (vm.store.backup.first().auto) BackupWorker.schedule(context)
            say(backupMessage(vm.backup.backupNow()))
        }
    }

    val hazeState = remember { HazeState() }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Pengaturan") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") } },
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
            item { Group("Tampilan") }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Rounded.DarkMode, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 16.dp, start = 8.dp))
                        Text("Tema", style = MaterialTheme.typography.bodyLarge)
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.padding(top = 12.dp)) {
                        val modes = listOf(ThemeMode.SYSTEM to "Sistem", ThemeMode.LIGHT to "Terang", ThemeMode.DARK to "Gelap")
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
                val languages = listOf(null to "Ikuti sistem", "id" to "Bahasa Indonesia", "ms" to "Bahasa Melayu", "en" to "English", "zh-CN" to "中文（简体）")
                ChoiceRow(Icons.Rounded.Language, "Bahasa", languages.map { it.first }, languageTag, { tag -> languages.first { it.first == tag }.second }) { tag ->
                    languageTag = tag
                    (context as? Activity)?.let { setAppLanguage(it, tag) }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    SwitchRow(Icons.Rounded.Palette, "Warna dinamis", "Ikuti warna wallpaper (Material You)", s.dynamicColor) {
                        vm.update { setDynamicColor(it) }
                    }
                }
            }
            item {
                SwitchRow(Icons.Rounded.Contrast, "Hitam pekat (AMOLED)", "Latar hitam murni saat mode gelap, hemat baterai", s.amoledBlack) {
                    vm.update { setAmoledBlack(it) }
                }
            }

            item { Group("Pemutar") }
            item {
                ChoiceRow(Icons.Rounded.HighQuality, "Kualitas default", listOf(360, 480, 720, 1080), s.preferredQuality, { "${it}p" }) {
                    vm.update { setPreferredQuality(it) }
                }
            }
            item {
                ChoiceRow(
                    Icons.Rounded.SignalCellularAlt,
                    "Kualitas di data seluler",
                    listOf(0, 360, 480, 720),
                    s.mobileDataQuality,
                    { if (it == 0) "Ikuti default" else "${it}p" },
                ) {
                    vm.update { setMobileDataQuality(it) }
                }
            }
            item {
                SwitchRow(Icons.Rounded.SkipNext, "Putar otomatis episode berikutnya", "Hitung mundur 6 detik di akhir episode", s.autoPlayNext) {
                    vm.update { setAutoPlayNext(it) }
                }
            }
            item {
                SwitchRow(Icons.Rounded.PictureInPictureAlt, "Picture-in-Picture otomatis", "Video mengecil saat kamu keluar aplikasi", s.autoPip) {
                    vm.update { setAutoPip(it) }
                }
            }
            item {
                SwitchRow(Icons.Rounded.Headphones, "Putar audio di latar", "Suara tetap jalan saat layar mati atau aplikasi ditinggal", s.backgroundAudio) {
                    vm.update { setBackgroundAudio(it) }
                }
            }
            item {
                SwitchRow(
                    Icons.Rounded.FastForward,
                    "Lewati OP otomatis",
                    "Untuk anime yang OP-nya sudah kamu tandai di pemutar",
                    s.autoSkipOp,
                ) {
                    vm.update { setAutoSkipOp(it) }
                }
            }
            item {
                SwitchRow(
                    Icons.Rounded.FastForward,
                    "Tombol +85 detik",
                    "Lompat 85 detik di awal episode yang OP-nya belum ditandai",
                    s.jumpAheadButton,
                ) {
                    vm.update { setJumpAheadButton(it) }
                }
            }
            item {
                ChoiceRow(Icons.Rounded.TouchApp, "Ketuk dua kali untuk lompat", listOf(5, 10, 15, 30), s.doubleTapSeekSeconds, { "${it}s" }) {
                    vm.update { setDoubleTapSeekSeconds(it) }
                }
            }

            item { Group("Notifikasi") }
            item {
                SwitchRow(
                    Icons.Rounded.NotificationsActive,
                    "Notifikasi episode baru",
                    "Cek anime favorit yang masih tayang setiap 6 jam",
                    s.notifyNewEpisodes,
                ) {
                    vm.update { setNotifyNewEpisodes(it) }
                }
            }

            item { Group("Unduhan") }
            item {
                ChoiceRow(Icons.Rounded.Download, "Kualitas download", listOf(360, 480, 720, 1080), s.downloadQuality, { "${it}p" }) {
                    vm.update { setDownloadQuality(it) }
                }
            }
            item {
                SwitchRow(Icons.Rounded.Wifi, "Download hanya lewat Wi-Fi", "Hemat kuota, download menunggu sampai tersambung Wi-Fi", s.downloadWifiOnly) {
                    vm.update { setDownloadWifiOnly(it) }
                }
            }

            item { Group("Cadangan") }
            backupPrefs?.let { b ->
                item {
                    ActionRow(Icons.Rounded.Folder, "Folder backup", b.treeUri?.let { folderName(Uri.parse(it)) } ?: "Belum dipilih") {
                        pickFolder.launch(null)
                    }
                }
                if (b.treeUri != null) {
                    item {
                        SwitchRow(Icons.Rounded.Autorenew, "Cadangkan otomatis", "Setiap hari", b.auto) { on ->
                            scope.launch { vm.store.setBackupAuto(on) }
                            if (on) BackupWorker.schedule(context) else BackupWorker.cancel(context)
                        }
                    }
                }
                item {
                    ActionRow(Icons.Rounded.Backup, "Cadangkan sekarang", backupStatusLabel(b)) {
                        scope.launch { say(backupMessage(vm.backup.backupNow())) }
                    }
                }
                item { ActionRow(Icons.Rounded.Restore, "Pulihkan dari file", "Gabung dengan data di HP ini", restore) }
            }

            item { Group("Data") }
            item {
                ActionRow(Icons.Rounded.DeleteSweep, "Hapus riwayat tontonan", "Progres semua episode akan hilang") {
                    confirm = "Hapus semua riwayat tontonan?" to {
                        vm.clearHistory()
                        scope.launch { snackbar.showSnackbar("Riwayat dihapus") }
                    }
                }
            }
            item {
                ActionRow(Icons.Rounded.ManageSearch, "Hapus riwayat pencarian", null) {
                    vm.clearSearches()
                    scope.launch { snackbar.showSnackbar("Riwayat pencarian dihapus") }
                }
            }
            item {
                ActionRow(
                    Icons.Rounded.DeleteForever,
                    "Hapus semua data",
                    "Favorit, riwayat, pencarian, pengaturan, dan file unduhan",
                ) {
                    confirm = "Hapus semua data di perangkat ini?" to {
                        vm.deleteAllData {
                            scope.launch { snackbar.showSnackbar("Semua data dihapus") }
                        }
                    }
                }
            }

            item { Group("Privasi & Legal") }
            item {
                ActionRow(Icons.Rounded.PrivacyTip, "Kebijakan Privasi", "Data apa yang disimpan dan apa yang tidak") {
                    onOpenLegal("privacy")
                }
            }
            item {
                ActionRow(Icons.Rounded.Description, "Ketentuan Layanan", "Aturan penggunaan dan lisensi aplikasi") {
                    onOpenLegal("terms")
                }
            }
            item {
                ActionRow(Icons.Rounded.Person, "Info Pengembang", "Kontak, lisensi, dan kredit konten") {
                    onOpenLegal("about")
                }
            }

            item { Group("Tentang") }
            item {
                ActionRow(Icons.Rounded.Info, "FuwaStream ${BuildConfig.VERSION_NAME}", "Fork komunitas dengan desain ulang Material 3", onClick = null)
            }
            item {
                ActionRow(Icons.Rounded.AutoAwesome, "Konten disediakan pihak ketiga", "Aplikasi ini tidak meng-host video apa pun", onClick = null)
            }
        }
    }

    confirm?.let { (title, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(title) },
            confirmButton = { TextButton(onClick = { action(); confirm = null }) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Batal") } },
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

private fun backupMessage(outcome: BackupOutcome): String = when (outcome) {
    BackupOutcome.Written -> "Data dicadangkan"
    BackupOutcome.SkippedEmpty -> "Folder ini sudah berisi backup. Pulihkan dulu, lalu backup akan berjalan otomatis"
    BackupOutcome.NoFolder -> "Pilih folder backup dulu"
    is BackupOutcome.Failed -> outcome.message
}

/** "Download/Anime" from a tree id like "primary:Download/Anime". */
private fun folderName(tree: Uri): String =
    runCatching { DocumentsContract.getTreeDocumentId(tree).substringAfterLast(':') }.getOrNull()?.ifEmpty { null } ?: "Penyimpanan"
