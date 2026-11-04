package com.fuwanime.util

import java.util.TimeZone

/** Days since 1970-01-01 in [tz] (minSdk 24 has no java.time). */
fun epochDay(millis: Long, tz: TimeZone = TimeZone.getDefault()): Int =
    Math.floorDiv(millis + tz.getOffset(millis), 86_400_000L).toInt()
