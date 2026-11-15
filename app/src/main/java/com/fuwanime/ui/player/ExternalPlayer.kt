package com.fuwanime.ui.player

import android.content.Intent
import android.net.Uri
import com.fuwanime.data.remote.AnimeApiService

/**
 * Hands a stream to another video app. Extras follow VLC (https://wiki.videolan.org/Android_Player_Intents/)
 * and MX Player; apps that ignore them still play the URL.
 */
fun externalPlayerIntent(url: String, title: String, positionMs: Long): Intent =
    Intent(Intent.ACTION_VIEW)
        .setDataAndType(Uri.parse(url), "video/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .putExtra("title", title)
        // MX Player reads an int; VLC accepts int or long.
        .putExtra("position", positionMs.coerceIn(0, Int.MAX_VALUE.toLong()).toInt())
        .putExtra("return_result", true)
        .putExtra("headers", arrayOf("User-Agent", AnimeApiService.USER_AGENT))

/** Where the other app stopped (VLC "extra_position", then MX "position"); null when it did not say. */
fun externalEndPosition(result: Intent?): Long? {
    val extras = result?.extras ?: return null
    val ms = when {
        extras.containsKey("extra_position") -> extras.getLong("extra_position")
        extras.containsKey("position") -> extras.getInt("position").toLong()
        else -> return null
    }
    return ms.takeIf { it > 0 }
}
