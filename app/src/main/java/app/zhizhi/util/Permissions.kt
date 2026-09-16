package app.zhizhi.util

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import android.app.NotificationManager

/**
 * 四类权限的统一读写入口。
 *
 * 关于"后台弹出界面"：国产 ROM 上，悬浮窗权限被授予后，系统仍可能拦截悬浮窗的弹出。
 * 这个开关没有统一的 API 名称（小米叫"后台弹出界面"，华为叫"悬浮窗"，OPPO/VIVO 叫"允许后台弹出界面"），
 * 只能按厂商跳到对应设置页，用户手动打开。
 */
object Permissions {

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = runCatching {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                )
            } else {
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                )
            }
        }.getOrDefault(AppOpsManager.MODE_ERRORED)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun canPostNotifications(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    // ---------------- 跳转 ----------------

    fun usageAccessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun overlayIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )

    fun notificationIntent(context: Context): Intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun batteryIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun appDetailsIntent(context: Context): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    )

    /**
     * 厂商后台管理页。全部用包名+类名硬跳，跳不到就退回应用详情页——
     * 绝不能因为"跳转失败"就让用户卡在权限页出不去。
     */
    fun oemBackgroundIntent(context: Context): Intent {
        val candidates = when {
            Build.MANUFACTURER.equals("Xiaomi", true) -> listOf(
                "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
                "com.miui.securitycenter" to "com.miui.powercenter.PowerSettings",
            )
            Build.MANUFACTURER.equals("Huawei", true) || Build.MANUFACTURER.equals("HONOR", true) -> listOf(
                "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                "com.huawei.systemmanager" to "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity",
            )
            Build.MANUFACTURER.equals("OPPO", true) || Build.MANUFACTURER.equals("realme", true) -> listOf(
                "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
            )
            Build.MANUFACTURER.equals("vivo", true) || Build.MANUFACTURER.equals("iQOO", true) -> listOf(
                "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
            )
            Build.MANUFACTURER.equals("Meizu", true) -> listOf(
                "com.meizu.safe" to "com.meizu.safe.permission.SmartBGActivity",
            )
            Build.MANUFACTURER.equals("OnePlus", true) -> listOf(
                "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
            )
            else -> emptyList()
        }

        candidates.forEach { (pkg, cls) ->
            val intent = Intent().setClassName(pkg, cls)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                return intent
            }
        }
        return appDetailsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** 有没有真的"没授就完全不能用"的权限缺着。 */
    fun missingCritical(context: Context): Boolean =
        !hasUsageAccess(context) || !canDrawOverlays(context)

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        return nm.areNotificationsEnabled()
    }
}
