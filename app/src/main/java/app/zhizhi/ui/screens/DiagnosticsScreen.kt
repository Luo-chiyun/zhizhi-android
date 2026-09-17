package app.zhizhi.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zhizhi.BuildConfig
import app.zhizhi.Graph
import app.zhizhi.R
import app.zhizhi.data.DiagnosticsStore
import app.zhizhi.monitor.MonitorService
import app.zhizhi.notify.Notifications
import app.zhizhi.ui.components.CardSection
import app.zhizhi.ui.components.HintBlock
import app.zhizhi.ui.components.ValueRow
import app.zhizhi.ui.components.ZhiZhiCard
import app.zhizhi.ui.components.ZhiZhiDivider
import app.zhizhi.util.Permissions
import kotlinx.coroutines.launch

/**
 * 诊断页。
 *
 * 设计原则：**这一页本身不允许有静默失败。**
 * 事件列表的权威副本在内存里（不经过磁盘），另有"主循环心跳 / 当前判定"两个活体指标，
 * 只要服务在跑就一定会动——这是判断"诊断到底有没有在撒谎"的依据。
 */
@Composable
fun DiagnosticsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val events by Graph.diagnostics.events.collectAsStateWithLifecycle()
    val totalRecorded by Graph.diagnostics.totalRecorded.collectAsStateWithLifecycle()
    val lastError by Graph.diagnostics.lastError.collectAsStateWithLifecycle()

    val running by MonitorService.running.collectAsStateWithLifecycle()
    val anchorVisible by MonitorService.anchorVisible.collectAsStateWithLifecycle()
    val heartbeat by MonitorService.heartbeat.collectAsStateWithLifecycle()
    val loopState by MonitorService.loopState.collectAsStateWithLifecycle()

    var refreshKey by remember { mutableIntStateOf(0) }
    val overlayPermission = remember(refreshKey) { Permissions.canDrawOverlays(context) }
    val usagePermission = remember(refreshKey) { Permissions.hasUsageAccess(context) }
    val notification = remember(refreshKey) { Permissions.canPostNotifications(context) }
    val alertChannel = remember(refreshKey) { Notifications.alertChannelStatus(context) }

    var logExpanded by rememberSaveable { mutableStateOf(false) }
    val logText = remember(events) { DiagnosticsStore.toLines(events) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.diag_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ---------------------------------------------------------- 活体指标
        Spacer(Modifier.height(14.dp))
        CardSection(
            title = stringResource(R.string.diag_live_title),
            trailing = {
                TextButton(onClick = { refreshKey += 1 }) { Text(stringResource(R.string.diag_refresh)) }
            },
        ) {
            Text(
                text = stringResource(R.string.diag_live_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            ValueRow(stringResource(R.string.diag_live_heartbeat), heartbeat.toString())
            ValueRow(stringResource(R.string.diag_live_state), loopState)
            ValueRow(
                stringResource(R.string.diag_live_total),
                stringResource(R.string.diag_total_unit, totalRecorded),
            )
        }

        // ---------------------------------------------------------- 环境
        Spacer(Modifier.height(16.dp))
        CardSection(title = stringResource(R.string.diag_env)) {
            ValueRow(stringResource(R.string.diag_pkg), context.packageName)
            ValueRow(
                stringResource(R.string.diag_version),
                BuildConfig.VERSION_NAME + "（" + BuildConfig.VERSION_CODE + "）",
            )
            ValueRow(stringResource(R.string.diag_system), "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")")
            ValueRow(stringResource(R.string.diag_model), Build.MANUFACTURER + " " + Build.MODEL)
            ValueRow(stringResource(R.string.diag_overlay_perm), boolText(overlayPermission))
            ValueRow(stringResource(R.string.diag_usage_perm), boolText(usagePermission))
            ValueRow(stringResource(R.string.diag_notif_perm), boolText(notification))
            ValueRow(stringResource(R.string.diag_alert_channel), alertChannel.importanceText())
            ValueRow(stringResource(R.string.diag_service_running), boolText(running))
            ValueRow(stringResource(R.string.diag_anchor), boolText(anchorVisible))
            if (lastError != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.diag_persist_error, lastError ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        if (!alertChannel.canPeek) {
            Spacer(Modifier.height(12.dp))
            ZhiZhiCard {
                Text(
                    text = stringResource(R.string.diag_alert_warning),
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        // ---------------------------------------------------------- 开关清单
        Spacer(Modifier.height(12.dp))
        HintBlock(text = stringResource(R.string.diag_toggles_body))

        // ---------------------------------------------------------- 自检
        Spacer(Modifier.height(16.dp))
        CardSection(title = stringResource(R.string.diag_self_test_title)) {
            Text(
                text = stringResource(R.string.diag_test_card_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { MonitorService.testCard(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.diag_test_card)) }
        }

        // ---------------------------------------------------------- 事件（单一块）
        Spacer(Modifier.height(16.dp))
        CardSection(
            title = stringResource(R.string.diag_events_header, events.size),
            trailing = {
                if (events.isNotEmpty()) {
                    TextButton(onClick = { logExpanded = !logExpanded }) {
                        Text(
                            stringResource(
                                if (logExpanded) {
                                    R.string.diag_events_collapse
                                } else {
                                    R.string.diag_events_expand
                                },
                            ),
                        )
                    }
                }
            },
        ) {
            Text(
                text = stringResource(R.string.diag_events_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                events.isEmpty() -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.diag_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                logExpanded -> {
                    Spacer(Modifier.height(10.dp))
                    ZhiZhiDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(10.dp))
                    SelectionContainer {
                        Text(
                            text = logText,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        // ---------------------------------------------------------- 导出
        Spacer(Modifier.height(16.dp))
        // 文案在这里取好：onClick 是普通闭包，不在 Compose 作用域里，
        // 里面直接调 stringResource 编译不过。
        val copiedToast = stringResource(R.string.diag_copied)
        Button(
            onClick = {
                copyToClipboard(context, Graph.diagnostics.dumpText())
                Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.diag_copy_button)) }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.diag_copy_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))
        HintBlock(
            text = stringResource(R.string.diag_copy_head) + "\n" +
                stringResource(R.string.diag_copy_body),
        )

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { scope.launch { Graph.diagnostics.clear() } },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.diag_clear)) }
        Spacer(Modifier.height(24.dp))
    }
}

private fun copyToClipboard(context: Context, text: String) {
    runCatching {
        val manager = context.getSystemService(ClipboardManager::class.java) ?: return
        manager.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.diag_export_title), text))
    }
}

@Composable
private fun boolText(value: Boolean): String =
    if (value) stringResource(R.string.diag_bool_yes) else stringResource(R.string.diag_bool_no)
