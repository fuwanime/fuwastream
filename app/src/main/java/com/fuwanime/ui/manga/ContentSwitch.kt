package com.fuwanime.ui.manga

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fuwanime.R
import com.fuwanime.data.Anime
import com.fuwanime.data.manga.Manga
import com.fuwanime.data.manga.MangaStatus

enum class ContentMode { ANIME, MANGA }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentSwitch(mode: ContentMode, onChange: (ContentMode) -> Unit, modifier: Modifier = Modifier) {
    val cd = stringResource(R.string.manga_switch_cd)
    val options = listOf(ContentMode.ANIME to R.string.manga_mode_anime, ContentMode.MANGA to R.string.manga_mode_manga)
    Box(modifier.fillMaxWidth().padding(horizontal = 20.dp).semantics { contentDescription = cd }) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (m, label) ->
                SegmentedButton(
                    selected = mode == m,
                    onClick = { onChange(m) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(stringResource(label)) }
            }
        }
    }
}

/** PosterCard/PosterRow take [Anime]; a manga is shown through the same card by mapping the fields it uses. */
internal fun Manga.toPoster() = Anime(id = id, title = title, poster = coverUrl.orEmpty(), year = year?.toString().orEmpty())

/** Localised status for the card caption; null (unknown) lets the card fall back to the year. */
@Composable
internal fun mangaStatusLabel(): (MangaStatus) -> String? {
    val ongoing = stringResource(R.string.manga_status_ongoing)
    val completed = stringResource(R.string.manga_status_completed)
    val hiatus = stringResource(R.string.manga_status_hiatus)
    val cancelled = stringResource(R.string.manga_status_cancelled)
    return { s ->
        when (s) {
            MangaStatus.ONGOING -> ongoing
            MangaStatus.COMPLETED -> completed
            MangaStatus.HIATUS -> hiatus
            MangaStatus.CANCELLED -> cancelled
            MangaStatus.UNKNOWN -> null
        }
    }
}
