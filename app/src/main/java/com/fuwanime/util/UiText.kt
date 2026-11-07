package com.fuwanime.util

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * A user-facing message that ViewModels can hold without a Context: a string resource plus arguments
 * (an argument may itself be a [UiText]), or [raw] text that is not translated (e.g. a server message).
 */
data class UiText(@StringRes val id: Int = 0, val args: List<Any> = emptyList(), val raw: String? = null) {
    fun resolve(res: Resources): String =
        raw ?: res.getString(id, *args.map { if (it is UiText) it.resolve(res) else it }.toTypedArray())

    companion object {
        fun raw(text: String) = UiText(raw = text)
    }
}

@Composable
fun UiText.text(): String = resolve(LocalContext.current.resources)
