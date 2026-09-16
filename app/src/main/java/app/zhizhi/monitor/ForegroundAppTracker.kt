package app.zhizhi.monitor

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * 前台应用识别。只用 UsageStatsManager，**不使用 AccessibilityService**。
 *
 * 为什么不解析"当前前台是谁"而是靠事件流维护状态：
 * UsageStatsManager 的事件是按时间戳追加的，某次查询窗口里可能一个事件都没有
 * （比如用户连续 20 分钟停在同一个应用里）。如果每次都从"窗口内最后一个事件"推断，
 * 就会在这时候误判成"前台为空"从而把计时清零。所以正确做法是：
 *   - 只认 ACTIVITY_RESUMED（API 29 之前叫 MOVE_TO_FOREGROUND）
 *   - 把状态保存在内存里，跨轮询保持
 *   - 只在屏幕关闭 / 锁屏时才清空
 *
 * 已知局限（不是 bug，是系统限制）：
 *   - 事件是异步写入的，从应用切到前台到我们能看见，通常有 0.5–2 秒延迟。
 *   - 系统可能对查询频率做限流；轮询间隔不要低于 1 秒。
 *   - 部分定制 ROM 会把某些应用的事件吃掉，这种情况下计时会漏。
 */
class ForegroundAppTracker(context: Context) {

    private val usm: UsageStatsManager? =
        context.getSystemService(UsageStatsManager::class.java)

    private var lastPolledAtMs = 0L

    @Volatile
    var foregroundPackage: String? = null
        private set

    /** 服务刚起来时调用：回溯一段时间，把"当前前台是谁"补上。 */
    fun seed(now: Long = System.currentTimeMillis()) {
        foregroundPackage = null
        lastPolledAtMs = now - SEED_LOOKBACK_MS
        poll(now)
    }

    fun reset() {
        foregroundPackage = null
        lastPolledAtMs = 0L
    }

    /** 返回当前前台包名（可能是 null，表示"没有可信目标"）。 */
    fun poll(now: Long = System.currentTimeMillis()): String? {
        val mgr = usm ?: return null
        val begin = when {
            lastPolledAtMs == 0L -> now - SEED_LOOKBACK_MS
            // 回看一点点重叠区间，避免事件在自己两次查询的边界上被漏掉
            else -> (lastPolledAtMs - OVERLAP_MS).coerceAtLeast(now - MAX_LOOKBACK_MS)
        }
        lastPolledAtMs = now

        val events = runCatching { mgr.queryEvents(begin, now) }.getOrNull() ?: return foregroundPackage
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                EVENT_RESUMED -> event.packageName?.takeIf { it.isNotBlank() }?.let {
                    foregroundPackage = it
                }

                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.KEYGUARD_SHOWN,
                UsageEvents.Event.DEVICE_SHUTDOWN,
                -> foregroundPackage = null

                else -> Unit
            }
        }
        if (DEBUG) Log.v(TAG, "foreground=$foregroundPackage")
        return foregroundPackage
    }

    companion object {
        private const val TAG = "FgTracker"
        private const val DEBUG = false

        /** 服务启动时的回溯窗口：30 分钟，足够覆盖"用户已经刷了很久"的情况。 */
        private const val SEED_LOOKBACK_MS = 30 * 60_000L

        /** 单次查询最多回看 5 分钟，避免长时间暂停后一次性拉回过多事件。 */
        private const val MAX_LOOKBACK_MS = 5 * 60_000L

        private const val OVERLAP_MS = 2_000L

        @Suppress("NewApi")
        val EVENT_RESUMED: Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                UsageEvents.Event.ACTIVITY_RESUMED
            } else {
                UsageEvents.Event.MOVE_TO_FOREGROUND
            }
    }
}
