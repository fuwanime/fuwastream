package com.fuwanime

import android.app.Application
import android.content.Intent
import android.net.Uri
import com.fuwanime.ui.player.externalEndPosition
import com.fuwanime.ui.player.externalPlayerIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ExternalPlayerTest {
    private val url = "https://cdn.example/ep12.mp4"

    @Test fun intentCarriesUrlTypeTitleAndPosition() {
        val i = externalPlayerIntent(url, "Frieren 12", 90_000L)
        assertEquals(Intent.ACTION_VIEW, i.action)
        assertEquals(Uri.parse(url), i.data)
        assertEquals("video/*", i.type)
        assertEquals("Frieren 12", i.getStringExtra("title"))
        assertEquals(90_000, i.getIntExtra("position", -1))
    }

    @Test fun endPositionReadsVlcThenMx() {
        assertEquals(1_234L, externalEndPosition(Intent().putExtra("extra_position", 1_234L)))
        assertEquals(5_000L, externalEndPosition(Intent().putExtra("position", 5_000)))
    }

    @Test fun endPositionNullWhenNoExtras() {
        assertNull(externalEndPosition(null))
        assertNull(externalEndPosition(Intent()))
        assertNull(externalEndPosition(Intent().putExtra("extra_position", 0L)))
    }
}
