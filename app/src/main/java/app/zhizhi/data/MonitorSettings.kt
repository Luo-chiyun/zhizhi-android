package app.zhizhi.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * 全应用共用一个 DataStore 文件。数据只落在应用私有目录：
 * /data/data/app.zhizhi/files/datastore/focus_guard.preferences_pb
 */
internal val Context.focusDataStore: DataStore<Preferences> by preferencesDataStore(name = "zhizhi")

enum class AppCategory {
    /** 不监控。默认值——本应用不会自动接管任何应用。 */
    IGNORED,

    /** 娱乐 / 社交：超时后温和提醒 */
    ENTERTAINMENT,

    /** 游戏：启动前确认一次，局内完全静默 */
    GAME,
    ;

    /** 只有这两类会被计时和提醒。IGNORED 只是"记下了不监控"。 */
    val isMonitored: Boolean
        get() = this == ENTERTAINMENT || this == GAME

    companion object {
        fun fromName(name: String?): AppCategory =
            entries.firstOrNull { it.name == name } ?: IGNORED
    }
}

/** 学习时段。用"当天的第几分钟"表示，避免携带日期与时区。 */
data class Schedule(
    // 默认**关闭**时段限制：装完就按总开关全天监测。
    // enabled=false 时 contains() 恒为 true => 相当于"不限时段"。
    val enabled: Boolean = false,
    val startMinute: Int = 20 * 60,
    val endMinute: Int = 22 * 60,
) {
    /** minuteOfDay 落在时段内则返回 true。start == end 视为全天。跨午夜（如 22:00–02:00）同样成立。 */
    fun contains(minuteOfDay: Int): Boolean {
        if (!enabled) return true
        if (startMinute == endMinute) return true
        return if (startMinute < endMinute) {
            minuteOfDay in startMinute until endMinute
        } else {
            minuteOfDay >= startMinute || minuteOfDay < endMinute
        }
    }

    companion object {
        val DEFAULT = Schedule()
    }
}

data class MonitorSettings(
    val masterEnabled: Boolean = false,
    val schedule: Schedule = Schedule.DEFAULT,
    /** 手动"暂时停止监测"的截止时间戳；0 表示未暂停 */
    val pausedUntilMs: Long = 0L,
    /** 连续使用多少秒后第一次提醒 */
    val firstNudgeAfterSec: Int = 300,
    /** 之后每隔多少秒可再次提醒；0 = 一次会话只提醒一次 */
    val repeatIntervalSec: Int = 600,
    /** 用户点"还在查，再给 N 分钟"授予的秒数 */
    val snoozeSec: Int = 300,
    /** 用户点"我正在做正事"后的静默秒数 */
    val graceSec: Int = 900,
    /**
     * 回答过之后，如果用户**离开再重新进入**同一个应用，在多少秒内不再提醒。0 = 不设。
     *
     * 注意它管的是"重新进入"，不是"同一次停留"——
     * 同一次停留里不重复问是由会话自身保证的（游戏的 gamePrompted、娱乐类的计数清零 + 重复间隔），
     * 所以默认 0 意味着"每次重新打开都会照常提醒"，这正是反复重进同一个应用测试时想要的行为。
     *
     * 设成 30 秒之类的值，用途是"别我一出去一进来就立刻问我"。
     */
    val answerCooldownSec: Int = 0,
    val gameConfirmEnabled: Boolean = true,
    /** 提醒卡片贴顶还是贴底。默认贴顶：底部容易和手势条、输入法打架。 */
    val cardAtBottom: Boolean = false,
    val onboardingDone: Boolean = false,
    val autoStartOnBoot: Boolean = false,
    /** 休息计时器默认时长 */
    val breakDurationMin: Int = 15,
    /** 休息结束时点"再休息"的时长 */
    val breakExtendMin: Int = 5,
    /** 休息结束时间戳；0 表示没有在休息 */
    val breakEndsAtMs: Long = 0L,
    /**
     * 回答"退出"之后，是否顺便尝试清掉那个应用的后台进程。
     *
     * ⚠️ **Android 14 起系统禁止第三方应用结束别的应用的进程**，
     * 传别人的包名进 killBackgroundProcesses 会被忽略并记一句 Invalid packageName。
     * 所以这个开关只在 Android 13 及以下真正生效，14+ 的机型上界面上会标成"本机不支持"。
     */
    val killTargetOnExit: Boolean = false,
) {
    fun isPausedNow(now: Long): Boolean = pausedUntilMs > now

    /** 是否正在休息（休息期间完全不打扰）。 */
    fun isResting(now: Long): Boolean = breakEndsAtMs > now

    /** 休息已到点但还没被处理（到点后主循环会弹一次"回来吧"的卡片并清掉它）。 */
    fun isBreakFinished(now: Long): Boolean = breakEndsAtMs in 1..now
}
