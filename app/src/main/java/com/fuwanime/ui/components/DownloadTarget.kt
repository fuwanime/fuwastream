package com.fuwanime.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fuwanime.FuwaApp
import com.fuwanime.R
import com.fuwanime.ui.settings.folderName
import kotlinx.coroutines.launch

/**
 * Wraps a download action: asks "Simpan ke mana?" (the app, or a folder the user picks) before it runs, unless the
 * user ticked "always use this". The choice becomes the download-location setting, which the download code reads.
 */
@Composable
fun rememberDownloadGate(): (action: () -> Unit) -> Unit {
    val ctx = LocalContext.current
    val store = (ctx.applicationContext as FuwaApp).container.settingsStore
    val settings by store.settings.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var always by remember { mutableStateOf(false) }

    fun choose(folder: String?, action: () -> Unit) {
        scope.launch {
            store.setDownloadFolder(folder)
            if (always) store.setDownloadAsk(false)
            action()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        val action = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (uri == null) return@rememberLauncherForActivityResult
        // The move into the folder happens later in the background, so access must survive restarts.
        val granted = runCatching {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }.isSuccess
        choose(if (granted) uri.toString() else null, action)
    }

    pending?.let { action ->
        val folder = settings?.downloadFolder
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.download_target_title)) },
            text = {
                Column {
                    TextButton(onClick = { pending = null; choose(null, action) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.mine_download_location_app), Modifier.fillMaxWidth())
                    }
                    if (folder != null) {
                        TextButton(onClick = { pending = null; choose(folder, action) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.download_target_folder, folderName(ctx.resources, Uri.parse(folder))), Modifier.fillMaxWidth())
                        }
                    }
                    TextButton(onClick = { picker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.mine_download_location_pick), Modifier.fillMaxWidth())
                    }
                    Row(Modifier.fillMaxWidth().clickable { always = !always }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = always, onCheckedChange = { always = it })
                        Text(stringResource(R.string.download_target_remember))
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
    return { action -> if (settings?.downloadAsk == false) action() else { always = false; pending = action } }
}
