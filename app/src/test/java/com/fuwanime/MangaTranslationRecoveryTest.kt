package com.fuwanime

import com.fuwanime.data.manga.shouldRetryEmptyTranslation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A textless/empty IsManga result must not fail a whole chapter; one fresh retry then the original page is kept. */
class MangaTranslationRecoveryTest {
    @Test fun emptyPayloadIsRetriedOnceThenPageIsKept() {
        assertTrue(shouldRetryEmptyTranslation(0))
        assertFalse(shouldRetryEmptyTranslation(1))
        assertFalse(shouldRetryEmptyTranslation(2))
    }
}
