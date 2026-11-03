package com.fuwanime.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Walks the main path (cold start, Beranda, Detail, the tabs) so those code paths ship precompiled. */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE) {
        // The runtime permission prompt would cover the app on first launch.
        device.executeShellCommand("pm grant $PACKAGE android.permission.POST_NOTIFICATIONS")
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.text("Episode Terbaru")), 15_000)

        val width = device.displayWidth
        val height = device.displayHeight
        // Hero pager, then the feed.
        device.swipe(width * 4 / 5, height / 3, width / 5, height / 3, 20)
        device.findObject(By.scrollable(true))?.let { list ->
            repeat(2) { list.fling(Direction.DOWN) }
            repeat(2) { list.fling(Direction.UP) }
        }

        device.findObject(By.text("Tonton"))?.let {
            it.click()
            device.wait(Until.hasObject(By.textStartsWith("Tonton EP")), 10_000)
            device.pressBack()
            device.waitForIdle()
        }

        for (tab in listOf("Jelajah", "Jadwal", "Koleksi", "Beranda")) {
            device.findObject(By.text(tab))?.click()
            device.waitForIdle()
            Thread.sleep(1_500)
        }
    }

    private companion object {
        const val PACKAGE = "com.fuwanime.app"
    }
}
