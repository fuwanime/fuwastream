package com.fuwanime

import android.content.Context
import android.content.res.Configuration
import com.fuwanime.data.Episode
import com.fuwanime.ui.player.nowPlaying
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "id")
class WatchTextTest {
    private fun res(tag: String) = testResources().let {
        val c = Configuration(it.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        (androidx.test.core.app.ApplicationProvider.getApplicationContext<Context>().createConfigurationContext(c)).resources
    }

    @Test fun sleepMinutesPlural() {
        assertEquals("15 menit", res("id").getQuantityString(R.plurals.watch_sleep_minutes, 15, 15))
        assertEquals("1 minute", res("en").getQuantityString(R.plurals.watch_sleep_minutes, 1, 1))
        assertEquals("15 minutes", res("en").getQuantityString(R.plurals.watch_sleep_minutes, 15, 15))
    }

    @Test fun seekBubbleKeepsSign() {
        assertEquals("-10 detik", res("id").getQuantityString(R.plurals.watch_seek_back, 10, 10))
        assertEquals("+10 seconds", res("en").getQuantityString(R.plurals.watch_seek_forward, 10, 10))
    }

    @Test fun nowPlayingSubtitleFollowsLocale() {
        val ep = Episode(id = "e", number = "3", title = "")
        assertEquals("Episode 3", nowPlaying(res("id"), ep, null).subtitle)
        assertEquals("第3集", nowPlaying(res("zh-CN"), ep, null).subtitle)
    }

    @Test fun bulkDownloadMessageIsUnchangedInIndonesian() = assertEquals(
        "Mengantre hingga 30 episode terbaru yang belum tersimpan, memakai kualitas download dan opsi Wi-Fi dari Pengaturan.",
        res("id").getQuantityString(R.plurals.watch_bulk_message, 30, 30),
    )
}
