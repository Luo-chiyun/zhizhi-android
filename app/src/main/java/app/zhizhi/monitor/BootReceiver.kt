package app.zhizhi.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.zhizhi.Graph
import app.zhizhi.util.Permissions
import kotlinx.coroutines.launch

/**
 * 开机 / 应用更新后尝试恢复监测。
 *
 * 两个刻意的克制：
 *  1. 必须用户在设置里显式打开"开机自动恢复"，默认关闭。
 *  2. 权限不全时直接放弃，不弹任何东西——开机时弹窗是最讨人厌的行为。
 *     应用内的权限页会把"上次没能自动恢复"这件事如实写出来。
 *
 * 技术上可行：Android 15 限制的是 BOOT_COMPLETED 拉起 dataSync / camera /
 * mediaPlayback / phoneCall / mediaProjection / microphone 六种前台服务，
 * specialUse 不在其列。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val pending = goAsync()
        Graph.ensure(context)
        Graph.scope.launch {
            try {
                val settings = Graph.settings.readOnce()
                if (!settings.masterEnabled || !settings.autoStartOnBoot) return@launch
                if (!Permissions.canDrawOverlays(context)) {
                    Log.i(TAG, "跳过开机自启：缺少悬浮窗权限")
                    return@launch
                }
                if (!Permissions.hasUsageAccess(context)) {
                    Log.i(TAG, "跳过开机自启：缺少使用情况访问权限")
                    return@launch
                }
                MonitorService.start(context)
            } catch (error: Exception) {
                Log.w(TAG, "开机恢复监测失败", error)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
