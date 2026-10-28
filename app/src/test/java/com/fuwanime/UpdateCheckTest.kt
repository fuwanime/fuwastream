package com.fuwanime

import com.fuwanime.data.isNewerRelease
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    @Test
    fun buildTags_onlyAHigherNumberIsAnUpdate() {
        assertTrue(isNewerRelease("build-52", "build-51"))
        assertFalse(isNewerRelease("build-24", "build-51")) // an older (or other repo's) release is not an update
        assertFalse(isNewerRelease("build-51", "build-51"))
    }

    @Test
    fun versionTags_compareNumerically() {
        assertTrue(isNewerRelease("v2.10.0", "v2.9.3"))
        assertFalse(isNewerRelease("v2.1.0", "v2.1.0"))
        assertFalse(isNewerRelease("v2.0.9", "v2.1.0"))
    }

    @Test
    fun mixedOrUnknownTags_areNotOffered() {
        assertFalse(isNewerRelease("build-60", "v2.1.0"))
        assertFalse(isNewerRelease("nightly", "build-51"))
    }
}
