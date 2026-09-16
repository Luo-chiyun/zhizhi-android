package app.zhizhi.notify

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.zhizhi.R
import app.zhizhi.monitor.MonitorService
import app.zhizhi.overlay.CardSpec
import app.zhizhi.policy.NudgeAnswer
import app.zhizhi.ui.MainActivity
import app.zhizhi.util.formatDuration

/**
 * 通知。三条通道，全部无声、无振动、无呼吸灯：
 *
 *  - focus_monitor  IMPORTANCE_MIN：前台服务的常驻通知。IMPORTANCE_MIN（"最小化"）会让它
 *    在通知栏里被折叠到最底部、不在状态栏显示图标——这是能做到的"最安静"的形态。
 *  - focus_alerts   IMPORTANCE_HIGH：**提醒卡片的降级通道**。当悬浮窗在这个 ROM 上弹不出来时，
 *    用一条横幅通知把同一组选项给用户。虽然是 HIGH，但通道关掉了声音和振动，
 *    所以只是视觉上的横幅，不会响。
 *  - focus_service_low  IMPORTANCE_LOW：缺权限、回到桌面被拦截之类的兜底提示。平时通知数恒为 0。
 *
 * 关于"不授予通知权限能不能更无感"：在 Android 13+ 上，POST_NOTIFICATIONS 被拒后
 * 通知确实不会出现在通知栏，但前台服务本身照常运行（服务生命周期不受该权限约束）。
 * 所以"服务继续跑"是真的，"完全不可见"做不到——系统的"正在运行 / 活跃应用"界面
 * 仍然会列出持有前台服务的应用。
 */
object Notifications {

    const val CHANNEL_MONITOR = "zhizhi_monitor"
    const val CHANNEL_ALERTS = "zhizhi_alerts"
    const val CHANNEL_LOW = "zhizhi_service_low"

    const val ID_MONITOR = 1001
    const val ID_REMINDER = 1010
    private const val ID_NEEDS_PERMISSION = 1002
    private const val ID_HOME_FALLBACK = 1003

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        if (nm.getNotificationChannel(CHANNEL_MONITOR) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_MONITOR,
                    context.getString(R.string.notif_channel_monitor),
                    NotificationManager.IMPORTANCE_MIN,
                ).apply {
                    description = context.getString(R.string.notif_channel_monitor_desc)
                    setShowBadge(false)
                    enableVibration(false)
                    enableLights(false)
                    setSound(null, null)
                    setLockscreenVisibility(Notification.VISIBILITY_SECRET)
                },
            )
        }

        if (nm.getNotificationChannel(CHANNEL_ALERTS) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ALERTS,
                    context.getString(R.string.notif_channel_alerts),
                    // HIGH 才会有横幅（heads-up）。通道本身无声无振动，所以只是"看得见"。
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.notif_channel_alerts_desc)
                    setShowBadge(false)
                    enableVibration(false)
                    enableLights(false)
                    setSound(null, null)
                    // 锁屏上也要能直接点按钮，否则降级通道就失去意义了。
                    setLockscreenVisibility(Notification.VISIBILITY_PUBLIC)
                },
            )
        }

        if (nm.getNotificationChannel(CHANNEL_LOW) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_LOW,
                    context.getString(R.string.notif_channel_low),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    setShowBadge(false)
                    enableVibration(false)
                    enableLights(false)
                    setSound(null, null)
                    setLockscreenVisibility(Notification.VISIBILITY_SECRET)
                },
            )
        }
    }

    fun buildMonitorNotification(
        context: Context,
        text: String,
        paused: Boolean,
        resting: Boolean,
        breakMinutes: Int,
    ): Notification {
        ensureChannels(context)
        val (serviceAction, actionLabel) = when {
            paused -> MonitorService.ACTION_RESUME to
                context.getString(R.string.notif_action_resume)

            resting -> MonitorService.ACTION_BREAK_END to
                context.getString(R.string.notif_action_break_end)

            else -> MonitorService.ACTION_BREAK to
                context.getString(R.string.notif_action_break, breakMinutes)
        }
        return NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(context.getString(R.string.notif_running_title))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(activityPendingIntent(context, 0))
            .addAction(0, actionLabel, servicePendingIntent(context, serviceAction, 1))
            .build()
    }

    /**
     * 提醒卡片的降级形态：一条带同样选项的横幅通知。
     *
     * 什么时候用它：
     *  - showCard() 直接被拒（没有权限 / 没有 WindowManager / addView 抛异常）
     *  - showCard() 返回成功，但多次探测后窗口始终没有真正可见/没有尺寸
     *
     * [reason] 会以副标题的形式出现在通知里。这一条很关键：在诊断本身不可用的设备上，
     * 通知是唯一**确定能到达用户**的界面，那就把原因写在通知上，而不是让它只存在于日志里。
     *
     * ⚠️ 一条硬规矩：**绝对不要在这条通知上调用 setSilent(true)。**
     * 官方文档写得很明确：setSilent(true) 会 "prevent the notification from peeking on screen,
     * regardless of the importance ... set on the notification or notification channel"。
     * 也就是说，哪怕通道是 IMPORTANCE_HIGH，加了 setSilent 就永远不会有横幅。
     * 想要"有横幅但不响"，正确做法是让**通道**无声音（setSound(null) + enableVibration(false)），
     * 而不是让通知静音。
     */
    fun postReminder(context: Context, spec: CardSpec, reason: String? = null) {
        ensureChannels(context)
        val (title, text) = reminderTexts(context, spec)
        val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(false)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            // 不调用 setSilent —— 见上面注释。音量由通道控制（通道无声）。
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(activityPendingIntent(context, 4))

        if (!reason.isNullOrBlank()) {
            builder.setSubText(context.getString(R.string.notif_fallback_reason, reason))
        }

        reminderAnswers(context, spec).forEachIndexed { index, (label, answer) ->
            builder.addAction(0, label, answerPendingIntent(context, answer, 20 + index))
        }

        runCatching {
            context.getSystemService(NotificationManager::class.java)
                ?.notify(ID_REMINDER, builder.build())
        }
    }

    /** 提醒通道的真实状态。用户或 ROM 把通道重要性降下去之后，横幅就没了——这一页要能看出来。 */
    data class AlertChannelStatus(
        val notificationsEnabled: Boolean,
        val importance: Int,
        val channelExists: Boolean,
    ) {
        fun importanceText(): String = when {
            !channelExists -> "通道未创建"
            importance >= NotificationManager.IMPORTANCE_HIGH -> "高（会弹横幅）"
            importance == NotificationManager.IMPORTANCE_DEFAULT -> "中（不弹横幅）"
            importance == NotificationManager.IMPORTANCE_LOW -> "低（不弹横幅）"
            importance == NotificationManager.IMPORTANCE_MIN -> "最低（不弹横幅、状态栏无图标）"
            else -> "已关闭"
        }

        /** 降级通知能不能真的弹出横幅。只要这一项是 false，提醒就只能是通知栏里的一条。 */
        val canPeek: Boolean
            get() = notificationsEnabled &&
                channelExists &&
                importance >= NotificationManager.IMPORTANCE_HIGH
    }

    fun alertChannelStatus(context: Context): AlertChannelStatus {
        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = nm?.getNotificationChannel(CHANNEL_ALERTS)
        return AlertChannelStatus(
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            importance = channel?.importance ?: NotificationManager.IMPORTANCE_NONE,
            channelExists = channel != null,
        )
    }

    fun cancelReminder(context: Context) {
        runCatching {
            context.getSystemService(NotificationManager::class.java)?.cancel(ID_REMINDER)
        }
    }

    private fun reminderTexts(context: Context, spec: CardSpec): Pair<String, String> = when (spec) {
        is CardSpec.Nudge -> context.getString(R.string.nudge_title) to
            context.getString(
                R.string.notif_fallback_nudge_text,
                spec.appLabel,
                formatDuration(spec.activeMs),
            )

        is CardSpec.GameConfirm -> context.getString(R.string.gameconfirm_title) to
            context.getString(R.string.gameconfirm_subtitle, spec.appLabel)

        is CardSpec.BreakOver -> context.getString(R.string.breakover_title) to
            context.getString(R.string.breakover_subtitle)
    }
    private fun reminderAnswers(
        context: Context,
        spec: CardSpec,
    ): List<Pair<String, NudgeAnswer>> = when (spec) {
        is CardSpec.Nudge -> listOf(
            // 通知折叠时只显示前三个，所以把最可能的三个放前面。
            context.getString(R.string.nudge_btn_done) to NudgeAnswer.DONE_EXIT,
            context.getString(R.string.nudge_btn_distracted) to NudgeAnswer.DISTRACTED,
            context.getString(R.string.nudge_btn_mute_app) to NudgeAnswer.MUTE_APP,
            context.getString(R.string.notif_action_snooze, spec.snoozeMinutes) to NudgeAnswer.SNOOZE,
            context.getString(R.string.notif_action_working) to NudgeAnswer.WORKING,
        )

        is CardSpec.GameConfirm -> listOf(
            context.getString(R.string.gameconfirm_btn_cancel) to NudgeAnswer.GAME_CANCEL,
            context.getString(R.string.gameconfirm_btn_enter) to NudgeAnswer.GAME_ENTER,
        )

        is CardSpec.BreakOver -> listOf(
            context.getString(R.string.breakover_btn_back) to NudgeAnswer.BREAK_BACK,
            context.getString(R.string.breakover_btn_extend, spec.extendMinutes) to
                NudgeAnswer.BREAK_EXTEND,
        )
    }

    /** 前台服务因缺权限起不来时，给一条能点回应用的提示。 */
    fun postNeedsPermission(context: Context) {
        ensureChannels(context)
        postSimple(
            context = context,
            id = ID_NEEDS_PERMISSION,
            title = context.getString(R.string.notif_need_permission_title),
            text = context.getString(R.string.notif_need_permission_text),
            contentIntent = activityPendingIntent(context, 2),
        )
    }

    /** "一键回到桌面"被拦截时的兜底入口。 */
    fun postHomeFallback(context: Context) {
        ensureChannels(context)
        postSimple(
            context = context,
            id = ID_HOME_FALLBACK,
            title = context.getString(R.string.notif_exit_fallback_title),
            text = context.getString(R.string.notif_exit_fallback_text),
            contentIntent = homePendingIntent(context),
        )
    }

    private fun postSimple(
        context: Context,
        id: Int,
        title: String,
        text: String,
        contentIntent: PendingIntent,
    ) {
        val notification = NotificationCompat.Builder(context, CHANNEL_LOW)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(contentIntent)
            .build()
        runCatching {
            context.getSystemService(NotificationManager::class.java)?.notify(id, notification)
        }
    }

    // ------------------------------------------------------------------ PI

    private fun answerPendingIntent(
        context: Context,
        answer: NudgeAnswer,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(context, MonitorService::class.java)
            .setAction(MonitorService.ACTION_ANSWER)
            .putExtra(MonitorService.EXTRA_ANSWER, answer.name)
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun activityPendingIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // targetSdk 35 起，创建 PendingIntent 的一方不再默认把自己的
            // "后台启动 Activity" 特权借给发送方，必须显式 opt-in。
            val options = ActivityOptions.makeBasic().apply {
                setPendingIntentCreatorBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                )
            }
            PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                options.toBundle(),
            )
        } else {
            PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }

    private fun servicePendingIntent(
        context: Context,
        action: String,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(context, MonitorService::class.java).setAction(action)
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun homePendingIntent(context: Context): PendingIntent {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            3,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
