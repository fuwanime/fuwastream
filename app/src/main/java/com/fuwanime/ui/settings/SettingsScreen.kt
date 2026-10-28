package com.fuwanime.ui.settings

import android.os.Build
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
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.ManageSearch
import androidx.compose.material.icons.rounded.Palette
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
import com.fuwanime.data.LibraryRepository
import com.fuwanime.data.local.AppSettings
import com.fuwanime.data.local.SettingsStore
import com.fuwanime.data.local.ThemeMode
import com.fuwanime.util.fuwaViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(val store: SettingsStore, private val library: LibraryRepository) : ViewModel() {
    val settings: StateFlow<AppSettings> = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    fun update(block: suspend SettingsStore.() -> Unit) = viewModelScope.launch { store.block() }
    fun clearHistory() = viewModelScope.launch { library.clearHistory() }
    fun clearSearches() = viewModelScope.launch { library.clearSearches() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(onBack: () -> Unit) {
    val vm: SettingsViewModel = fuwaViewModel { c, _ -> SettingsViewModel(c.settingsStore, c.libraryRepository) }
    val s by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Pengaturan") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") } },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp)) {
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
