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
import android.content.res.Resources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.fuwanime.R
import com.fuwanime.data.backup.BackupFile
import com.fuwanime.data.backup.ERROR_FOLDER
import com.fuwanime.data.backup.ERROR_OTHER_PREFIX
import com.fuwanime.data.backup.BackupRepository
import com.fuwanime.data.backup.BackupResult
import com.fuwanime.data.local.BackupPrefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.launch

/** Text for a stored backup error code; anything unrecognised (an older install's saved message) is shown as is. */
fun backupErrorLabel(res: Resources, code: String): String = when {
    code == ERROR_FOLDER -> res.getString(R.string.mine_backup_err_folder)
    code.startsWith(ERROR_OTHER_PREFIX) -> res.getString(R.string.mine_backup_err_other, code.removePrefix(ERROR_OTHER_PREFIX))
    else -> code
}

/** Subtitle of "Cadangkan sekarang": the last error, else when the last backup was written. */
fun backupStatusLabel(
    res: Resources,
    prefs: BackupPrefs,
    tz: TimeZone = TimeZone.getDefault(),
    locale: Locale = res.configuration.locales[0],
): String {
    prefs.error?.let { return backupErrorLabel(res, it) }
    val last = prefs.lastMs ?: return res.getString(R.string.mine_backup_never)
    val time = if (locale.language == "id" || locale.language == "ms") "HH.mm" else "HH:mm"
    return res.getString(R.string.mine_backup_last, SimpleDateFormat("d MMM yyyy $time", locale).apply { timeZone = tz }.format(Date(last)))
}

/**
 * Pick a backup file, show what is in it, and merge it after the user confirms. Returns the function that
 * opens the picker; shared by Settings and the first-run prompt on Home.
 */
@Composable
fun rememberRestoreFlow(backup: BackupRepository, onRestored: () -> Unit, onMessage: (String) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val res = LocalContext.current.resources
    var pending by remember { mutableStateOf<BackupFile?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            when (val result = backup.read(uri)) {
                is BackupResult.Ok -> pending = result.file
                BackupResult.TooNew -> onMessage(res.getString(R.string.mine_restore_too_new))
                BackupResult.Corrupt -> onMessage(res.getString(R.string.mine_restore_corrupt))
            }
        }
    }

    pending?.let { file ->
        val date = remember(file) { SimpleDateFormat("d MMM yyyy", res.configuration.locales[0]).format(Date(file.createdAt)) }
        AlertDialog(
            onDismissRequest = { pending = null },
            icon = { Icon(Icons.Rounded.Restore, null) },
            title = { Text(stringResource(R.string.mine_restore_title)) },
            text = {
                Text(stringResource(R.string.mine_restore_body, file.favorites.size, file.history.size, date))
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    scope.launch {
                        runCatching { backup.restore(file) }
                            .onSuccess { onRestored(); onMessage(res.getString(R.string.mine_restore_done)) }
                            .onFailure { onMessage(res.getString(R.string.mine_restore_failed, it.message)) }
                    }
                }) { Text(stringResource(R.string.mine_restore_confirm)) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.mine_cancel)) } },
        )
    }
    return { picker.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }
}
