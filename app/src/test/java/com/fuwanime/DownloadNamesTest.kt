package com.fuwanime

import com.fuwanime.data.episodeFileName
import com.fuwanime.data.episodeLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadNamesTest {
    @Test fun wholeEpisodesArePaddedSoTheySortInOrder() {
        assertEquals("Ep 01", episodeLabel("1"))
        assertEquals("Ep 12", episodeLabel("Episode 12"))
        assertEquals("Ep 100", episodeLabel("eps 100"))
    }

    @Test fun halvesAndSpecialsKeepTheirLabel() {
        assertEquals("Ep 12.5", episodeLabel("12.5"))
        assertEquals("OVA", episodeLabel("OVA"))
    }

    @Test fun fileNameHasTitleEpisodeQualityAndNoIllegalCharacters() =
        assertEquals("Re_Zero - Ep 03 (720p).mkv", episodeFileName("Re:Zero", "3", "720p"))
}
