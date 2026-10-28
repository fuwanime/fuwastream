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
    val diff = nowMs - at
    return when {
        diff < -60_000 -> ""
        diff < 60_000 -> "baru saja"
        diff < 3_600_000 -> "${diff / 60_000} mnt lalu"
        diff < 86_400_000 -> "${diff / 3_600_000} jam lalu"
        else -> "${diff / 86_400_000} hari lalu"
    }
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
