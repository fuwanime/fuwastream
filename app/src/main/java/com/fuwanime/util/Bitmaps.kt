package com.fuwanime.util

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.withTimeoutOrNull

/** Small software bitmap for widgets and notifications; null on any failure or after 10 s. */
suspend fun loadBitmap(context: Context, url: String, maxPx: Int): Bitmap? {
    if (url.isBlank()) return null
    return runCatching {
        withTimeoutOrNull(10_000) {
            val request = ImageRequest.Builder(context).data(url).size(maxPx).allowHardware(false).build()
            (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap()
        }
    }.getOrNull()
}
