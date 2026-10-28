package com.fuwanime.ui.player

import androidx.media3.common.PlaybackException
import com.fuwanime.data.local.AppSettings

/** A quality picked in this player session wins; otherwise the mobile-data setting on metered networks. */
fun preferredHeight(settings: AppSettings, metered: Boolean, sessionHeight: Int?): Int = when {
    sessionHeight != null -> sessionHeight
    metered && settings.mobileDataQuality > 0 -> settings.mobileDataQuality
    else -> settings.preferredQuality
}

/** Errors a smaller file can plausibly avoid. A dropped connection is not one of them. */
fun shouldFallBack(errorCode: Int): Boolean = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    -> true
    else -> false
}
