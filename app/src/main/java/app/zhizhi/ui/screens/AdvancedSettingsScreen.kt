package app.zhizhi.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zhizhi.Graph
import app.zhizhi.R
import app.zhizhi.data.MonitorSettings
import app.zhizhi.monitor.MonitorService
import app.zhizhi.ui.components.CardSection
import app.zhizhi.ui.components.SegmentedOption
import app.zhizhi.ui.components.SliderRow
import app.zhizhi.ui.components.SwitchRow
import app.zhizhi.ui.components.ZhiZhiDivider
import app.zhizhi.util.formatClock
import kotlinx.coroutines.launch

/**
 * 其他个性化设置（底部 tab）。
 *
 * 首页只留每天都会碰的东西；会显著改变行为但不常改的参数集中在这里。
 * 起因：0.1.4 之前"回答后的冷却"是写死的常量、界面上看不到，被当成 bug 排查了很久。
 */
@Composable
fun AdvancedSettingsScreen() {
    val scope = rememberCoroutineScope()
    val settings by Graph.settings.settings.collectAsStateWithLifecycle()

    fun update(block: (MonitorSettings) -> MonitorSettings) {
        scope.launch { Graph.settings.edit(block) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.advanced_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))
        CardSection(title = stringResource(R.string.advanced_section_timing)) {
            SliderRow(
                title = stringResource(R.string.home_repeat),
                value = settings.repeatIntervalSec / 60f,
                range = 0f..30f,
                steps = 29,
                format = {
                    if (it.roundToInt() == 0) {
                        stringResource(R.string.home_repeat_once)
                    } else {
                        stringResource(R.string.home_threshold_value, it.roundToInt())
                    }
                },
                onCommit = { v -> update { it.copy(repeatIntervalSec = v.roundToInt() * 60) } },
            )
            SliderRow(
                title = stringResource(R.string.home_answer_cooldown),
                value = settings.answerCooldownSec / 60f,
                range = 0f..10f,
                steps = 9,
                format = {
                    if (it.roundToInt() == 0) {
                        stringResource(R.string.home_answer_cooldown_off)
                    } else {
                        stringResource(R.string.home_threshold_value, it.roundToInt())
                    }
                },
                onCommit = { v -> update { it.copy(answerCooldownSec = v.roundToInt() * 60) } },
            )
            Text(
                text = stringResource(R.string.home_answer_cooldown_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ZhiZhiDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            SliderRow(
                title = stringResource(R.string.home_grace_len),
                value = settings.graceSec / 60f,
                range = 5f..60f,
                steps = 54,
                format = { stringResource(R.string.home_threshold_value, it.roundToInt()) },
                onCommit = { v -> update { it.copy(graceSec = v.roundToInt() * 60) } },
            )
        }

        Spacer(Modifier.height(18.dp))
        CardSection(title = stringResource(R.string.advanced_section_break)) {
            SliderRow(
                title = stringResource(R.string.advanced_break_default),
                value = settings.breakDurationMin.toFloat(),
                range = 5f..60f,
                steps = 54,
                format = { stringResource(R.string.home_threshold_value, it.roundToInt()) },
                onCommit = { v -> update { it.copy(breakDurationMin = v.roundToInt()) } },
                subtitle = stringResource(R.string.advanced_break_default_desc),
            )
            SliderRow(
                title = stringResource(R.string.advanced_break_extend),
                value = settings.breakExtendMin.toFloat(),
                range = 1f..30f,
                steps = 28,
                format = { stringResource(R.string.home_threshold_value, it.roundToInt()) },
                onCommit = { v -> update { it.copy(breakExtendMin = v.roundToInt()) } },
            )
            Text(
                text = stringResource(R.string.advanced_break_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(18.dp))
        CardSection(title = stringResource(R.string.advanced_section_card)) {
            SwitchRow(
                title = stringResource(R.string.home_game_confirm),
                subtitle = stringResource(R.string.home_game_confirm_desc),
                checked = settings.gameConfirmEnabled,
                onChange = { on -> update { it.copy(gameConfirmEnabled = on) } },
            )
            ZhiZhiDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_card_position),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SegmentedOption(
                    stringResource(R.string.home_pos_top),
                    !settings.cardAtBottom,
                ) { update { it.copy(cardAtBottom = false) } }
                SegmentedOption(
                    stringResource(R.string.home_pos_bottom),
                    settings.cardAtBottom,
                ) { update { it.copy(cardAtBottom = true) } }
            }
        }

        Spacer(Modifier.height(18.dp))
        CardSection(title = stringResource(R.string.advanced_section_background)) {
            SwitchRow(
                title = stringResource(R.string.advanced_autostart),
                subtitle = stringResource(R.string.advanced_autostart_desc),
                checked = settings.autoStartOnBoot,
                onChange = { on -> update { it.copy(autoStartOnBoot = on) } },
            )
            ZhiZhiDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            KillTargetRow(
                checked = settings.killTargetOnExit,
                onChange = { on -> update { it.copy(killTargetOnExit = on) } },
            )
        }

        Spacer(Modifier.height(18.dp))
        CardSection(title = stringResource(R.string.advanced_section_pause)) {
            Text(
                text = stringResource(R.string.advanced_pause_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            val now = System.currentTimeMillis()
            if (settings.isPausedNow(now)) {
                Text(
                    text = stringResource(R.string.home_paused_until, formatClock(settings.pausedUntilMs)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { update { it.copy(pausedUntilMs = 0L) } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.home_resume_now)) }
            } else {
                OutlinedButton(
                    onClick = {
                        update {
                            it.copy(
                                pausedUntilMs = System.currentTimeMillis() + MonitorService.PAUSE_STEP_MS,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.home_pause_30)) }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * "退出后清后台"。Android 14 起 killBackgroundProcesses 只能结束自己的进程（官方文档明确写了），
 * 所以在 14+ 上直接标成"本机不支持"并禁用开关——不给用户一个永远不生效却看不出来的开关。
 */
@Composable
private fun KillTargetRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    val supported = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    SwitchRow(
        title = stringResource(R.string.advanced_kill_target),
        subtitle = stringResource(
            if (supported) {
                R.string.advanced_kill_target_desc
            } else {
                R.string.advanced_kill_target_unsupported
            },
        ),
        checked = checked && supported,
        enabled = supported,
        onChange = onChange,
    )
}
