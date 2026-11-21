package com.fuwanime.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fuwanime.R
import kotlin.math.roundToInt

/**
 * Per-title personal rating (0–10 slider) and private note, saved into content_preferences.
 * Edits stay local until "Simpan"; the card re-seeds whenever the stored values change.
 */
@Composable
fun PersonalRatingNoteCard(
    rating: Int,
    note: String,
    onSave: (rating: Int, note: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var localRating by remember(rating, note) { mutableIntStateOf(rating) }
    var localNote by remember(rating, note) { mutableStateOf(note) }
    val changed = localRating != rating || localNote.trim() != note
    val ratingCd = stringResource(R.string.ux_personal_rating_cd, localRating)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Star, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.ux_personal_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (localRating == 0) stringResource(R.string.ux_personal_unrated) else stringResource(R.string.ux_personal_rating_value, localRating),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (localRating == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = localRating.toFloat(),
                onValueChange = { localRating = it.roundToInt().coerceIn(0, 10) },
                valueRange = 0f..10f,
                steps = 9,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = ratingCd },
            )
            OutlinedTextField(
                value = localNote,
                onValueChange = { localNote = it },
                placeholder = { Text(stringResource(R.string.ux_personal_note_hint)) },
                minLines = 2,
                maxLines = 6,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (rating > 0 || note.isNotBlank()) {
                    TextButton(onClick = { localRating = 0; localNote = ""; onSave(0, "") }) {
                        Text(stringResource(R.string.ux_personal_clear))
                    }
                    Spacer(Modifier.width(8.dp))
                }
                FilledTonalButton(onClick = { onSave(localRating, localNote) }, enabled = changed) {
                    Text(stringResource(R.string.ux_personal_save))
                }
            }
        }
    }
}
