package com.fuwanime.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private val ID = Locale.forLanguageTag("id-ID")

/** 15894862 -> "15,9 jt", 38798 -> "38,8 rb". */
fun compactNumber(value: Long): String = when {
    value >= 1_000_000_000 -> String.format(ID, "%.1f M", value / 1_000_000_000.0)
    value >= 1_000_000 -> String.format(ID, "%.1f jt", value / 1_000_000.0)
    value >= 1_000 -> String.format(ID, "%.1f rb", value / 1_000.0)
    else -> value.toString()
}.replace(",0 ", " ")

/** 1536 -> "1,5 KB", 734003200 -> "700 MB". */
fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.coerceAtLeast(0).toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit <= 1) "${value.toLong()} ${units[unit]}" else String.format(ID, "%.1f %s", value, units[unit]).replace(",0 ", " ")
}

fun formatDuration(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
}

/** "Sisa 14 menit", rounded up to whole minutes; null when the duration is unknown or nothing is left. */
fun remainingLabel(positionMs: Long, durationMs: Long): String? {
    if (durationMs <= 0) return null
    val remaining = durationMs - positionMs.coerceAtLeast(0)
    if (remaining <= 0) return null
    return "Sisa ${(remaining + 59_999) / 60_000} menit"
}

private val MONTHS = listOf("JAN", "FEB", "MAR", "APR", "MEI", "JUN", "JUL", "AGU", "SEP", "OKT", "NOV", "DES")

/** Day heading for watch history: "HARI INI", "KEMARIN", "28 OKT", or "28 DES 2025" for another year. */
fun historyDayLabel(timestampMs: Long, nowMs: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()): String {
    val at = Calendar.getInstance(zone).apply { timeInMillis = timestampMs }
    val day = Calendar.getInstance(zone).apply { timeInMillis = nowMs }
    fun sameDay() = at.get(Calendar.YEAR) == day.get(Calendar.YEAR) && at.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR)
    if (sameDay()) return "HARI INI"
    day.add(Calendar.DAY_OF_YEAR, -1)
    if (sameDay()) return "KEMARIN"
    val label = "${at.get(Calendar.DAY_OF_MONTH)} ${MONTHS[at.get(Calendar.MONTH)]}"
    return if (at.get(Calendar.YEAR) == Calendar.getInstance(zone).apply { timeInMillis = nowMs }.get(Calendar.YEAR)) label
    else "$label ${at.get(Calendar.YEAR)}"
}

enum class Day(val apiValue: String, val label: String, val short: String) {
    MONDAY("senin", "Senin", "Sen"),
    TUESDAY("selasa", "Selasa", "Sel"),
    WEDNESDAY("rabu", "Rabu", "Rab"),
    THURSDAY("kamis", "Kamis", "Kam"),
    FRIDAY("jumat", "Jumat", "Jum"),
    SATURDAY("sabtu", "Sabtu", "Sab"),
    SUNDAY("minggu", "Minggu", "Min");

    companion object {
        fun today(): Day = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
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
fun relativeTime(raw: String, nowMs: Long = System.currentTimeMillis()): String {
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Jakarta")
        isLenient = false
    }
    val at = runCatching { format.parse(raw.trim()) }.getOrNull()?.time ?: return ""
    return relativeAge(nowMs - at)
}

/** Age in ms -> "3 jam lalu"; more than a minute in the future (clock skew) -> "". */
fun relativeAge(diffMs: Long): String = when {
    diffMs < -60_000 -> ""
    diffMs < 60_000 -> "baru saja"
    diffMs < 3_600_000 -> "${diffMs / 60_000} mnt lalu"
    diffMs < 86_400_000 -> "${diffMs / 3_600_000} jam lalu"
    else -> "${diffMs / 86_400_000} hari lalu"
}

/** Text of the offline pill: how old the cached data on screen is, when known. */
fun offlineLabel(cacheServedAt: Long?, nowMs: Long): String {
    val age = cacheServedAt?.let { relativeAge(nowMs - it) }.orEmpty()
    return if (age.isEmpty()) "Offline" else "Offline · data $age"
}

fun Throwable.friendlyMessage(): String = when (this) {
    is java.net.UnknownHostException -> "Tidak ada koneksi internet."
    is java.net.SocketTimeoutException -> "Server terlalu lama merespons."
    is java.io.IOException -> "Gagal terhubung ke server."
    is retrofit2.HttpException -> "Server sedang bermasalah (${code()})."
    else -> message ?: "Terjadi kesalahan."
}

fun statusLabel(status: String): String = when (status.uppercase()) {
    "ONGOING" -> "Tayang"
    "FINISHED", "COMPLETED", "COMPLETE" -> "Tamat"
    "UPCOMING", "NOT_YET_AIRED" -> "Segera"
    else -> status.lowercase().replaceFirstChar { it.titlecase(ID) }
}
