package com.fuwanime

import android.app.Application
import com.fuwanime.ui.legal.LegalText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "id", application = Application::class)
class LegalTextTest {
    @Test fun indonesianSections() {
        val res = testResources()
        assertEquals(12, LegalText.privacy(res).split("\n\n").size)
        assertEquals(10, LegalText.terms(res).split("\n\n").size)
        assertTrue(LegalText.privacy(res).contains("RINGKASAN\nFuwaStream tidak memiliki akun"))
        assertTrue(LegalText.about(res).contains("Email: ${LegalText.DEVELOPER_EMAIL}"))
    }

    @Config(qualifiers = "en")
    @Test fun englishSections() {
        val res = testResources()
        assertEquals(12, LegalText.privacy(res).split("\n\n").size)
        assertEquals(10, LegalText.terms(res).split("\n\n").size)
        assertTrue(LegalText.privacy(res).contains("SUMMARY\nFuwaStream has no accounts"))
        assertTrue(LegalText.about(res).contains("DEVELOPER\n${LegalText.DEVELOPER_NAME}"))
    }
}
