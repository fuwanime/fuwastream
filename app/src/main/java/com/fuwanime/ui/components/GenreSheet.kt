package com.fuwanime.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fuwanime.R

/** First chip of a filter row: "Genre", or "Genre · 2" while some are picked; opens [GenreSheet]. */
@Composable
fun GenreChip(selectedCount: Int, onClick: () -> Unit) {
    FilterChip(
        selected = selectedCount > 0,
        onClick = onClick,
        label = { Text(if (selectedCount > 0) stringResource(R.string.common_genre_count, selectedCount) else stringResource(R.string.common_genre)) },
        leadingIcon = { Icon(Icons.Rounded.Tune, null, Modifier.padding(start = 2.dp)) },
        trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary, selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary, selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimary),
    )
}

/** All genres as a grid of toggles; each tap applies at once, so the results behind the sheet already follow. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GenreSheet(genres: List<Pair<String, String>>, selected: Set<String>, onToggle: (String) -> Unit, onClear: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.common_genre), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (selected.isNotEmpty()) TextButton(onClick = onClear) { Text(stringResource(R.string.common_genre_clear)) }
            }
            FlowRow(
                Modifier.verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                genres.forEach { (id, name) -> TagChip(name, onClick = { onToggle(id) }, selected = id in selected) }
            }
        }
    }
}
