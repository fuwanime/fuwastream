package com.fuwanime.util

import android.content.Context
import android.graphics.Bitmap
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.withTimeoutOrNull

/** Small software bitmap for widgets and notifications; null on any failure or after 10 s. */
suspend fun loadBitmap(context: Context, url: String, maxPx: Int): Bitmap? {
    if (url.isBlank()) return null
    return runCatching {
        withTimeoutOrNull(10_000) {
            val request = ImageRequest.Builder(context).data(url).size(maxPx).allowHardware(false).build()
            (context.imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap()
        }
    }.getOrNull()
}
