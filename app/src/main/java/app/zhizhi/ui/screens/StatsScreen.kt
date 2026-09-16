package app.zhizhi.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zhizhi.Graph
import app.zhizhi.R
import app.zhizhi.ui.components.CardSection
import app.zhizhi.ui.components.SegmentedOption
import app.zhizhi.ui.components.StatCard
import app.zhizhi.ui.components.ValueRow
import app.zhizhi.util.daysAgoKey
import app.zhizhi.util.formatDuration
import kotlinx.coroutines.launch

/** 记录页（底部 tab）。数据只在本机，可一键清空。 */
@Composable
fun StatsScreen() {
    val scope = rememberCoroutineScope()
    val days by Graph.stats.days.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    var windowDays by remember { mutableIntStateOf(7) }

    // 按**日期区间**取，而不是"最近 N 条记录"。
    // 后者在中间有几天没记录时会把更早的日子算进来，用户看到"近 7 天"里出现三周前的数据。
    val fromDay = daysAgoKey(windowDays - 1)
    val recent = days.entries
        .filter { it.key >= fromDay }
        .sortedByDescending { it.key }
    val nudges = recent.sumOf { it.value.nudges }
    val returned = recent.sumOf { it.value.returned }
    val snoozed = recent.sumOf { it.value.snoozed }
    val grace = recent.sumOf { it.value.grace }
    val declined = recent.sumOf { it.value.gameDeclined }
    val muted = recent.sumOf { it.value.mutedApps }
    val monitored = recent.sumOf { it.value.monitoredMs }
    val exitAttempts = recent.sumOf { it.value.exitAttempts }
    val exitSuccess = recent.sumOf { it.value.exitSuccess }
    val overlayShown = recent.sumOf { it.value.overlayShown }
    val overlayFailures = recent.sumOf { it.value.overlayFailures }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.stats_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.stats_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SegmentedOption("近 7 天", windowDays == 7) { windowDays = 7 }
            SegmentedOption("近 30 天", windowDays == 30) { windowDays = 30 }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(
                label = stringResource(R.string.stats_nudges),
                value = nudges.toString(),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = stringResource(R.string.stats_returned),
                value = returned.toString(),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = stringResource(R.string.stats_monitored),
                value = formatDuration(monitored),
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(18.dp))
        CardSection(title = "明细") {
            ValueRow(stringResource(R.string.stats_snoozed), snoozed.toString())
            ValueRow(stringResource(R.string.stats_grace), grace.toString())
            ValueRow(stringResource(R.string.stats_game_declined), declined.toString())
            ValueRow(stringResource(R.string.stats_muted), muted.toString())
            ValueRow(stringResource(R.string.stats_exit_probe), exitSuccess.toString() + " / " + exitAttempts)
            ValueRow(stringResource(R.string.stats_overlay), overlayShown.toString() + " / " + overlayFailures)
        }

        if (exitAttempts > 0 && exitSuccess == 0) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "「一键回到桌面」在本机从未生效过。卡片按钮本身仍然有效，只是自动退出需要你手动点。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(18.dp))
        if (recent.isEmpty()) {
            Text(
                text = stringResource(R.string.stats_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            CardSection(title = "逐日") {
                recent.forEach { entry ->
                    ValueRow(
                        entry.key,
                        entry.value.nudges.toString() + " 次 · " + formatDuration(entry.value.monitoredMs),
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        OutlinedButton(
            onClick = { confirmClear = true },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.stats_clear)) }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.stats_clear)) },
            text = { Text(stringResource(R.string.stats_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { Graph.stats.clear() }
                    confirmClear = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
