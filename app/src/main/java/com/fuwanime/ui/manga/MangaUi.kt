package com.fuwanime.ui.manga

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.fuwanime.R
import com.fuwanime.data.Anime
import com.fuwanime.data.manga.Manga
import com.fuwanime.data.manga.MangaStatus

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
