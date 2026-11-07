package com.fuwanime.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.fuwanime.data.backup.BackupFile
import com.fuwanime.data.backup.BackupRepository
import com.fuwanime.data.backup.BackupResult
import com.fuwanime.data.local.BackupPrefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.launch

/** Subtitle of "Cadangkan sekarang": the last error, else when the last backup was written. */
fun backupStatusLabel(prefs: BackupPrefs, tz: TimeZone = TimeZone.getDefault(), locale: Locale = Locale.getDefault()): String {
    prefs.error?.let { return it }
    val last = prefs.lastMs ?: return "Belum pernah"
    return "Terakhir: " + SimpleDateFormat("d MMM yyyy HH.mm", locale).apply { timeZone = tz }.format(Date(last))
}

/**
 * Pick a backup file, show what is in it, and merge it after the user confirms. Returns the function that
 * opens the picker; shared by Settings and the first-run prompt on Home.
 */
@Composable
fun rememberRestoreFlow(backup: BackupRepository, onRestored: () -> Unit, onMessage: (String) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<BackupFile?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            when (val result = backup.read(uri)) {
                is BackupResult.Ok -> pending = result.file
                BackupResult.TooNew -> onMessage("Backup dibuat versi app yang lebih baru. Perbarui app dulu.")
                BackupResult.Corrupt -> onMessage("File backup tidak bisa dibaca.")
            }
        }
    }

    pending?.let { file ->
        val date = remember(file) { SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(file.createdAt)) }
        AlertDialog(
            onDismissRequest = { pending = null },
            icon = { Icon(Icons.Rounded.Restore, null) },
            title = { Text("Pulihkan data?") },
            text = {
                Text("${file.favorites.size} favorit, ${file.history.size} riwayat, backup $date.\n\nData di HP ini tetap ada; yang lebih baru dipakai.")
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    scope.launch {
                        runCatching { backup.restore(file) }
                            .onSuccess { onRestored(); onMessage("Data dipulihkan") }
                            .onFailure { onMessage("Gagal memulihkan: ${it.message}") }
                    }
                }) { Text("Pulihkan") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Batal") } },
        )
    }
    return { picker.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }
}
