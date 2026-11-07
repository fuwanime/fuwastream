package com.fuwanime

import com.fuwanime.util.legalLocale
import com.fuwanime.util.resolveAppLocale
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLocaleTest {
    @Test fun explicitTagWins() = assertEquals("ms", resolveAppLocale("ms", Locale.US).language)

    @Test fun systemLanguageUsedWhenSupported() = assertEquals("ms", resolveAppLocale(null, Locale("ms", "MY")).language)

    @Test fun unsupportedSystemFallsBackToIndonesian() = assertEquals("id", resolveAppLocale("", Locale.FRANCE).language)

    @Test fun anyChineseBecomesSimplified() = assertEquals(Locale.SIMPLIFIED_CHINESE, resolveAppLocale(null, Locale.TRADITIONAL_CHINESE))

    @Test fun legalIndonesianStaysIndonesian() = assertEquals("id", legalLocale(Locale("id")).language)

    @Test fun legalMalayShowsEnglish() = assertEquals(Locale.ENGLISH, legalLocale(Locale("ms")))

    @Test fun legalChineseShowsEnglish() = assertEquals(Locale.ENGLISH, legalLocale(Locale.SIMPLIFIED_CHINESE))
}
