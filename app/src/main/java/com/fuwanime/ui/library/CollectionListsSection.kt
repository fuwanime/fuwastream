package com.fuwanime.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
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
import com.fuwanime.ui.components.displayLabel

/** Permanent system tabs of the Koleksi tab row; user-created list ids are always positive. */
const val TAB_HISTORY = -1L
const val TAB_ALL = 0L

/**
 * Google Keep-style label tabs: Riwayat comes first and is permanent (no rename/delete), then "Semua"
 * (the favourites grid), one tab per custom list, and a trailing ＋ that creates a new list.
 * Tonal surfaceContainerLow row; the selected tab and the indicator use the primary colour.
 */
@Composable
fun CollectionTabs(
    lists: List<CollectionListEntity>,
    selectedTab: Long,
    onSelect: (Long) -> Unit,
    onCreate: () -> Unit,
) {
    val tabIds = listOf(TAB_HISTORY, TAB_ALL) + lists.map { it.id }
    val selectedIndex = tabIds.indexOf(selectedTab).coerceAtLeast(0)
    SecondaryScrollableTabRow(
        selectedTabIndex = selectedIndex,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        edgePadding = 16.dp,
        divider = {},
    ) {
        Tab(
            selected = selectedTab == TAB_HISTORY,
            onClick = { onSelect(TAB_HISTORY) },
            selectedContentColor = MaterialTheme.colorScheme.primary,
            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) { TabLabel(stringResource(R.string.mine_tab_history), selected = selectedTab == TAB_HISTORY) }
        Tab(
            selected = selectedTab == TAB_ALL,
            onClick = { onSelect(TAB_ALL) },
            selectedContentColor = MaterialTheme.colorScheme.primary,
            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) { TabLabel(stringResource(R.string.ux_status_all), selected = selectedTab == TAB_ALL) }
        lists.forEach { list ->
            Tab(
                selected = list.id == selectedTab,
                onClick = { onSelect(list.id) },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) { TabLabel(list.displayLabel(), selected = list.id == selectedTab) }
        }
        // Never selected: a button-shaped tab that opens the create-list dialog.
        Tab(
            selected = false,
            onClick = onCreate,
            selectedContentColor = MaterialTheme.colorScheme.primary,
            unselectedContentColor = MaterialTheme.colorScheme.primary,
        ) {
            Icon(Icons.Rounded.Add, stringResource(R.string.mine_list_new), Modifier.padding(vertical = 14.dp).size(20.dp))
        }
    }
}

@Composable
private fun TabLabel(text: String, selected: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(vertical = 14.dp),
    )
}

/** Name, count and the list's own actions: reorder, rename, delete. Shown while a custom list tab is open. */
@Composable
fun ListManageRow(
    list: CollectionListEntity,
    count: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (up: Boolean) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
