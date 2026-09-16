package app.zhizhi.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

fun hhmm(minuteOfDay: Int): String {
    val m = ((minuteOfDay % 1440) + 1440) % 1440
    return "%02d:%02d".format(m / 60, m % 60)
}

fun formatClock(epochMs: Long): String = clockFormat.format(Date(epochMs))

/** "5 分 12 秒" / "42 秒" / "1 小时 4 分" */
fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return when {
        h > 0 -> "$h 小时 $m 分"
        m > 0 -> "$m 分 $s 秒"
        else -> "$s 秒"
    }
}

/** "5:12" / "1:04:12" —— 倒计时用 */
fun formatCountdown(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun minuteOfDayNow(epochMs: Long = System.currentTimeMillis()): Int {
    val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
    return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
}

fun daysAgoKey(days: Int): String {
    val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -days) }
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
}

fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

/**
 * 极限紧凑的时长，专给"一格只有 4 个汉字宽"的统计卡用。
 *
 * 原来统计卡用的是 [formatDuration]（"1 小时 4 分"，7 个字符带空格），
 * 在三分之一屏宽里会折成两行，把三张卡撑成一高一低。
 * 这里压到最多 4 个字符，保证永远单行。
 */
fun formatDurationTiny(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return when {
        h > 0 -> "${h}时${m}分"
        m > 0 -> "${m}分"
        else -> "${sec}秒"
    }
}
