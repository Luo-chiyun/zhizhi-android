package app.zhizhi.policy

import app.zhizhi.data.AppCategory
import app.zhizhi.data.MonitorSettings

/** 用户在卡片上按下的按钮。 */
enum class NudgeAnswer {
    /** 已完成，退出 */
    DONE_EXIT,

    /** 还在查，再给 N 分钟 */
    SNOOZE,

    /** 我走神了，退出 */
    DISTRACTED,

    /** 我正在做正事，请勿打扰 */
    WORKING,

    /**
     * 别再提醒这个应用 —— 把当前这个包一键加入忽略名单。
     * 不是"再给我点时间"，而是"这个应用我不管了"，所以它不进冷却期而是永久生效。
     */
    MUTE_APP,

    /** 游戏前确认：确定要玩 */
    GAME_ENTER,

    /** 游戏前确认：算了 */
    GAME_CANCEL,

    /** 休息结束：回到学习 */
    BREAK_BACK,

    /** 休息结束：再休息 5 分钟 */
    BREAK_EXTEND,
}

/** 策略引擎决定"现在该做什么"。它不碰 Android API，只做判断，方便单独测试。 */
sealed interface OverlayAction {
    data class ShowNudge(
        val packageName: String,
        val appLabel: String,
        val activeMs: Long,
        /** 这是本次会话第几次提醒，从 1 开始 */
        val nth: Int,
        val snoozeMinutes: Int,
    ) : OverlayAction

    data class ShowGameConfirm(
        val packageName: String,
        val appLabel: String,
    ) : OverlayAction

    data object ShowBreakOver : OverlayAction

    data object HideCard : OverlayAction

    /** 把用户送回桌面。这是"退出"两个按钮的实现方式。 */
    data object GoHome : OverlayAction
}

/**
 * 分级提醒策略。核心是三条不相干的时间线：
 *
 *  1. 娱乐类：累计前台时长 >= 阈值 → 提醒一次；之后按 repeatIntervalSec 控制频率。
 *     用户每次回答都会重置累计时长，所以"再给 5 分钟"是真的重新计时。
 *  2. 游戏类：进入后只问一次，问完整个会话静默——这是为了避免局内弹窗造成的断触。
 *  3. 休息计时器：到点提醒一次，之后 60 秒内不重复。
 *
 * 会话（Session）= 同一次连续停留。一旦切走、锁屏、或离开学习时段，会话清零。
 */
class ReminderPolicy {

    private class Session(val packageName: String, var lastTickAtMs: Long) {
        var activeMs: Long = 0L
        var nudges: Int = 0
        var nextNudgeAtMs: Long = 0L
        var gamePrompted: Boolean = false
        var gameDeclined: Boolean = false
    }

    private var session: Session? = null
    private var cardShowing = false

    /** 上一次弹过"休息结束"的那次休息（用 breakEndsAtMs 当 key），避免同一次休息弹两遍。 */
    private var lastBreakPromptKey = 0L

    /** "用户已经回答过退出"的应用 -> 该包名在什么时间之前不再被提醒。 */
    private val suppressed = mutableMapOf<String, Long>()

    /**
     * 休息到点只提醒一次。key 用 breakEndsAtMs：每一次休息的结束时刻都不同，
     * 所以同一个 key 出现第二次就说明是同一次休息被重复触发。
     */
    @Synchronized
    fun markBreakOverPrompted(breakEndsAtMs: Long): Boolean {
        if (breakEndsAtMs == lastBreakPromptKey) return false
        lastBreakPromptKey = breakEndsAtMs
        return true
    }

    fun isTracking(): Boolean = session != null

    fun sessionActiveMs(): Long = session?.activeMs ?: 0L

    fun currentPackage(): String? = session?.packageName

    fun isCardShowing(): Boolean = cardShowing

    /**
     * 卡片最终没能出现在屏幕上（降级成通知了）。
     *
     * 这一条必须回传给策略：否则策略会一直以为卡片还挂在屏幕上（cardShowing=true），
     * 于是整段会话都不再提醒——而用户其实什么都没看到。
     * 清零之后，原有的 nextNudgeAtMs 节奏仍然生效，不会变成连续追问。
     */
    fun onCardUnavailable() {
        cardShowing = false
    }

    fun notifyCardDismissed() {
        cardShowing = false
    }

    fun notifyCardShown() {
        cardShowing = true
    }

    /** 离开监测条件时调用（锁屏、离开时段、被暂停）。 */
    @Synchronized
    fun reset() {
        session = null
        cardShowing = false
    }

    /**
     * 主循环每个 tick 调一次；用户点卡片/通知按钮时也会从主线程调 [onAnswer]。
     * **两条路径是并发的**（主循环在 Dispatchers.Default，点击回调在主线程），
     * 所以这里必须加锁——否则 suppressed / session / cardShowing 会出现丢失更新，
     * 表现就是"回答了但冷却没生效，下一秒又被问一遍"。
     */
    @Synchronized
    fun onTick(
        now: Long,
        foregroundPackage: String?,
        category: AppCategory,
        settings: MonitorSettings,
        appLabel: String,
    ): List<OverlayAction> {
        val out = mutableListOf<OverlayAction>()

        // ---- 没有可监控目标：结束会话 ----
        // 用 isMonitored 而不是 "!= IGNORED"：将来若再加分类，默认不会被误当成娱乐类提醒。
        if (foregroundPackage == null || !category.isMonitored) {
            if (session != null) {
                session = null
                if (cardShowing) out += OverlayAction.HideCard
                cardShowing = false
            }
            return out
        }

        // ---- 会话切换 ----
        val s = session?.takeIf { it.packageName == foregroundPackage }
            ?: Session(foregroundPackage, lastTickAtMs = now).also { fresh ->
                session = fresh
                cardShowing = false
                // 用户刚说过"我退出了"的那个应用：短时间内不要再追着问同一个问题。
                suppressed[foregroundPackage]?.let { until ->
                    if (until > now) fresh.nextNudgeAtMs = until
                }
            }

        // 累计前台时长。单次 tick 的上限兜底：即使主循环被系统调度卡住，
        // 也不会因为一次巨大的时间差把"3 秒"算成"3 分钟"。
        val delta = (now - s.lastTickAtMs).coerceIn(0L, MAX_TICK_DELTA_MS)
        s.activeMs += delta
        s.lastTickAtMs = now

        when (category) {
            AppCategory.GAME -> {
                // `now >= s.nextNudgeAtMs` 这一道只在设了"重新进入冷却"时才起作用；
                // 同一次停留里不重复问靠的是 gamePrompted —— 它跟着 session 走，
                // 所以只要用户没离开这个应用，无论怎么点都不会被问第二遍。
                if (settings.gameConfirmEnabled &&
                    !s.gamePrompted &&
                    !cardShowing &&
                    now >= s.nextNudgeAtMs
                ) {
                    s.gamePrompted = true
                    cardShowing = true
                    out += OverlayAction.ShowGameConfirm(foregroundPackage, appLabel)
                }
            }

            AppCategory.ENTERTAINMENT -> {
                if (cardShowing) return out
                val reachedThreshold = s.activeMs >= settings.firstNudgeAfterSec * 1000L
                val allowedByCadence = now >= s.nextNudgeAtMs
                if (reachedThreshold && allowedByCadence) {
                    s.nudges += 1
                    s.nextNudgeAtMs = if (settings.repeatIntervalSec <= 0) {
                        Long.MAX_VALUE
                    } else {
                        now + settings.repeatIntervalSec * 1000L
                    }
                    cardShowing = true
                    out += OverlayAction.ShowNudge(
                        packageName = foregroundPackage,
                        appLabel = appLabel,
                        activeMs = s.activeMs,
                        nth = s.nudges,
                        snoozeMinutes = (settings.snoozeSec / 60).coerceAtLeast(1),
                    )
                }
            }

            AppCategory.IGNORED -> Unit
        }

        return out
    }

    @Synchronized
    fun onAnswer(
        answer: NudgeAnswer,
        now: Long,
        settings: MonitorSettings,
    ): List<OverlayAction> {
        val s = session
        val out = mutableListOf<OverlayAction>()
        cardShowing = false
        out += OverlayAction.HideCard

        when (answer) {
            NudgeAnswer.GAME_ENTER -> Unit

            NudgeAnswer.GAME_CANCEL -> {
                // ⚠️ 刻意**不**清 session。
                //
                // 曾经在这里写 `session = null`，造成过一串很隐蔽的问题：
                // 用户点"算了"之后一键回桌面要 1.5 秒才生效，这期间前台仍然是那个游戏，
                // 于是下一 tick 就建出一个新会话 —— 新会话的 gamePrompted 是 false，
                // 结果 0.4 秒后又弹一次，连着弹五六次。
                //
                // 保留 session 就天然保证了"同一次停留里只问一次"；而用户真的退出再重新打开时
                // 是一个**新会话**，会照常弹出确认 —— 这正是想要的行为（反复重进应该每次都问）。
                s?.gameDeclined = true
                s?.let { applyReEntryCooldown(it, now, settings) }
                out += OverlayAction.GoHome
            }

            NudgeAnswer.MUTE_APP -> {
                s?.let {
                    // Long.MAX_VALUE：不只是"过一会儿再说"，而是这次运行内彻底不再问。
                    // 分类本身也会被改成"不监控"，那之后连会话都不会建。
                    suppressed[it.packageName] = Long.MAX_VALUE
                }
            }

            NudgeAnswer.DONE_EXIT, NudgeAnswer.DISTRACTED -> {
                // 同样不清 session，理由见上面 GAME_CANCEL 的注释。
                // 他说了要退出，所以把计数清零；如果他不走而是留在这儿，就按重复间隔再问。
                s?.let {
                    it.activeMs = 0L
                    it.nextNudgeAtMs = nextRepeatAt(now, settings)
                    applyReEntryCooldown(it, now, settings)
                }
                out += OverlayAction.GoHome
            }

            NudgeAnswer.SNOOZE -> s?.let {
                it.activeMs = 0L
                it.nextNudgeAtMs = now + settings.snoozeSec * 1000L
            }

            NudgeAnswer.WORKING -> s?.let {
                it.activeMs = 0L
                it.nextNudgeAtMs = now + settings.graceSec * 1000L
            }

            NudgeAnswer.BREAK_BACK, NudgeAnswer.BREAK_EXTEND -> Unit
        }
        return out
    }

    /**
     * 可选的"离开再重新进入"冷却。
     *
     * 注意它管的是**新会话**（离开之后重新进入），不是同一次停留——同一次停留由 session 自身的
     * `gamePrompted` / `nextNudgeAtMs` 负责。默认 0 = 不设，也就是每次重新进入都照常提醒，
     * 这样反复重进同一个应用来测试时每一次都会弹。
     */
    private fun applyReEntryCooldown(session: Session, now: Long, settings: MonitorSettings) {
        val ms = settings.answerCooldownSec.coerceAtLeast(0) * 1000L
        if (ms > 0L) suppressed[session.packageName] = now + ms
    }

    /** repeatIntervalSec = 0 表示这一轮停留里不再提醒。 */
    private fun nextRepeatAt(now: Long, settings: MonitorSettings): Long =
        if (settings.repeatIntervalSec <= 0) Long.MAX_VALUE else now + settings.repeatIntervalSec * 1000L

    companion object {
        /** 单次 tick 最多计入 5 秒。轮询间隔最长也就是几秒，正常不会碰到。 */
        private const val MAX_TICK_DELTA_MS = 5_000L
    }
}
