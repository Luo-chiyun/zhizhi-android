package app.zhizhi.monitor

import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.zhizhi.Graph
import app.zhizhi.R
import app.zhizhi.data.AppCategory
import app.zhizhi.data.DayStats
import app.zhizhi.data.DiagnosticsStore
import app.zhizhi.data.MonitorSettings
import app.zhizhi.notify.Notifications
import app.zhizhi.overlay.CardOutcome
import app.zhizhi.overlay.CardSpec
import app.zhizhi.overlay.OverlayController
import app.zhizhi.policy.NudgeAnswer
import app.zhizhi.policy.OverlayAction
import app.zhizhi.policy.ReminderPolicy
import app.zhizhi.util.Permissions
import app.zhizhi.util.formatClock
import app.zhizhi.util.hhmm
import app.zhizhi.util.minuteOfDayNow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 后台监测服务。
 *
 * 设计要点：
 *  - 前台服务类型用 specialUse（Android 14+ 必须声明类型；本应用的用途不落在任何一个既有类型里）。
 *  - 屏幕上没有常驻 UI。唯一的常态窗口是 1×1 的隐形锚点。
 *  - 轮询节奏：学习时段内固定 1 秒，时段外 20 秒。
 *  - **提醒不会因为悬浮窗出问题而丢失**：卡片加不上、或探测多次仍不可见，
 *    都会自动降级为一条带同样选项的横幅通知，并把原因写进通知副标题。
 *
 * ⚠️ 一条贯穿全局的纪律：**所有 View / WindowManager 操作都在主线程。**
 * 主循环跑在 Dispatchers.Default 上，那里没有 Looper，直接 addView 会抛
 * "Can't create handler inside thread ... that has not called Looper.prepare()"。
 * 所以 OverlayController 暴露的是 suspend API，内部自己切主线程——调用方不要绕开它。
 */
class MonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var tracker: ForegroundAppTracker
    private lateinit var overlay: OverlayController
    private lateinit var diagnostics: DiagnosticsStore
    private val policy = ReminderPolicy()

    private var loopJob: Job? = null

    /**
     * 屏幕状态自己维护一份，但**不把它当成唯一真相**：每 [SCREEN_SYNC_INTERVAL_MS] 会跟系统
     * 重新对齐一次。曾经踩过的坑：只靠 ACTION_USER_PRESENT 广播，某次没收到，screenInteractive
     * 就永远停在 false，监测静悄悄地彻底停摆。
     */
    private var screenInteractive = true
    private var lastScreenSyncAtMs = 0L

    private var lastPolledForeground: String? = null
    private var lastNotifKey: String? = null
    private var lastStateLine: String? = null

    private var lastTickAtMs = 0L
    private var accumTrackedMs = 0L
    private var exitTarget: String? = null

    /** 每次展示卡片自增。用于让过期的可见性探测自行作废，避免给已经换掉的卡片误判。 */
    private var cardGeneration = 0

    private val labelCache = mutableMapOf<String, String>()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenInteractive = false
                    scope.launch { endSession() }
                    tracker.reset()
                }

                Intent.ACTION_SCREEN_ON -> {
                    // 屏幕亮了还不代表能用：可能还锁着。交给 refreshScreenState() 判定。
                    refreshScreenState(force = true)
                }

                Intent.ACTION_USER_PRESENT -> {
                    refreshScreenState(force = true)
                    tracker.seed()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Graph.ensure(this)
        diagnostics = Graph.diagnostics
        Notifications.ensureChannels(this)
        tracker = ForegroundAppTracker(this)
        overlay = OverlayController(this, diagnostics).apply {
            onAnswer = { answer -> handleAnswer(answer) }
        }
        refreshScreenState()
        registerScreenReceiver()

        val channel = Notifications.alertChannelStatus(this)
        diagnostics.record(
            "Service",
            buildString {
                append("onCreate sdk=").append(Build.VERSION.SDK_INT)
                append(" 厂商=").append(Build.MANUFACTURER).append('/').append(Build.MODEL)
                append(" 悬浮窗权限=").append(Permissions.canDrawOverlays(this@MonitorService))
                append(" 使用情况访问=").append(Permissions.hasUsageAccess(this@MonitorService))
                append(" 通知=").append(channel.notificationsEnabled)
                append(" 提醒通道=").append(channel.importanceText())
            },
        )
        if (channel.channelExists && channel.importance < NotificationManager.IMPORTANCE_HIGH) {
            diagnostics.record(
                "Service",
                "警告：提醒通道被降到了「${channel.importanceText()}」，横幅通知不会出现。" +
                    "请在系统通知设置里把「知止 → 提醒（降级通道）」设为允许横幅。",
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                diagnostics.record("Service", "收到 ACTION_STOP，主动停止")
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_ANSWER -> {
                val name = intent.getStringExtra(EXTRA_ANSWER)
                val answer = runCatching { NudgeAnswer.valueOf(name ?: "") }.getOrNull()
                if (answer == null) {
                    diagnostics.record("Service", "收到无法识别的回答：$name")
                } else {
                    diagnostics.record("Service", "收到来自通知的回答：$answer")
                    handleAnswer(answer)
                }
            }

            ACTION_PAUSE_30 -> Graph.scope.launch {
                Graph.settings.edit { it.copy(pausedUntilMs = System.currentTimeMillis() + PAUSE_STEP_MS) }
            }

            ACTION_RESUME -> Graph.scope.launch {
                Graph.settings.edit { it.copy(pausedUntilMs = 0L) }
            }

            ACTION_BREAK -> {
                diagnostics.record("Break", "从通知开始休息")
                Graph.scope.launch {
                    Graph.settings.edit {
                        it.copy(breakEndsAtMs = System.currentTimeMillis() + it.breakDurationMin * 60_000L)
                    }
                    Graph.stats.bump { day -> day.copy(breakTaken = day.breakTaken + 1) }
                }
            }

            ACTION_BREAK_END -> {
                diagnostics.record("Break", "从通知结束休息")
                Graph.scope.launch { Graph.settings.edit { it.copy(breakEndsAtMs = 0L) } }
            }

            null -> diagnostics.record("Service", "被系统重启（intent=null），按原设置恢复")
            else -> Unit
        }

        if (!startForegroundSafely()) return START_NOT_STICKY
        running.value = true
        refreshNotification(force = true)
        startLoop()

        if (intent?.action == ACTION_TEST_CARD) runCardSelfTest()

        return START_STICKY
    }

    /**
     * 用户在"最近任务"里划掉本应用。国产 ROM 上这一下经常连服务一起带走，
     * 所以记一条日志：下次再看日志就能区分"被划掉"和"被系统杀"。
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        diagnostics.record("Service", "任务被移除（用户划掉最近任务）")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        running.value = false
        anchorVisible.value = false
        diagnostics.record("Service", "onDestroy")
        loopJob?.cancel()
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        // onDestroy 在主线程，可以直接走同步版本。
        overlay.removeAllOnMainThread()
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------ 前台服务启动

    /**
     * Android 15（targetSdk 35）收紧了 SYSTEM_ALERT_WINDOW 提供的豁免：
     * 应用必须"已经持有一个可见的 TYPE_APPLICATION_OVERLAY 窗口"，才允许从后台启动前台服务。
     * 所以顺序必须是：先挂上锚点悬浮窗 → 再 startForeground。
     * 反过来写，在被系统从后台拉起（开机自启 / START_STICKY 重启）时会直接抛
     * ForegroundServiceStartNotAllowedException。
     *
     * 这里刻意用同步的主线程版本（`onStartCommand` 本来就在主线程），
     * 因为这个先后顺序不能交给协程调度去碰运气。
     */
    @Suppress("NewApi")
    private fun startForegroundSafely(): Boolean {
        if (Permissions.canDrawOverlays(this)) {
            if (!overlay.addAnchorOnMainThread()) {
                diagnostics.record("Service", "锚点悬浮窗未建立，后台启动前台服务可能被系统拒绝")
            }
        } else {
            diagnostics.record("Service", "没有悬浮窗权限，跳过锚点窗口")
        }
        anchorVisible.value = overlay.isAnchorVisibleOnMainThread()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

        return runCatching {
            ServiceCompat.startForeground(
                this,
                Notifications.ID_MONITOR,
                buildNotification(),
                type,
            )
            diagnostics.record("Service", "前台服务已启动 type=$type")
            true
        }.getOrElse { error ->
            diagnostics.record(
                "Service",
                "前台服务被拒绝 ${error.javaClass.simpleName}: ${error.message}",
            )
            Log.e(TAG, "前台服务启动被拒绝", error)
            overlay.removeAllOnMainThread()
            anchorVisible.value = false
            if (Permissions.canPostNotifications(this)) Notifications.postNeedsPermission(this)
            stopSelf()
            false
        }
    }

    // ------------------------------------------------------------------ 主循环

    private fun startLoop() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            tracker.seed()
            while (isActive) {
                val tickAt = System.currentTimeMillis()
                val delta = if (lastTickAtMs == 0L) 0L else (tickAt - lastTickAtMs).coerceIn(0L, 5_000L)
                lastTickAtMs = tickAt

                refreshScreenState(tickAt)

                val settings = Graph.settings.settings.value
                val minuteOfDay = minuteOfDayNow(tickAt)

                // ---- 休息到点：必须在 canMonitor 判断**之前**处理 ----
                // 因为"正在休息"会让 canMonitor 变成 false，如果放在后面就永远不会执行。
                // 到点后立刻清掉 breakEndsAtMs（恢复监测），并弹一次回来提醒；
                // 用户点"再休息"会重新设定它。这样也避免了"用户无视卡片就被永久挡住"。
                if (settings.isBreakFinished(tickAt)) {
                    val breakEndsAt = settings.breakEndsAtMs
                    if (policy.markBreakOverPrompted(breakEndsAt)) {
                        diagnostics.record("Break", "休息结束，弹回来提醒")
                        scope.launch { execute(OverlayAction.ShowBreakOver, settings) }
                    }
                    Graph.scope.launch { Graph.settings.edit { it.copy(breakEndsAtMs = 0L) } }
                }

                val blockReason = when {
                    !settings.masterEnabled -> "总开关关闭"
                    settings.isResting(tickAt) ->
                        "休息中（到 ${formatClock(settings.breakEndsAtMs)}）"
                    settings.isPausedNow(tickAt) -> "已暂停至 ${formatClock(settings.pausedUntilMs)}"
                    !settings.schedule.contains(minuteOfDay) ->
                        "不在学习时段 ${hhmm(settings.schedule.startMinute)}–${hhmm(settings.schedule.endMinute)}"
                    !screenInteractive -> "屏幕未点亮或未解锁"
                    !Permissions.hasUsageAccess(this@MonitorService) -> "缺少使用情况访问权限"
                    else -> null
                }

                if (blockReason != null) {
                    endSession()
                    tracker.reset()
                    lastTickAtMs = 0L
                    publishLoopState("monitor=off 原因=$blockReason")
                    refreshNotification()
                    delay(SLEEP_TICK_MS)
                    continue
                }

                val foreground = tracker.poll(tickAt)
                lastPolledForeground = foreground

                val category = Graph.classifier.categoryOf(foreground)
                publishLoopState(
                    "monitor=on fg=${foreground ?: "-"} cat=$category card=${policy.isCardShowing()}",
                )

                val actions = policy.onTick(
                    now = tickAt,
                    foregroundPackage = foreground,
                    category = category,
                    settings = settings,
                    appLabel = labelOf(foreground),
                )
                for (action in actions) execute(action, settings)

                if (policy.isTracking()) accumTrackedMs += delta
                flushTrackedTime()
                refreshNotification()

                delay(IDLE_TICK_MS)
            }
        }
    }

    /**
     * 主循环每转一圈都会调用：心跳自增 + 把当前状态写进内存 StateFlow。
     *
     * 这两个值都**不经过 DataStore**，所以只要服务活着，诊断页上就一定能看到它在动。
     * 这是"诊断本身会不会撒谎"这个问题的答案：心跳在涨 = 循环在跑，与磁盘无关。
     *
     * 只有状态跳变时才额外写一条持久化记录，避免把 60 条的环形缓冲刷爆。
     */
    private fun publishLoopState(line: String) {
        heartbeat.update { it + 1L }
        loopState.value = line
        if (line == lastStateLine) return
        lastStateLine = line
        diagnostics.record("Loop", line)
    }

    private fun refreshScreenState(
        now: Long = System.currentTimeMillis(),
        force: Boolean = false,
    ) {
        // force 用于广播回调：解锁那一刻必须立刻重算，不能因为"距离上次同步不到 5 秒"被吞掉。
        // 真机日志里出现过解锁后 17 秒才恢复监测——因为被节流挡了，然后主循环要等 20 秒的
        // SLEEP_TICK 才醒。广播是"现在就变化了"的信号，不该受节流约束。
        if (!force && now - lastScreenSyncAtMs < SCREEN_SYNC_INTERVAL_MS) return
        lastScreenSyncAtMs = now
        val power = getSystemService(PowerManager::class.java)
        val keyguard = getSystemService(KeyguardManager::class.java)
        val interactive = power?.isInteractive ?: true
        val locked = keyguard?.isKeyguardLocked ?: false
        val next = interactive && !locked
        if (next && !screenInteractive) {
            diagnostics.record("Service", "屏幕状态重新对齐 → 可监测（interactive=$interactive locked=$locked）")
            tracker.seed()
        }
        screenInteractive = next
    }

    private suspend fun endSession() {
        flushTrackedTime()
        if (policy.isTracking() || overlay.isCardPresent()) {
            overlay.hideCard()
            Notifications.cancelReminder(this)
        }
        policy.reset()
        accumTrackedMs = 0L
    }

    /**
     * 累计的监测时长落盘。
     *
     * 两条规则：
     *  1. 会话还在进行：每满 [FLUSH_THRESHOLD_MS] 落一次盘，避免频繁写 DataStore。
     *  2. 会话已经结束：**把余数也写下去**。
     *
     * 第 2 条是必须的。原来在会话结束时直接 `accumTrackedMs = 0L`，
     * 于是一次 40 秒的停留（典型的"打开游戏 → 被问一次 → 退出"）被整段丢掉，
     * 首页那张「监测」卡永远显示 0 —— 用户看到的就是"我明明被拦了，怎么什么都没记上"。
     */
    private fun flushTrackedTime() {
        if (accumTrackedMs <= 0L) return
        if (policy.isTracking()) {
            if (accumTrackedMs < FLUSH_THRESHOLD_MS) return
        } else if (accumTrackedMs < MIN_FLUSH_MS) {
            // 不足 1 秒的零头不值得写盘
            accumTrackedMs = 0L
            return
        }
        val amount = accumTrackedMs
        accumTrackedMs = 0L
        bump { it.copy(monitoredMs = it.monitoredMs + amount) }
    }

    // --------------------------------------------------------------- 动作执行

    private suspend fun execute(action: OverlayAction, settings: MonitorSettings) {
        when (action) {
            is OverlayAction.ShowNudge -> {
                val spec = CardSpec.Nudge(
                    appLabel = action.appLabel,
                    activeMs = action.activeMs,
                    nth = action.nth,
                    snoozeMinutes = action.snoozeMinutes,
                )
                diagnostics.record(
                    "Nudge",
                    "第 ${action.nth} 次提醒 app=${action.appLabel} pkg=${action.packageName} " +
                        "已用=${action.activeMs / 1000}s",
                )
                presentCard(spec, atBottom = settings.cardAtBottom)
                bump { it.copy(nudges = it.nudges + 1) }
            }

            is OverlayAction.ShowGameConfirm -> {
                diagnostics.record("Game", "游戏前确认 app=${action.appLabel} pkg=${action.packageName}")
                presentCard(CardSpec.GameConfirm(action.appLabel), atBottom = settings.cardAtBottom)
                // 这张卡也是一次提醒，必须计入 nudges。
                // 否则"只在游戏里活动"的那天，首页「今日提醒」会一直显示 0，
                // 用户明明被问了两次，仪表盘却说无事发生。
                bump { it.copy(nudges = it.nudges + 1) }
            }

            OverlayAction.ShowBreakOver -> {
                diagnostics.record("Break", "弹休息结束卡片")
                presentCard(CardSpec.BreakOver(settings.breakExtendMin), atBottom = true)
            }

            OverlayAction.HideCard -> {
                overlay.hideCard()
                Notifications.cancelReminder(this)
            }

            OverlayAction.GoHome -> goHomeSafely()
        }
    }

    /**
     * 展示一张卡片，并且**分多次探测它是否真的可见**。
     *
     * 为什么不能只信 showCard() 的返回值：`addView()` 不抛异常，不代表窗口对用户可见。
     * 厂商 ROM 可以把窗口加进 WindowManager 之后立刻隐藏，也可以让它以 0 尺寸布局。
     *
     * 为什么不能只探测一次：`addView()` 返回后窗口还没走过第一帧布局，`width/height` 都还是 0。
     * 只等一个固定时长就下结论，设备一忙（比如正在启动游戏）就会误判成"不可见"而白白降级。
     * 所以是阶梯式探测：任意一次通过算成功，全部超时才降级。
     */
    private suspend fun presentCard(
        spec: CardSpec,
        atBottom: Boolean,
        autoHideAfterMs: Long? = null,
    ) {
        val generation = ++cardGeneration
        val outcome = overlay.showCard(spec, atBottom)
        diagnostics.record("Card", "showCard -> $outcome")

        if (outcome != CardOutcome.ADDED) {
            val reason = when (outcome) {
                CardOutcome.NO_PERMISSION -> "悬浮窗权限对该应用未生效"
                CardOutcome.NO_WINDOW_MANAGER -> "系统未提供 WindowManager"
                CardOutcome.FAILED -> "addView 抛异常 ${overlay.lastCardFailureDetail ?: "未知"}"
                CardOutcome.ADDED -> "未知"
            }
            diagnostics.record("Card", "悬浮窗不可用（$reason），降级为横幅通知")
            Notifications.postReminder(this, spec, reason)
            bump { it.copy(overlayFailures = it.overlayFailures + 1) }
            // 卡片没出现，策略不能继续以为它挂着，否则整段会话都不会再提醒。
            policy.onCardUnavailable()
        } else {
            // 第一段延迟设为 0：addView 之后如果窗口已经画好了就立刻确认。
            probeCardVisibility(spec, generation, step = 0)
        }

        if (autoHideAfterMs != null) {
            val guardedGeneration = generation
            scope.launch {
                delay(autoHideAfterMs)
                // 必须校验代次：自检卡片排了 8 秒后自动移除，如果这 8 秒里用户拿到了一张**真**的
                // 提醒卡片（比如刚好进了游戏），无条件 hideCard() 会把它一起收走。
                // 真机日志里就出现过：原神确认卡片 10:46:16 出现，10:46:17 被自检的定时器移除。
                if (guardedGeneration != cardGeneration) {
                    diagnostics.record("Test", "自检卡片已被新卡片取代，跳过自动移除")
                    return@launch
                }
                overlay.hideCard()
                diagnostics.record("Test", "自检卡片已移除")
            }
        }
    }

    private fun probeCardVisibility(spec: CardSpec, generation: Int, step: Int) {
        if (generation != cardGeneration) return
        val delayMs = CARD_PROBE_DELAYS_MS.getOrNull(step) ?: return
        scope.launch {
            if (delayMs > 0L) delay(delayMs)
            if (generation != cardGeneration) return@launch
            if (!overlay.isCardPresent()) return@launch // 用户已经答过了，或被新会话收走
            if (overlay.isCardRenderable()) {
                if (step > 0) {
                    diagnostics.record(
                        "Card",
                        "第 ${step + 1} 次探测（累计 ${CARD_PROBE_DELAYS_MS.take(step + 1).sum()}ms）才确认可见",
                    )
                }
                diagnostics.record("Card", "可见性确认 ${overlay.describeCardState()}")
                bump { it.copy(overlayShown = it.overlayShown + 1) }
                return@launch
            }
            if (step == CARD_PROBE_DELAYS_MS.lastIndex) {
                val detail = overlay.describeCardState()
                diagnostics.record(
                    "Card",
                    "探测 ${CARD_PROBE_DELAYS_MS.size} 次仍不可见 → 降级为横幅通知；$detail",
                )
                Notifications.postReminder(
                    this@MonitorService,
                    spec,
                    "卡片已加入窗口但始终不可见（$detail）",
                )
                bump { it.copy(overlayFailures = it.overlayFailures + 1) }
                policy.onCardUnavailable()
            } else {
                probeCardVisibility(spec, generation, step + 1)
            }
        }
    }

    /** 应用内的"测试提醒卡片"按钮：在前台走一次完整链路，结果写进诊断记录。 */
    private fun runCardSelfTest() {
        val settings = Graph.settings.settings.value
        val channel = Notifications.alertChannelStatus(this)
        diagnostics.record(
            "Test",
            "开始自检 悬浮窗权限=${Permissions.canDrawOverlays(this)} " +
                "通知=${channel.notificationsEnabled} 提醒通道=${channel.importanceText()}",
        )
        scope.launch {
            presentCard(
                spec = CardSpec.Nudge(
                    appLabel = getString(R.string.app_name),
                    activeMs = 5 * 60_000L,
                    nth = 1,
                    snoozeMinutes = (settings.snoozeSec / 60).coerceAtLeast(1),
                ),
                atBottom = settings.cardAtBottom,
                autoHideAfterMs = 8_000L,
            )
        }
    }

    /** 可能在主线程（onStartCommand / 卡片点击）被调用，所以内部把界面动作丢进协程。 */
    private fun handleAnswer(answer: NudgeAnswer) {
        val now = System.currentTimeMillis()
        val settings = Graph.settings.settings.value
        exitTarget = policy.currentPackage()
        val actions = policy.onAnswer(answer, now, settings)
        Notifications.cancelReminder(this)
        diagnostics.record("Answer", answer.name)

        when (answer) {
            NudgeAnswer.DONE_EXIT, NudgeAnswer.DISTRACTED ->
                bump { it.copy(returned = it.returned + 1) }

            NudgeAnswer.SNOOZE -> bump { it.copy(snoozed = it.snoozed + 1) }

            NudgeAnswer.WORKING -> bump { it.copy(grace = it.grace + 1) }

            NudgeAnswer.GAME_ENTER -> bump { it.copy(gameConfirm = it.gameConfirm + 1) }

            // 用户点了「算了，退出」。这既是一次"游戏前放弃"，也是一次实实在在的"被拦回来"，
            // 所以要同时计进 returned —— 首页那张「拦回」卡统计的就是这个。
            NudgeAnswer.GAME_CANCEL -> bump {
                it.copy(returned = it.returned + 1, gameDeclined = it.gameDeclined + 1)
            }

            NudgeAnswer.MUTE_APP -> {
                bump { it.copy(mutedApps = it.mutedApps + 1) }
                val muted = exitTarget
                if (muted != null) {
                    diagnostics.record("Answer", "不再提醒 $muted，已加入忽略名单")
                    Graph.scope.launch {
                        Graph.settings.setCategory(muted, AppCategory.IGNORED)
                    }
                }
            }

            NudgeAnswer.BREAK_BACK ->
                Graph.scope.launch { Graph.settings.edit { it.copy(breakEndsAtMs = 0L) } }

            NudgeAnswer.BREAK_EXTEND ->
                Graph.scope.launch {
                    Graph.settings.edit {
                        it.copy(breakEndsAtMs = now + it.breakExtendMin * 60_000L)
                    }
                    Graph.stats.bump { day -> day.copy(breakTaken = day.breakTaken + 1) }
                }
        }

        scope.launch { for (action in actions) execute(action, settings) }
    }

    /**
     * "退出"两个按钮的实现。
     *
     * 本应用不需要无障碍权限就能把用户送回桌面：持有 SYSTEM_ALERT_WINDOW 的应用
     * 属于"可以从后台启动 Activity"的豁免之一（见 Android 官方 "Activity security /
     * background activity launch restrictions"）。但豁免清单随版本变动，且部分 ROM 会加码，
     * 所以这里做一次实测：发起后 1.5 秒回查前台是不是还停在原应用，
     * 成功/失败都记进本地统计与诊断，失败则给一条可点的通知兜底。
     */
    private fun goHomeSafely() {
        val target = exitTarget
        scope.launch {
            bump { it.copy(exitAttempts = it.exitAttempts + 1) }
            val started = runCatching {
                startActivity(
                    Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.isSuccess
            delay(HOME_VERIFY_DELAY_MS)
            val left = tracker.poll(System.currentTimeMillis()) != target
            if (started && left) {
                bump { it.copy(exitSuccess = it.exitSuccess + 1) }
                diagnostics.record("Exit", "回到桌面成功")
                tryKillTargetBackground(target)
            } else {
                diagnostics.record("Exit", "回到桌面未生效 started=$started left=$left target=$target")
                Log.w(TAG, "回到桌面未生效 started=$started left=$left target=$target")
                if (Permissions.canPostNotifications(this@MonitorService)) {
                    Notifications.postHomeFallback(this@MonitorService)
                }
            }
        }
    }

    /**
     * 可选：顺手清掉被退出应用的后台进程。
     *
     * ⚠️ **Android 14 起这条路的官方说法是"第三方应用只能结束自己的进程"。**
     * 文档（ActivityManager.killBackgroundProcesses）原文：
     *   "On devices that run Android 14 or higher, third party applications can only use this
     *    API to kill their own processes."
     * 传别的应用的包名会被系统忽略，只在 logcat 里留一句 "Invalid packageName: ..."。
     * 能杀别的应用的是 KILL_ALL_BACKGROUND_PROCESSES，保护级别 privileged|signature，只有系统应用有。
     *
     * 所以这里按系统版本分流：Android 13 及以下真的调用（会生效），
     * 14+ 直接跳过并记一条诊断——**不假装成功**。界面上的开关也会标出"本机不支持"。
     */
    private fun tryKillTargetBackground(target: String?) {
        if (target.isNullOrBlank()) return
        if (!Graph.settings.settings.value.killTargetOnExit) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            diagnostics.record(
                "Exit",
                "已跳过清后台：Android 14+ 禁止第三方应用结束其他应用的进程（$target）",
            )
            return
        }

        val killed = runCatching {
            getSystemService(ActivityManager::class.java)?.killBackgroundProcesses(target)
        }.isSuccess
        diagnostics.record("Exit", "尝试清 $target 的后台：$killed")
    }

    // ------------------------------------------------------------------ 杂项

    private fun bump(block: (DayStats) -> DayStats) {
        Graph.scope.launch { Graph.stats.bump(block) }
    }

    private fun labelOf(packageName: String?): String {
        if (packageName.isNullOrBlank()) return ""
        labelCache[packageName]?.let { return it }
        val label = runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
        labelCache[packageName] = label
        return label
    }

    private fun buildNotification(): android.app.Notification {
        val settings = Graph.settings.settings.value
        val now = System.currentTimeMillis()
        val paused = settings.isPausedNow(now)
        val resting = settings.isResting(now)
        val text = when {
            resting -> getString(R.string.notif_resting_text, formatClock(settings.breakEndsAtMs))
            paused -> getString(R.string.home_paused_until, formatClock(settings.pausedUntilMs))
            settings.schedule.contains(minuteOfDayNow(now)) ->
                getString(R.string.notif_running_text, hhmm(settings.schedule.startMinute))
            else -> getString(R.string.notif_running_text_idle)
        }
        return Notifications.buildMonitorNotification(
            context = this,
            text = text,
            paused = paused,
            resting = resting,
            breakMinutes = settings.breakDurationMin,
        )
    }

    private fun refreshNotification(force: Boolean = false) {
        val settings = Graph.settings.settings.value
        val now = System.currentTimeMillis()
        val key = buildString {
            append(settings.masterEnabled)
            append('|')
            append(settings.isPausedNow(now))
            append('|')
            append(settings.schedule.enabled)
            append('|')
            append(settings.schedule.startMinute)
            append('|')
            append(settings.schedule.contains(minuteOfDayNow(now)))
            // 必须带上"是否在休息"：否则点通知栏的「休息 N 分钟」之后，
            // 常驻通知的文案和按钮不会变（key 没变就直接 return 了），
            // 用户看到的是一个按了没反应的按钮。
            append('|')
            append(settings.isResting(now))
        }
        if (!force && key == lastNotifKey) return
        lastNotifKey = key
        runCatching {
            getSystemService(NotificationManager::class.java)
                ?.notify(Notifications.ID_MONITOR, buildNotification())
        }
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    companion object {
        private const val TAG = "MonitorService"

        const val ACTION_START = "app.zhizhi.action.START"
        const val ACTION_STOP = "app.zhizhi.action.STOP"
        const val ACTION_PAUSE_30 = "app.zhizhi.action.PAUSE_30"
        const val ACTION_RESUME = "app.zhizhi.action.RESUME"
        const val ACTION_ANSWER = "app.zhizhi.action.ANSWER"
        const val ACTION_TEST_CARD = "app.zhizhi.action.TEST_CARD"
        const val ACTION_BREAK = "app.zhizhi.action.BREAK"
        const val ACTION_BREAK_END = "app.zhizhi.action.BREAK_END"
        const val EXTRA_ANSWER = "app.zhizhi.extra.ANSWER"

        /**
         * 学习时段内的轮询间隔。固定 1 秒：
         * UsageStatsManager 的事件本身有 0.5–2 秒延迟，再把轮询拖到 2.5 秒，游戏启动
         * 就可能要 4 秒以上才被识别，玩家已经进局了。1 秒能把总延迟压到 ~2 秒。
         * 代价只在用户自己设定的学习时段内产生，时段外是 20 秒。
         */
        private const val IDLE_TICK_MS = 1_000L

        /** 不在学习时段 / 屏幕关闭时的轮询间隔 */
        private const val SLEEP_TICK_MS = 20_000L

        /** 屏幕状态（是否点亮、是否解锁）与系统重新对齐的间隔 */
        private const val SCREEN_SYNC_INTERVAL_MS = 5_000L

        private const val FLUSH_THRESHOLD_MS = 60_000L

        /** 会话结束时不足这个量的零头不写盘。 */
        private const val MIN_FLUSH_MS = 1_000L
        private const val HOME_VERIFY_DELAY_MS = 1_500L
        const val PAUSE_STEP_MS = 30 * 60_000L

        /**
         * 卡片可见性的阶梯式探测点（毫秒，相对 addView 之后）。
         * 第 0 个是 0：窗口如果本来就画好了就立刻确认。后面几个用来容忍慢布局。
         */
        private val CARD_PROBE_DELAYS_MS = longArrayOf(0L, 350L, 800L, 1_600L, 2_600L)

        /** 给 UI 观察服务是否在跑。 */
        val running = MutableStateFlow(false)

        /** 给诊断页看锚点窗口到底建没建起来。 */
        val anchorVisible = MutableStateFlow(false)

        /**
         * 主循环心跳。每转一圈自增一次，**不经过 DataStore**。
         * 诊断页上只要它在涨，就证明这个安装的服务循环真的在跑。
         */
        val heartbeat = MutableStateFlow(0L)

        /** 主循环当前的完整状态描述。同样是内存值。 */
        val loopState = MutableStateFlow("（服务未运行）")

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MonitorService::class.java).setAction(ACTION_START),
            )
        }

        /**
         * 由界面主动停止。这里记一条诊断再停：否则日志里只会看到一个孤零零的 onDestroy，
         * 分不清是用户点了开关、系统杀的、还是被划掉了。
         */
        fun stop(context: Context) {
            Graph.ensure(context)
            Graph.diagnostics.record("Service", "界面请求停止监测")
            context.stopService(Intent(context, MonitorService::class.java))
        }

        /** 应用内自检：让服务立刻在前台弹一张样例卡片，并把可见性实测结果写进诊断。 */
        fun testCard(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MonitorService::class.java).setAction(ACTION_TEST_CARD),
            )
        }
    }
}
