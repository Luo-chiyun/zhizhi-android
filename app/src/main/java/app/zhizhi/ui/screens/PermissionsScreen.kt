package app.zhizhi.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.zhizhi.R
import app.zhizhi.ui.components.HintBlock
import app.zhizhi.ui.components.StatusPill
import app.zhizhi.ui.components.ZhiZhiCard
import app.zhizhi.util.Permissions

private data class PermItem(
    val kind: PermKind,
    val titleRes: Int,
    val whyRes: Int,
    val optional: Boolean,
)

/**
 * 权限页。
 *
 * 除了"为什么需要"，每一项还直接给出**在当前机型上点到哪一层**——
 * 各家 ROM 的路径差异是这一页最大的痛点，光说"去系统设置开启"等于没说。
 * 顶部有一个开关切换"只看本机 / 显示全部机型"。
 *
 * 从系统设置返回时会自动刷新（LifecycleResumeEffect），不用手动点刷新。
 */
@Composable
fun PermissionsScreen(
    primaryLabel: String,
    onPrimary: () -> Unit,
) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    var showAll by rememberSaveable { mutableStateOf(false) }

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

    val myGuide = remember {
        OemGuides.detect(Build.MANUFACTURER, Build.BRAND) ?: OemGuides.generic
    }

    val items = listOf(
        PermItem(PermKind.Usage, R.string.perm_usage_title, R.string.perm_usage_why, optional = false),
        PermItem(PermKind.Overlay, R.string.perm_overlay_title, R.string.perm_overlay_why, optional = false),
        PermItem(PermKind.Notifications, R.string.perm_notif_title, R.string.perm_notif_why, optional = true),
        PermItem(PermKind.Battery, R.string.perm_battery_title, R.string.perm_battery_why, optional = true),
    )
    val granted = listOf(usageGranted, overlayGranted, notifGranted, batteryOk)
    val missingRequired = listOf(0, 1).filter { !granted[it] }
    val missingOptional = listOf(2, 3).filter { !granted[it] }
    val requiredGranted = 2 - missingRequired.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        HintBlock(
            text = when {
                // 顺序要紧：必需项缺了就说必需项的事；必需项齐了但可选项没开，
                // 不能再显示"四项权限都已就绪"——那是一句会当场被用户拆穿的话。
                missingRequired.isNotEmpty() ->
                    stringResource(R.string.perm_summary_missing, missingRequired.size)
                missingOptional.isNotEmpty() ->
                    stringResource(R.string.perm_summary_optional_missing, missingOptional.size)
                else -> stringResource(R.string.perm_summary_ok)
            },
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.perm_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ---------------- 机型切换 ----------------
        Spacer(Modifier.height(14.dp))
        ZhiZhiCard {
            Column(Modifier.padding(14.dp)) {
                Text(
                    text = stringResource(R.string.perm_current_device, myGuide.label),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.perm_path_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { showAll = !showAll }) {
                    Text(
                        stringResource(
                            if (showAll) R.string.perm_only_mine else R.string.perm_show_all,
                        ),
                    )
                }
            }
        }

        // ---------------- 四张权限卡 ----------------
        var requiredIndex = 0
        items.forEachIndexed { index, item ->
            val stepLabel = if (item.optional) {
                null
            } else {
                requiredIndex += 1
                stringResource(R.string.perm_step_label, requiredIndex)
            }
            Spacer(Modifier.height(12.dp))
            PermissionCard(
                item = item,
                granted = granted[index],
                stepLabel = stepLabel,
                myGuide = myGuide,
                showAll = showAll,
                onGrant = {
                    when (item.kind) {
                        PermKind.Usage -> open(Permissions.usageAccessIntent())
                        PermKind.Overlay -> open(Permissions.overlayIntent(context))
                        PermKind.Notifications ->
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                open(Permissions.notificationIntent(context))
                            }

                        PermKind.Battery -> open(Permissions.batteryIntent())
                    }
                },
            )
        }

        // ---------------- 厂商后台设置 ----------------
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

        // ---------------- 主按钮 ----------------
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
                        stringResource(items[missingRequired.first()].titleRes),
                    ),
                )
            }
            Spacer(Modifier.height(8.dp))
        } else {
            Text(
                text = stringResource(R.string.perm_required_ready, requiredGranted),
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
        OutlinedButton(onClick = { refresh() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.perm_refresh))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PermissionCard(
    item: PermItem,
    granted: Boolean,
    stepLabel: String?,
    myGuide: OemGuide,
    showAll: Boolean,
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
                    if (stepLabel != null) StatusPill(text = stepLabel, ok = true)
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
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.perm_steps_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))

                if (showAll) {
                    listOf(myGuide).forEach { ShowSteps(it, item.kind, highlight = true) }
                    (OemGuides.all + OemGuides.generic)
                        .filter { it.label != myGuide.label }
                        .forEach { ShowSteps(it, item.kind, highlight = false) }
                } else {
                    ShowSteps(myGuide, item.kind, highlight = true)
                }

                Spacer(Modifier.height(12.dp))
                Button(onClick = onGrant) {
                    Text(stringResource(R.string.goto_settings))
                }
            }
        }
    }
}

@Composable
private fun ShowSteps(guide: OemGuide, kind: PermKind, highlight: Boolean) {
    val steps = guide.steps[kind].orEmpty()
    if (steps.isEmpty()) return
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(
            text = guide.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (highlight) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(
                    if (highlight) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Column {
                steps.forEachIndexed { i, line ->
                    Text(
                        text = "${i + 1}. $line",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (highlight) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}
