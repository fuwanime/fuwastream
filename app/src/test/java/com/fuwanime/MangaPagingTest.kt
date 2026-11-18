package com.fuwanime

import com.fuwanime.ui.manga.shouldLoadMore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaPagingTest {
    @Test fun shouldLoadMoreNearEnd() {
        assertTrue(shouldLoadMore(lastVisible = 18, loaded = 24, total = 100))
        assertFalse(shouldLoadMore(lastVisible = 10, loaded = 24, total = 100))
        assertFalse(shouldLoadMore(lastVisible = 99, loaded = 100, total = 100))
    }

    @Test fun loadsMoreWhenMoreResultsRemain() {
        assertTrue(shouldLoadMore(lastVisible = 9999, loaded = 9990, total = 50000))
        assertFalse(shouldLoadMore(lastVisible = 9955, loaded = 9960, total = 9960))
    }
}
