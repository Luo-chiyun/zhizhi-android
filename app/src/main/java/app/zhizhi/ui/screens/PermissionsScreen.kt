package app.zhizhi.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.zhizhi.R
import app.zhizhi.ui.components.HintBlock
import app.zhizhi.ui.components.StatusPill
import app.zhizhi.ui.components.ZhiZhiCard
import app.zhizhi.util.Permissions

private data class PermItem(
    val titleRes: Int,
    val whyRes: Int,
    val howToRes: Int,
    val optional: Boolean,
)

/**
 * 权限页。
 *
 * 引导上的三个改进（都来自真机上"不知道该干嘛"的反馈）：
 *  1. 顶部先给一句总结：还差几项必需权限，或者"已就绪"。
 *  2. 每张卡按顺序编号（第 1 步 / 第 2 步），并直接写明**在系统设置里怎么点**，
 *     而不是只说"为什么需要"。
 *  3. 主按钮直接指向**第一个还没授予的必需权限**，不用自己在四张卡里找。
 *  4. 从系统设置返回时自动刷新状态（LifecycleResumeEffect），不用手动点刷新。
 */
@Composable
fun PermissionsScreen(
    primaryLabel: String,
    onPrimary: () -> Unit,
) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }

    var usageGranted by remember { mutableStateOf(Permissions.hasUsageAccess(context)) }
    var overlayGranted by remember { mutableStateOf(Permissions.canDrawOverlays(context)) }
    var notifGranted by remember { mutableStateOf(Permissions.canPostNotifications(context)) }
    var batteryOk by remember { mutableStateOf(Permissions.isIgnoringBatteryOptimizations(context)) }

    fun refresh() {
        usageGranted = Permissions.hasUsageAccess(context)
        overlayGranted = Permissions.canDrawOverlays(context)
        notifGranted = Permissions.canPostNotifications(context)
        batteryOk = Permissions.isIgnoringBatteryOptimizations(context)
    }

    LaunchedEffect(refreshKey) { refresh() }

    // 从系统设置页返回时自动重查一次——否则用户授完权回来还是看到"未授予"，以为没生效。
    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose { }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notifGranted = granted
        refresh()
    }

    fun open(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    val required = listOf(
        PermItem(
            R.string.perm_usage_title,
            R.string.perm_usage_why,
            R.string.perm_howto_usage,
            optional = false,
        ),
        PermItem(
            R.string.perm_overlay_title,
            R.string.perm_overlay_why,
            R.string.perm_howto_overlay,
            optional = false,
        ),
        PermItem(
            R.string.perm_notif_title,
            R.string.perm_notif_why,
            R.string.perm_howto_notif,
            optional = true,
        ),
        PermItem(
            R.string.perm_battery_title,
            R.string.perm_battery_why,
            R.string.perm_howto_battery,
            optional = true,
        ),
    )

    val granted = listOf(usageGranted, overlayGranted, notifGranted, batteryOk)
    val missingRequired = listOf(0, 1).filter { !granted[it] }
    val requiredCount = 2
    val requiredGranted = requiredCount - missingRequired.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        // ---------------- 顶部总结 ----------------
        HintBlock(
            text = if (missingRequired.isEmpty()) {
                stringResource(R.string.perm_summary_ok)
            } else {
                stringResource(R.string.perm_summary_missing, missingRequired.size)
            },
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.perm_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ---------------- 四张权限卡 ----------------
        var requiredIndex = 0
        required.forEachIndexed { index, item ->
            val isGranted = granted[index]
            val stepLabel = if (item.optional) {
                null
            } else {
                requiredIndex += 1
                stringResource(R.string.perm_step_label, requiredIndex)
            }
            Spacer(Modifier.height(12.dp))
            PermissionCard(
                item = item,
                granted = isGranted,
                stepLabel = stepLabel,
                onGrant = {
                    when (index) {
                        0 -> open(Permissions.usageAccessIntent())
                        1 -> open(Permissions.overlayIntent(context))
                        2 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            open(Permissions.notificationIntent(context))
                        }

                        else -> open(Permissions.batteryIntent())
                    }
                },
            )
        }

        // ---------------- 厂商后台 ----------------
        Spacer(Modifier.height(12.dp))
        ZhiZhiCard {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.perm_oem_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.perm_oem_why, Build.MANUFACTURER),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { open(Permissions.oemBackgroundIntent(context)) }) {
                    Text(stringResource(R.string.perm_oem_button))
                }
            }
        }

        // ---------------- 主按钮：指向第一个缺的必需权限 ----------------
        Spacer(Modifier.height(22.dp))
        if (missingRequired.isNotEmpty()) {
            Button(
                onClick = {
                    when (missingRequired.first()) {
                        0 -> open(Permissions.usageAccessIntent())
                        else -> open(Permissions.overlayIntent(context))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        R.string.perm_next_missing,
                        stringResource(required[missingRequired.first()].titleRes),
                    ),
                )
            }
            Spacer(Modifier.height(8.dp))
        } else {
            Text(
                text = "必需权限（$requiredGranted/$requiredCount）已就绪。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }

        Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) {
            Text(primaryLabel)
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            onClick = { refresh() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("刷新状态") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PermissionCard(
    item: PermItem,
    granted: Boolean,
    stepLabel: String?,
    onGrant: () -> Unit,
) {
    ZhiZhiCard {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(item.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (stepLabel != null) {
                        StatusPill(text = stepLabel, ok = true)
                    }
                }
                StatusPill(
                    text = stringResource(
                        when {
                            granted -> R.string.permission_granted
                            item.optional -> R.string.perm_notif_optional
                            else -> R.string.permission_missing
                        },
                    ),
                    ok = granted,
                    warn = item.optional,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(item.whyRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!granted) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.perm_how_to, stringResource(item.howToRes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = onGrant) {
                    Text(stringResource(R.string.goto_settings))
                }
            }
        }
    }
}
