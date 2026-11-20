package com.fuwanime.ui.player

import androidx.media3.common.PlaybackException
import com.fuwanime.data.local.AppSettings

/** Quality presets selectable in Settings. "auto" is network-aware; "manual" uses preferredQuality. */
const val QUALITY_AUTO = "auto"
const val QUALITY_DATA_SAVER = "data_saver"
const val QUALITY_BEST = "best"
const val QUALITY_MANUAL = "manual"

/** A quality picked in this player session wins; otherwise the configured quality mode. */
fun preferredHeight(settings: AppSettings, metered: Boolean, sessionHeight: Int?): Int = when {
    sessionHeight != null -> sessionHeight
    settings.qualityMode == QUALITY_MANUAL -> settings.preferredQuality
    settings.qualityMode == QUALITY_BEST -> 1080
    settings.qualityMode == QUALITY_DATA_SAVER -> 480
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
