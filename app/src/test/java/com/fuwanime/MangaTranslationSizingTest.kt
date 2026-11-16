package com.fuwanime

import com.fuwanime.data.manga.TranslationUploadSize
import com.fuwanime.data.manga.translationUploadSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaTranslationSizingTest {
    @Test fun ordinaryPageKeepsItsResolution() {
        assertEquals(TranslationUploadSize(1_200, 1_800), translationUploadSize(1_200, 1_800))
    }

    @Test fun longPageFitsDimensionLimitWithoutChangingAspectRatio() {
        val result = translationUploadSize(1_400, 12_000)

        assertEquals(4_096, result.height)
        assertTrue(result.width in 477..478)
        assertEquals(1_400.0 / 12_000.0, result.width.toDouble() / result.height, 0.001)
    }

    @Test fun highResolutionSquareFitsPixelLimit() {
        val result = translationUploadSize(4_000, 4_000)

        assertEquals(result.width, result.height)
        assertTrue(result.width.toLong() * result.height <= 8_000_000L)
    }

    @Test fun retryCanApplyMoreConservativeLimits() {
        val result = translationUploadSize(5_000, 8_000, maxDimension = 2_048, maxPixels = 4_000_000L)

        assertEquals(2_048, result.height)
        assertEquals(1_280, result.width)
        assertTrue(result.width.toLong() * result.height <= 4_000_000L)
    }

    @Test fun invalidBoundsNeverProduceZeroSize() {
        assertEquals(TranslationUploadSize(1, 1), translationUploadSize(0, -1))
    }
}
