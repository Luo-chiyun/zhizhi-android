package app.zhizhi.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.zhizhi.R
import app.zhizhi.data.DiagnosticsStore
import app.zhizhi.policy.NudgeAnswer
import app.zhizhi.util.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 卡片要显示什么。文案在这里组装，位置与外观由 OverlayController 统一处理。 */
sealed interface CardSpec {
    data class Nudge(
        val appLabel: String,
        val activeMs: Long,
        val nth: Int,
        val snoozeMinutes: Int,
    ) : CardSpec

    data class GameConfirm(val appLabel: String) : CardSpec

    data class BreakOver(val extendMinutes: Int) : CardSpec
}

/** showCard 的结果。调用方靠它决定要不要降级到通知。 */
enum class CardOutcome {
    /** addView 成功。**注意：这不等于窗口真的可见**，还需要 isCardRenderable() 实测。 */
    ADDED,

    /** 没有悬浮窗权限 */
    NO_PERMISSION,

    /** 拿不到 WindowManager（理论上不该发生） */
    NO_WINDOW_MANAGER,

    /** addView 抛异常 */
    FAILED,
}

/**
 * 悬浮窗管理。
 *
 * ## 线程纪律（这一条踩过坑，不要破）
 *
 * **所有 View / WindowManager 操作必须在主线程完成。**
 * `WindowManager.addView()` 内部会构造 `ViewRootImpl`，而它会创建一个绑定**当前线程** Looper 的
 * `Handler`。在 `Dispatchers.Default` 这类没有 `Looper.prepare()` 的工作线程上调它，会直接抛：
 *
 * ```
 * RuntimeException: Can't create handler inside thread Thread[DefaultDispatcher-worker-N,5,main]
 *                   that has not called Looper.prepare()
 * ```
 *
 * 这个异常曾经让"从后台弹卡片"100% 失败，同时"在应用内点测试按钮"100% 成功
 * （因为 Service 生命周期回调本来就在主线程），从而长时间被误判成"厂商 ROM 拦截了悬浮窗"。
 *
 * 现在的约定：
 *  - 公开 API 一律是 **suspend**，内部用 `withContext(Dispatchers.Main.immediate)` 切到主线程，
 *    调用方从哪里调都安全。
 *  - 少数必须在主线程**同步**完成的地方（`startForeground` 之前要先挂好锚点窗口）走
 *    `*OnMainThread()` 系列，它们是 internal 的，并且会做一次运行时断言。
 *
 * ## 窗口
 *
 * 1. 锚点窗口：1×1、几乎全透明、不可触摸、不可聚焦。存在的唯一理由是 Android 15 的规则——
 *    目标 SDK 35 的应用只有"已经持有一个可见的 TYPE_APPLICATION_OVERLAY 窗口"，
 *    才被允许从后台启动前台服务。
 *
 * 2. 卡片窗口：真正给用户看的那张。刻意用 FLAG_NOT_FOCUSABLE —— 底下的应用保持输入焦点。
 *
 * ## 另一条硬规矩
 *
 * **卡片不允许靠动画来获得可见性。** alpha 直接就是 1；动画只作为已经可见之后的修饰，
 * 去掉它也不影响功能。把可见性挂在动画上，在压帧率的机器上会得到一张永远 alpha=0 的卡片。
 */
class OverlayController(
    private val context: Context,
    private val diagnostics: DiagnosticsStore,
) {

    /** 用户按下某个按钮时回调。卡片会先自己消失，再通知外部。 */
    var onAnswer: ((NudgeAnswer) -> Unit)? = null

    private val windowManager: WindowManager? =
        context.getSystemService(WindowManager::class.java)

    private val main = Dispatchers.Main.immediate

    private var anchorView: View? = null
    private var cardView: View? = null

    /** 卡片上一次失败的原因摘要，用于写进降级通知的副标题，让用户在通知里就能看到原因。 */
    var lastCardFailureDetail: String? = null
        private set

    // ------------------------------------------------------------ 公开的安全 API

    suspend fun showCard(spec: CardSpec, atBottom: Boolean = true): CardOutcome =
        withContext(main) { addCardNow(spec, atBottom) }

    suspend fun hideCard() = withContext(main) { removeCardNow() }

    suspend fun showAnchor(): Boolean = withContext(main) { addAnchorNow() }

    suspend fun hideAnchor() = withContext(main) { removeAnchorNow() }

    suspend fun isCardPresent(): Boolean = withContext(main) { cardView != null }

    /**
     * 实测卡片是否真的能被用户看到。
     *
     * addView() 返回、甚至不抛异常，都不代表窗口可见：厂商 ROM 可以把它加进去之后立刻隐藏，
     * 也可以让它以 0 尺寸布局。所以必须回查 attach 状态和实际尺寸，而不是相信 addView。
     */
    suspend fun isCardRenderable(): Boolean = withContext(main) { isCardRenderableNow() }

    /** 供诊断页展示的一行状态描述。 */
    suspend fun describeCardState(): String = withContext(main) { describeCardStateNow() }

    suspend fun isAnchorVisible(): Boolean = withContext(main) { isAnchorVisibleNow() }

    // --------------------------------------------------- 主线程同步版本（internal）

    /**
     * 给 `startForeground` 用。Android 15 起必须**先**有可见的锚点窗口才能从后台启动前台服务，
     * 顺序不能交给协程调度，所以这里要求调用方已经在主线程上同步执行。
     * Service 的 `onStartCommand` 本来就在主线程。
     */
    internal fun addAnchorOnMainThread(): Boolean {
        assertMainThread("addAnchorOnMainThread")
        return addAnchorNow()
    }

    internal fun isAnchorVisibleOnMainThread(): Boolean {
        assertMainThread("isAnchorVisibleOnMainThread")
        return isAnchorVisibleNow()
    }

    /** 给 `onDestroy` 用。onDestroy 也在主线程。 */
    internal fun removeAllOnMainThread() {
        assertMainThread("removeAllOnMainThread")
        removeCardNow()
        removeAnchorNow()
    }

    /**
     * 运行时护栏：如果哪天有人又把 View 操作搬到工作线程上，至少让它出现在诊断里，
     * 而不是变成一个看不出原因的失败。
     */
    private fun assertMainThread(where: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            diagnostics.record(
                TAG,
                "内部错误：$where 不在主线程（${Thread.currentThread().name}），View 操作需要主线程",
            )
        }
    }

    // ------------------------------------------------------------------ 锚点实现

    private fun addAnchorNow(): Boolean {
        if (anchorView != null) return true
        val wm = windowManager ?: run {
            diagnostics.record(TAG, "锚点：拿不到 WindowManager")
            return false
        }
        if (!Settings.canDrawOverlays(context)) {
            diagnostics.record(TAG, "锚点：没有悬浮窗权限")
            return false
        }

        val view = View(context).apply {
            // 用 alpha=1/255 而不是全透明：窗口需要被系统认定为"可见"。
            setBackgroundColor(Color.argb(1, 0, 0, 0))
        }
        val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            // 让 `dumpsys window windows | grep FocusGuard` 能认出这个窗口。
            setTitle(WINDOW_TITLE_ANCHOR)
        }

        return runCatching {
            wm.addView(view, params)
            anchorView = view
            diagnostics.record(
                TAG,
                "锚点窗口已添加 type=APPLICATION_OVERLAY 1x1 attached=${view.isAttachedToWindow}",
            )
            true
        }.getOrElse { error ->
            diagnostics.record(
                TAG,
                "锚点窗口添加失败 ${error.javaClass.simpleName}: ${error.message}",
            )
            Log.e(TAG, "锚点悬浮窗创建失败", error)
            false
        }
    }

    private fun removeAnchorNow() {
        val view = anchorView ?: return
        anchorView = null
        runCatching { windowManager?.removeView(view) }
    }

    private fun isAnchorVisibleNow(): Boolean =
        anchorView?.let { it.isAttachedToWindow && it.windowVisibility == View.VISIBLE } == true

    // ------------------------------------------------------------------ 卡片实现

    private fun addCardNow(spec: CardSpec, atBottom: Boolean): CardOutcome {
        removeCardNow()

        val wm = windowManager
        if (wm == null) {
            lastCardFailureDetail = "WindowManager 为 null"
            diagnostics.record(TAG, "卡片：拿不到 WindowManager")
            return CardOutcome.NO_WINDOW_MANAGER
        }
        if (!Settings.canDrawOverlays(context)) {
            lastCardFailureDetail = "canDrawOverlays=false"
            diagnostics.record(TAG, "卡片：没有悬浮窗权限（canDrawOverlays=false）")
            return CardOutcome.NO_PERMISSION
        }

        // WindowManager.LayoutParams 直接继承 ViewGroup.LayoutParams（不是 MarginLayoutParams），
        // 没有 setMargins()。悬浮窗的内边距只能靠外层容器自己加。
        val container = FrameLayout(context).apply {
            val pad = dp(12)
            setPadding(pad, pad, pad, pad)
            isFocusable = false
            isFocusableInTouchMode = false
        }
        container.addView(
            buildCard(spec),
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // 注意：没有 FLAG_NOT_TOUCHABLE —— 按钮要能点。
            // 但有 FLAG_NOT_FOCUSABLE —— 底下的应用保持输入焦点。
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = if (atBottom) {
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            } else {
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }
            // 顶部卡片要避开状态栏与刘海。
            if (!atBottom) y = dp(44)
            // 兜底：不依赖任何动画，窗口本身就不透明。
            alpha = 1f
            setTitle(WINDOW_TITLE_CARD)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        // 可见性不交给动画：先设成完全不透明，动画只是可见之后的修饰。
        container.alpha = 1f

        return runCatching {
            wm.addView(container, params)
            cardView = container
            lastCardFailureDetail = null
            diagnostics.record(
                TAG,
                "卡片已添加 spec=${spec.javaClass.simpleName} atBottom=$atBottom " +
                    "type=APPLICATION_OVERLAY flags=NOT_FOCUSABLE|NOT_TOUCH_MODAL " +
                    "thread=${Thread.currentThread().name}",
            )
            // 动画仅作修饰，失败也不影响可见性。
            runCatching {
                container.translationY = if (atBottom) dp(18).toFloat() else -dp(18).toFloat()
                container.animate().translationY(0f).setDuration(160L).start()
            }
            CardOutcome.ADDED
        }.getOrElse { error ->
            cardView = null
            lastCardFailureDetail = "${error.javaClass.simpleName}: ${error.message}"
            diagnostics.record(
                TAG,
                "卡片添加失败 ${error.javaClass.simpleName}: ${error.message}",
            )
            Log.e(TAG, "提醒卡片创建失败", error)
            CardOutcome.FAILED
        }
    }

    private fun removeCardNow() {
        val view = cardView ?: return
        cardView = null
        runCatching { windowManager?.removeView(view) }
    }

    private fun isCardRenderableNow(): Boolean {
        val view = cardView ?: return false
        return view.isAttachedToWindow &&
            view.windowVisibility == View.VISIBLE &&
            view.width > 0 &&
            view.height > 0
    }

    private fun describeCardStateNow(): String {
        val view = cardView ?: return "卡片未创建"
        return "attached=${view.isAttachedToWindow} visibility=${view.windowVisibility} " +
            "size=${view.width}x${view.height} alpha=${view.alpha}"
    }

    // ------------------------------------------------------------------ 构建

    private fun buildCard(spec: CardSpec): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(CARD_BG)
                setStroke(dp(1), CARD_BORDER)
            }
            isFocusable = false
            isFocusableInTouchMode = false
        }

        when (spec) {
            is CardSpec.Nudge -> {
                root.addView(textView(context.getString(R.string.nudge_title), 17f, TITLE, bold = true))
                root.addView(
                    textView(
                        if (spec.nth > 1) {
                            context.getString(
                                R.string.nudge_subtitle_repeat,
                                spec.appLabel,
                                spec.nth,
                            )
                        } else {
                            context.getString(
                                R.string.nudge_subtitle,
                                spec.appLabel,
                                formatDuration(spec.activeMs),
                            )
                        },
                        13f,
                        SUBTITLE,
                    ),
                    topMargin(dp(4)),
                )

                // 卡片高度是真机上学到的教训：五个按钮竖着排，窗口会占掉屏幕高度的 45%，
                // 对一个号称"不打扰"的工具来说太霸道了。改成"主按钮通栏 + 2×2 网格"，
                // 高度大约减半，同时五个动作一个都不少。
                root.addView(
                    actionButton(context.getString(R.string.nudge_btn_done), primary = true) {
                        answer(NudgeAnswer.DONE_EXIT)
                    },
                    topMargin(dp(12)),
                )

                val row1 = buttonRow()
                addToRow(
                    row1,
                    actionButton(context.getString(R.string.nudge_btn_distracted), primary = false) {
                        answer(NudgeAnswer.DISTRACTED)
                    },
                    first = true,
                )
                addToRow(
                    row1,
                    actionButton(
                        context.getString(R.string.nudge_btn_snooze_short, spec.snoozeMinutes),
                        primary = false,
                    ) { answer(NudgeAnswer.SNOOZE) },
                    first = false,
                )
                root.addView(row1, topMargin(dp(8)))

                val row2 = buttonRow()
                addToRow(
                    row2,
                    actionButton(context.getString(R.string.nudge_btn_working_short), primary = false) {
                        answer(NudgeAnswer.WORKING)
                    },
                    first = true,
                )
                // 第五个动作刻意做得更轻（无底色、字更小）：
                // 它是"退出这个应用的监控"，不是"这次算了"，不该和前面几个抢注意力。
                addToRow(
                    row2,
                    quietButton(context.getString(R.string.nudge_btn_mute_app)) {
                        answer(NudgeAnswer.MUTE_APP)
                    },
                    first = false,
                )
                root.addView(row2, topMargin(dp(8)))
            }

            is CardSpec.GameConfirm -> {
                root.addView(textView(context.getString(R.string.gameconfirm_title), 17f, TITLE, bold = true))
                root.addView(
                    textView(
                        context.getString(R.string.gameconfirm_subtitle, spec.appLabel),
                        13f,
                        SUBTITLE,
                    ),
                    topMargin(dp(4)),
                )
                // "算了" 是主按钮：默认把用户往退出方向推，但不替他做决定。
                val row = buttonRow()
                addToRow(
                    row,
                    actionButton(context.getString(R.string.gameconfirm_btn_cancel), primary = true) {
                        answer(NudgeAnswer.GAME_CANCEL)
                    },
                    first = true,
                )
                addToRow(
                    row,
                    actionButton(context.getString(R.string.gameconfirm_btn_enter), primary = false) {
                        answer(NudgeAnswer.GAME_ENTER)
                    },
                    first = false,
                )
                root.addView(row, topMargin(dp(12)))
            }

            is CardSpec.BreakOver -> {
                root.addView(textView(context.getString(R.string.breakover_title), 17f, TITLE, bold = true))
                root.addView(
                    textView(context.getString(R.string.breakover_subtitle), 13f, SUBTITLE),
                    topMargin(dp(4)),
                )
                val row = buttonRow()
                addToRow(
                    row,
                    actionButton(context.getString(R.string.breakover_btn_back), primary = true) {
                        answer(NudgeAnswer.BREAK_BACK)
                    },
                    first = true,
                )
                addToRow(
                    row,
                    actionButton(
                        context.getString(R.string.breakover_btn_extend, spec.extendMinutes),
                        primary = false,
                    ) { answer(NudgeAnswer.BREAK_EXTEND) },
                    first = false,
                )
                root.addView(row, topMargin(dp(12)))
            }
        }
        return root
    }

    private fun buttonRow(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        isFocusable = false
        isFocusableInTouchMode = false
    }

    private fun addToRow(row: LinearLayout, view: TextView, first: Boolean) {
        val params = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f,
        )
        if (!first) params.marginStart = dp(8)
        row.addView(view, params)
    }

    private fun answer(value: NudgeAnswer) {
        removeCardNow()
        onAnswer?.invoke(value)
    }

    private fun textView(
        value: String,
        sizeSp: Float,
        color: Int,
        bold: Boolean = false,
    ): TextView = TextView(context).apply {
        text = value
        textSize = sizeSp
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        isFocusable = false
    }

    private fun actionButton(label: String, primary: Boolean, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(if (primary) Color.WHITE else SECONDARY_FG)
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(if (primary) PRIMARY else SECONDARY_BG)
            }
            isClickable = true
            isFocusable = false
            isFocusableInTouchMode = false
            minHeight = dp(46)
            setPadding(dp(14), dp(13), dp(14), dp(13))
            setOnClickListener { onClick() }
        }

    private fun quietButton(label: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            textSize = 13.5f
            gravity = Gravity.CENTER
            setTextColor(SUBTITLE)
            isClickable = true
            isFocusable = false
            isFocusableInTouchMode = false
            minHeight = dp(40)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setOnClickListener { onClick() }
        }

    private fun topMargin(margin: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = margin }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "Overlay"

        const val WINDOW_TITLE_ANCHOR = "知止 锚点"
        const val WINDOW_TITLE_CARD = "知止 卡片"

        // 与 UI 模板同一套令牌：白底 + slate-200 描边 + indigo-600 主按钮
        private val CARD_BG = Color.parseColor("#FFFFFF")
        private val CARD_BORDER = Color.parseColor("#E2E8F0")
        private val TITLE = Color.parseColor("#0F172A")
        private val SUBTITLE = Color.parseColor("#64748B")
        private val PRIMARY = Color.parseColor("#4F46E5")
        private val SECONDARY_BG = Color.parseColor("#F1F5F9")
        private val SECONDARY_FG = Color.parseColor("#334155")
    }
}
