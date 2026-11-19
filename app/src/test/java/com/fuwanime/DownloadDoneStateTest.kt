package com.fuwanime

import com.fuwanime.data.DownloadState
import com.fuwanime.data.doneState
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadDoneStateTest {
    @Test fun doneWhenFileExistsAndAllBytesArrived() =
        assertEquals(DownloadState.DONE, doneState(fileExists = true, downloadedBytes = 100, totalBytes = 100))

    @Test fun failedWhenTruncatedAgainstKnownTotal() =
        assertEquals(DownloadState.FAILED, doneState(fileExists = true, downloadedBytes = 40, totalBytes = 100))

    @Test fun doneWhenTotalUnknown() =
        assertEquals(DownloadState.DONE, doneState(fileExists = true, downloadedBytes = 40, totalBytes = -1))

    @Test fun failedWhenFileMissing() =
        assertEquals(DownloadState.FAILED, doneState(fileExists = false, downloadedBytes = 100, totalBytes = 100))
}
