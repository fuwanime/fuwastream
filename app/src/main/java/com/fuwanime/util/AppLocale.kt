package com.fuwanime.util

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import java.util.Locale

val SUPPORTED_LANGUAGES = listOf("id", "ms", "en", "zh")

private const val PREFS = "locale"
private const val KEY_TAG = "tag"

private fun String.normalized() = if (this == "in") "id" else this // legacy ISO code Android may report

/** Explicit [tag] wins; otherwise the system language if we ship it, else Indonesian. */
fun resolveAppLocale(tag: String?, system: Locale): Locale {
    if (!tag.isNullOrEmpty()) return Locale.forLanguageTag(tag)
    val lang = system.language.normalized()
    return when {
        lang == "zh" -> Locale.SIMPLIFIED_CHINESE
        lang in SUPPORTED_LANGUAGES -> Locale(lang)
        else -> Locale("id")
    }
}

/** Legal texts exist only in Indonesian and English; every other app language shows English. */
fun legalLocale(appLocale: Locale): Locale =
    if (appLocale.language.normalized() == "id") Locale("id") else Locale.ENGLISH

private fun Context.localePrefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

fun savedLanguageTag(context: Context): String? = context.localePrefs().getString(KEY_TAG, null)?.ifEmpty { null }

/** [tag] null = follow the system. Below API 33 the choice is stored by us and the activity recreated. */
fun setAppLanguage(activity: Activity, tag: String?) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag ?: "")
    } else {
        activity.localePrefs().edit().putString(KEY_TAG, tag).apply()
        activity.recreate()
    }
}

/** API 33+ the platform applies the per-app locale itself; below that we wrap the context. */
fun localizedContext(context: Context): Context {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return context
    // Locale only: a full copy would freeze orientation/uiMode/fontScale that MainActivity handles in place.
    val config = android.content.res.Configuration().apply { setLocale(resolveAppLocale(savedLanguageTag(context), Locale.getDefault())) }
    return context.createConfigurationContext(config)
}

/** null means "follow the system". */
fun currentLanguageTag(context: Context): String? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags().ifEmpty { null }
    } else {
        savedLanguageTag(context)
    }
