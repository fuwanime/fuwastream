package com.fuwanime.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fuwanime.R

/**
 * App-styled snackbar host: a rounded surface card in the Yozakura palette instead of Material3's
 * inverse (light-on-dark) default. Used by every screen so messages look the same everywhere.
 */
@Composable
fun FuwaSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data -> FuwaSnackbar(data) }
}

@Composable
private fun FuwaSnackbar(data: SnackbarData) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    val visuals = data.visuals
    Surface(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .widthIn(max = 560.dp)
            .border(1.dp, colors.outlineVariant, shape),
        shape = shape,
        color = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            visuals.actionLabel?.let { label ->
                TextButton(
                    onClick = { data.performAction() },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.primary),
                ) { Text(label) }
            }
            if (visuals.withDismissAction) {
                IconButton(onClick = { data.dismiss() }) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.mine_close))
                }
            }
        }
    }
}
