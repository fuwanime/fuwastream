package com.fuwanime

import com.fuwanime.data.notify.groupSummaryTitle
import org.junit.Assert.assertEquals
import org.junit.Test

class NotifyGroupTest {
    @Test fun summaryTitle_three() = assertEquals("3 episode baru", groupSummaryTitle(3))
}
