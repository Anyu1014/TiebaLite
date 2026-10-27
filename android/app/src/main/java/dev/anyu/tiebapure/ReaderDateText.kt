package dev.anyu.tiebapure

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class PostTimestampFormat(pattern: String) {
    MAIN_POST("HH:mm:ss'「'yyyy-MM-dd'」'"),
    SUBPOST("HH:mm'「'MM-dd'」'");

    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern(pattern, Locale.ROOT)
}

internal fun postTimestampText(epoch: Long, format: PostTimestampFormat, zone: ZoneId = ZoneId.systemDefault()): String {
    if (epoch !in 1..253402300799L) return ""
    return Instant.ofEpochSecond(epoch).atZone(zone).format(format.formatter)
}

fun readerDateText(epoch: Long, now: Long = System.currentTimeMillis() / 1000, zone: ZoneId = ZoneId.systemDefault()): String {
    if (epoch !in 1..253402300799L) return ""
    val elapsed = (now - epoch).coerceAtLeast(0)
    if (elapsed < 60) return "刚刚"
    if (elapsed < 3600) return "${elapsed / 60}分钟前"
    val date = Instant.ofEpochSecond(epoch).atZone(zone)
    val today = Instant.ofEpochSecond(now).atZone(zone).toLocalDate()
    val pattern = when {
        date.toLocalDate() == today -> "HH:mm"
        date.toLocalDate() == today.minusDays(1) -> "'昨天 'HH:mm"
        date.year == today.year -> "MM-dd HH:mm"
        else -> "yyyy-MM-dd"
    }
    return DateTimeFormatter.ofPattern(pattern).format(date)
}
