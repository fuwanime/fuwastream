package com.fuwanime

import com.fuwanime.data.notify.groupSummaryTitle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "id", application = android.app.Application::class)
class NotifyGroupTest {
    @Test fun summaryTitle_three() = assertEquals("3 episode baru", groupSummaryTitle(testResources(), 3))
}
