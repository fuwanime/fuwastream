package com.fuwanime.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fuwanime.R
import com.fuwanime.data.Anime
import com.fuwanime.data.local.CollectionItemEntity
import com.fuwanime.data.local.CollectionListEntity
import com.fuwanime.ui.components.TagChip
import com.fuwanime.ui.components.displayLabel

/** "Semua" (the favourites grid) plus one chip per custom list and a create chip. */
@Composable
fun CollectionListChips(
    lists: List<CollectionListEntity>,
    selectedListId: Long?,
    onSelect: (Long?) -> Unit,
    onCreate: () -> Unit,
) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TagChip(stringResource(R.string.ux_status_all), onClick = { onSelect(null) }, selected = selectedListId == null)
        lists.forEach { list ->
            TagChip(list.displayLabel(), onClick = { onSelect(list.id) }, selected = list.id == selectedListId)
        }
        TagChip("＋ " + stringResource(R.string.mine_list_new), onClick = onCreate)
    }
}

/** Name, count and the list's own actions: reorder, rename, delete. Shown while a list is open. */
@Composable
fun ListManageRow(
    list: CollectionListEntity,
    count: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (up: Boolean) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(list.displayLabel(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.mine_list_count, count), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { onMove(true) }, enabled = canMoveUp) {
            Icon(Icons.Rounded.ArrowUpward, stringResource(R.string.mine_list_move_up))
        }
        IconButton(onClick = { onMove(false) }, enabled = canMoveDown) {
            Icon(Icons.Rounded.ArrowDownward, stringResource(R.string.mine_list_move_down))
        }
        IconButton(onClick = onRename) {
            Icon(Icons.Rounded.Edit, stringResource(R.string.mine_list_rename))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Delete, stringResource(R.string.mine_list_delete), tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun DeleteListDialog(list: CollectionListEntity, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Delete, null) },
        title = { Text(stringResource(R.string.mine_list_delete_title, list.name)) },
        text = { Text(stringResource(R.string.mine_list_delete_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.mine_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.mine_cancel)) } },
    )
}

/** A listed title as a poster card payload; manga items open through the manga route instead. */
fun CollectionItemEntity.toAnime() = Anime(id = contentId, title = title, poster = poster)
