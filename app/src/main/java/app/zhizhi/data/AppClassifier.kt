package app.zhizhi.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 判定一个包名属于哪一类。优先级：
 *   用户显式设置 > 预设表（包名 / 应用名）> 系统忽略名单 > 不监控
 *
 * 注意：**默认是不监控**。本应用不会"因为你装了抖音就自动开始计时"，
 * 必须是用户在分类页勾选过的应用才进入范围。
 */
class AppClassifier(
    private val context: Context,
    private val settings: SettingsRepository,
) {
    @Volatile
    private var overrides: Map<String, AppCategory> = emptyMap()

    /** 永远不会被计时的包：自己、系统 UI、各类桌面。 */
    private val systemIgnored: Set<String> = setOf(
        context.packageName,
        "com.android.systemui",
        "com.android.settings",
        "android",
    )

    /**
     * 桌面应用的包名集合。
     *
     * 必须缓存：categoryOf() 在主循环里每个 tick 都会被调用，而一次
     * queryIntentActivities 就是一次 PackageManager 的跨进程调用。每一两秒打一次 IPC，
     * 既浪费电，也会让轮询周期被拉长——表现就是"提醒时灵时不灵"。
     * 用户中途换桌面需要在应用重启后才生效，这个代价可以接受。
     */
    private val launcherPackages: Set<String> by lazy {
        runCatching {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            @Suppress("DEPRECATION")
            context.packageManager
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        }.getOrDefault(emptySet())
    }

    fun start(scope: CoroutineScope) {
        scope.launch { settings.overrides.collect { overrides = it } }
    }

    fun categoryOf(packageName: String?): AppCategory {
        if (packageName.isNullOrBlank()) return AppCategory.IGNORED
        if (packageName in systemIgnored) return AppCategory.IGNORED
        if (isLauncher(packageName)) return AppCategory.IGNORED
        if (isOurDebugVariant(packageName)) return AppCategory.IGNORED

        overrides[packageName]?.let { return it }
        PresetCatalog.byPackageName(packageName)?.let { return it.category }
        return AppCategory.IGNORED
    }

    /** 分类页要展示"这个应用现在是什么状态" */
    fun resolvedWithLabelOverride(pkg: String, label: String): AppCategory {
        overrides[pkg]?.let { return it }
        PresetCatalog.byPackageName(pkg)?.let { return it.category }
        PresetCatalog.byAppLabel(label)?.let { return it.category }
        return AppCategory.IGNORED
    }

    /**
     * 真正会被计时/提醒的应用数。
     * 不能直接用 overrides.size —— 预设里"不用监测"的应用也在 overrides 里，但它们不监控。
     */
    fun monitoredCount(): Int = overrides.values.count { it.isMonitored }

    private fun isLauncher(packageName: String): Boolean = packageName in launcherPackages

    /** 调试包（app.zhizhi.debug）不应被自己监控。 */
    private fun isOurDebugVariant(packageName: String): Boolean =
        packageName.startsWith("app.zhizhi")
}
