package com.fuwanime.util

import android.content.res.Resources
import androidx.annotation.StringRes
import com.fuwanime.R
import java.text.DateFormatSymbols
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** One decimal in the app language, without a trailing zero: 15.9 / 2. */
private fun decimal1(res: Resources, value: Double): String {
    val locale = res.configuration.locales[0]
    return String.format(locale, "%.1f", value).removeSuffix("${DecimalFormatSymbols.getInstance(locale).decimalSeparator}0")
}

/** 15894862 -> "15,9 jt" (id), "15.9M" (en); 38798 -> "38,8 rb". */
fun compactNumber(res: Resources, value: Long): String = when {
    value >= 1_000_000_000 -> res.getString(R.string.common_number_billion, decimal1(res, value / 1_000_000_000.0))
    value >= 1_000_000 -> res.getString(R.string.common_number_million, decimal1(res, value / 1_000_000.0))
    value >= 1_000 -> res.getString(R.string.common_number_thousand, decimal1(res, value / 1_000.0))
    else -> value.toString()
}

/** 1536 -> "1,5 KB", 734003200 -> "700 MB"; the decimal separator follows the app language. */
fun formatBytes(res: Resources, bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.coerceAtLeast(0).toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit <= 1) "${value.toLong()} ${units[unit]}" else "${decimal1(res, value)} ${units[unit]}"
}

fun formatDuration(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
}

/** "Sisa 14 menit", rounded up to whole minutes; null when the duration is unknown or nothing is left. */
fun remainingLabel(res: Resources, positionMs: Long, durationMs: Long): String? {
    if (durationMs <= 0) return null
    val remaining = durationMs - positionMs.coerceAtLeast(0)
    if (remaining <= 0) return null
    val minutes = ((remaining + 59_999) / 60_000).toInt()
    return res.getQuantityString(R.plurals.common_minutes_left, minutes, minutes)
}

/** Short month name ("Jan", "Okt") in the language of [res]; [month] is 0-11. */
fun shortMonth(res: Resources, month: Int): String = DateFormatSymbols.getInstance(res.configuration.locales[0]).shortMonths[month]

/** Day heading for watch history: "HARI INI", "KEMARIN", "28 OKT", or "28 DES 2025" for another year. */
fun historyDayLabel(res: Resources, timestampMs: Long, nowMs: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()): String {
    val at = Calendar.getInstance(zone).apply { timeInMillis = timestampMs }
    val day = Calendar.getInstance(zone).apply { timeInMillis = nowMs }
    fun sameDay() = at.get(Calendar.YEAR) == day.get(Calendar.YEAR) && at.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR)
    if (sameDay()) return res.getString(R.string.common_today)
    day.add(Calendar.DAY_OF_YEAR, -1)
    if (sameDay()) return res.getString(R.string.common_yesterday)
    val month = shortMonth(res, at.get(Calendar.MONTH)).uppercase(res.configuration.locales[0])
    val label = "${at.get(Calendar.DAY_OF_MONTH)} $month"
    return if (at.get(Calendar.YEAR) == Calendar.getInstance(zone).apply { timeInMillis = nowMs }.get(Calendar.YEAR)) label
    else "$label ${at.get(Calendar.YEAR)}"
}

enum class Day(val apiValue: String, @StringRes val full: Int, @StringRes val short: Int) {
    MONDAY("senin", R.string.common_day_monday, R.string.common_day_monday_short),
    TUESDAY("selasa", R.string.common_day_tuesday, R.string.common_day_tuesday_short),
    WEDNESDAY("rabu", R.string.common_day_wednesday, R.string.common_day_wednesday_short),
    THURSDAY("kamis", R.string.common_day_thursday, R.string.common_day_thursday_short),
    FRIDAY("jumat", R.string.common_day_friday, R.string.common_day_friday_short),
    SATURDAY("sabtu", R.string.common_day_saturday, R.string.common_day_saturday_short),
    SUNDAY("minggu", R.string.common_day_sunday, R.string.common_day_sunday_short);

    companion object {
        fun today(): Day = of(Calendar.getInstance().get(Calendar.DAY_OF_WEEK))

        /** [dayOfWeek] is a [Calendar.DAY_OF_WEEK] value. */
        fun of(dayOfWeek: Int): Day = when (dayOfWeek) {
            Calendar.MONDAY -> MONDAY
            Calendar.TUESDAY -> TUESDAY
            Calendar.WEDNESDAY -> WEDNESDAY
            Calendar.THURSDAY -> THURSDAY
            Calendar.FRIDAY -> FRIDAY
            Calendar.SATURDAY -> SATURDAY
            else -> SUNDAY
        }
    }
}

/** "2026-10-22 00:36:10" -> "00:36"; anything unparseable is shown as-is. */
fun airTime(raw: String): String =
    Regex("""\d{4}-\d{2}-\d{2} (\d{2}:\d{2})""").find(raw)?.groupValues?.get(1) ?: raw

/** "Episode 5" -> "Ep 5". */
fun episodeShortLabel(label: String): String = label.trim().replace(Regex("^episode\\s*", RegexOption.IGNORE_CASE), "Ep ")

/** "2026-10-26 20:10:10" (WIB, as the API sends it) -> "3 jam lalu"; unparseable or future -> "". */
fun relativeTime(res: Resources, raw: String, nowMs: Long = System.currentTimeMillis()): String {
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Jakarta")
        isLenient = false
    }
    val at = runCatching { format.parse(raw.trim()) }.getOrNull()?.time ?: return ""
    return relativeAge(res, nowMs - at)
}

/** Age in ms -> "3 jam lalu"; more than a minute in the future (clock skew) -> "". */
fun relativeAge(res: Resources, diffMs: Long): String = when {
    diffMs < -60_000 -> ""
    diffMs < 60_000 -> res.getString(R.string.common_just_now)
    diffMs < 3_600_000 -> (diffMs / 60_000).toInt().let { res.getQuantityString(R.plurals.common_minutes_ago, it, it) }
    diffMs < 86_400_000 -> (diffMs / 3_600_000).toInt().let { res.getQuantityString(R.plurals.common_hours_ago, it, it) }
    else -> (diffMs / 86_400_000).toInt().let { res.getQuantityString(R.plurals.common_days_ago, it, it) }
}

/** Text of the offline pill: how old the cached data on screen is, when known. */
fun offlineLabel(res: Resources, cacheServedAt: Long?, nowMs: Long): String {
    val age = cacheServedAt?.let { relativeAge(res, nowMs - it) }.orEmpty()
    return if (age.isEmpty()) res.getString(R.string.common_offline) else res.getString(R.string.common_offline_with_age, age)
}

/** Network/server failure as a message; ViewModels keep this and the screen resolves it so it follows the app language. */
fun Throwable.friendlyText(): UiText = when (this) {
    is UiTextException -> text
    is java.net.UnknownHostException -> UiText(R.string.common_error_no_internet)
    is java.net.SocketTimeoutException -> UiText(R.string.common_error_timeout)
    is java.io.IOException -> UiText(R.string.common_error_connect)
    is retrofit2.HttpException -> UiText(R.string.common_error_server, listOf(code()))
    else -> message?.let { UiText.raw(it) } ?: UiText(R.string.common_error_generic)
}

fun Throwable.friendlyMessage(res: Resources): String = friendlyText().resolve(res)

fun statusLabel(res: Resources, status: String): String = when (status.uppercase()) {
    "ONGOING" -> res.getString(R.string.common_status_airing)
    "FINISHED", "COMPLETED", "COMPLETE" -> res.getString(R.string.common_status_finished)
    "UPCOMING", "NOT_YET_AIRED" -> res.getString(R.string.common_status_upcoming)
    else -> status.lowercase().replaceFirstChar { it.titlecase(res.configuration.locales[0]) }
}
