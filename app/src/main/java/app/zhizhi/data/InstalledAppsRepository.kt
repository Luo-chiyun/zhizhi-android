package app.zhizhi.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(
    val packageName: String,
    val label: String,
)

/**
 * 用 <queries> 里声明的 MAIN/LAUNCHER intent 枚举"所有带启动图标的应用"。
 * 刻意不使用 QUERY_ALL_PACKAGES —— 那属于商店敏感权限，而这里也确实不需要它。
 */
class InstalledAppsRepository(private val context: Context) {

    suspend fun load(): List<AppEntry> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolved = pm.queryIntentActivities(intent, 0)
        resolved.asSequence()
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { info ->
                AppEntry(
                    packageName = info.packageName,
                    label = runCatching { pm.getApplicationLabel(info).toString() }
                        .getOrDefault(info.packageName),
                )
            }
            .sortedWith(compareBy({ it.label }, { it.packageName }))
            .toList()
    }

    fun hasLaunchIntent(packageName: String): Boolean =
        context.packageManager.getLaunchIntentForPackage(packageName) != null

    @Suppress("unused")
    private fun pm(): PackageManager = context.packageManager
}
