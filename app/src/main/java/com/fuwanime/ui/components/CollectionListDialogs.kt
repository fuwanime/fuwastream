package com.fuwanime.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fuwanime.R
import com.fuwanime.data.local.CollectionListEntity

/** Chip/row label: the optional emoji before the name, e.g. "🌸 Comfort shows". */
fun CollectionListEntity.displayLabel(): String = listOfNotNull(emoji?.takeIf { it.isNotBlank() }, name).joinToString(" ")

/**
 * Picker that adds/removes one title (or a whole multi-selection) to/from custom lists.
 * [checkedIds] are the lists already containing everything being added; toggling applies to all of them.
 * A new list can be created inline and the caller adds the title to it right away.
 */
@Composable
fun AddToListDialog(
    lists: List<CollectionListEntity>,
    checkedIds: Set<Long>,
    onToggle: (listId: Long, add: Boolean) -> Unit,
    onCreate: (name: String, emoji: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var emoji by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.PlaylistAdd, null) },
        title = { Text(stringResource(R.string.ux_add_to_list_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (lists.isEmpty() && !creating) {
                    Text(
                        stringResource(R.string.mine_list_empty_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                lists.forEach { list ->
                    val checked = list.id in checkedIds
                    Row(
                        Modifier.fillMaxWidth().clickable { onToggle(list.id, !checked) }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { onToggle(list.id, it) })
                        Text(list.displayLabel(), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (creating) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.mine_list_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it.take(2) },
                        label = { Text(stringResource(R.string.mine_list_emoji)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    TextButton(onClick = { creating = true }) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.mine_list_new))
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = { onCreate(name, emoji); name = ""; emoji = ""; creating = false },
                ) { Text(stringResource(R.string.mine_list_create)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.ux_done)) }
            }
        },
        dismissButton = {
            if (creating) TextButton(onClick = { creating = false }) { Text(stringResource(R.string.mine_cancel)) }
        },
    )
}

/** Create or rename a list: a name plus an optional emoji. */
@Composable
fun ListNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String = "",
    initialEmoji: String? = null,
    onConfirm: (name: String, emoji: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var emoji by rememberSaveable { mutableStateOf(initialEmoji.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.PlaylistAdd, null) },
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.mine_list_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it.take(2) },
                    label = { Text(stringResource(R.string.mine_list_emoji)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name, emoji) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.mine_cancel)) } },
    )
}
