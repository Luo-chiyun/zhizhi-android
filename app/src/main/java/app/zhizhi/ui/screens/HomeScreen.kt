package app.zhizhi.ui.screens

import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zhizhi.Graph
import app.zhizhi.R
import app.zhizhi.data.MonitorSettings
import app.zhizhi.monitor.MonitorService
import app.zhizhi.ui.components.CardSection
import app.zhizhi.ui.components.FeatureTile
import app.zhizhi.ui.components.HintBlock
import app.zhizhi.ui.components.SectionHeader
import app.zhizhi.ui.components.SliderRow
import app.zhizhi.ui.components.StatCard
import app.zhizhi.ui.components.SwitchRow
import app.zhizhi.ui.theme.ZhiZhiPalette
import app.zhizhi.util.Permissions
import app.zhizhi.util.daysAgoKey
import app.zhizhi.util.formatClock
import app.zhizhi.util.formatCountdown
import app.zhizhi.util.formatDuration
import app.zhizhi.util.formatDurationTiny
import app.zhizhi.util.hhmm
import app.zhizhi.util.minuteOfDayNow
import app.zhizhi.util.todayKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 首页仪表盘。
 *
 * 布局照 UI 模板：日期小字 + 大字标题 → 主控卡片 → 三列统计卡 → 功能模块 2 列网格 → 箴言。
 */
@Composable
fun HomeScreen(
    onOpenCategories: () -> Unit,
    onOpenBreak: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by Graph.settings.settings.collectAsStateWithLifecycle()
    val overrides by Graph.settings.overrides.collectAsStateWithLifecycle()
    val days by Graph.stats.days.collectAsStateWithLifecycle()
    val running by MonitorService.running.collectAsStateWithLifecycle()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    fun update(block: (MonitorSettings) -> MonitorSettings) {
        scope.launch { Graph.settings.edit(block) }
    }

    // 只数真正会被提醒的应用。预设里"不用监测"的应用也会写进 overrides，
    // 直接用 overrides.size 会把它们算成"已选"，数字虚高。
    val monitoredCount = remember(overrides) { overrides.values.count { it.isMonitored } }

    val paused = settings.isPausedNow(now)
    val resting = settings.isResting(now)
    val inWindow = settings.schedule.contains(minuteOfDayNow(now))
    val statusText = when {
        !settings.masterEnabled -> stringResource(R.string.home_state_off)
        resting -> stringResource(R.string.home_resting, formatCountdown(settings.breakEndsAtMs - now))
        paused -> stringResource(R.string.home_paused_until, formatClock(settings.pausedUntilMs))
        !inWindow -> stringResource(R.string.home_state_outside_schedule)
        running -> stringResource(R.string.home_state_running)
        else -> stringResource(R.string.home_state_service_off)
    }

    val today = days[todayKey()] ?: app.zhizhi.data.DayStats()
    val yesterday = days[daysAgoKey(1)]
    val hasYesterday = yesterday != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))

        // ------------------------------------------------------------ 头部
        Text(
            text = currentDateText(stringResource(R.string.home_date_pattern)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.home_greeting),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (running) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        // ------------------------------------------------------------ 主控卡片
        Spacer(Modifier.height(16.dp))
        CardSection(title = stringResource(R.string.home_section_monitor)) {
            SwitchRow(
                title = stringResource(R.string.home_master_switch),
                subtitle = permissionSummary(context),
                checked = settings.masterEnabled,
                onChange = { on ->
                    if (on && Permissions.missingCritical(context)) {
                        onOpenPermissions()
                    } else {
                        update { it.copy(masterEnabled = on) }
                        if (on) MonitorService.start(context) else MonitorService.stop(context)
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            if (resting) {
                Button(
                    onClick = { update { it.copy(breakEndsAtMs = 0L) } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.home_end_rest)) }
            } else {
                Button(
                    onClick = {
                        val minutes = settings.breakDurationMin
                        update {
                            it.copy(breakEndsAtMs = System.currentTimeMillis() + minutes * 60_000L)
                        }
                        scope.launch {
                            Graph.stats.bump { day -> day.copy(breakTaken = day.breakTaken + 1) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.home_rest, settings.breakDurationMin)) }
            }
        }

        // ------------------------------------------------------------ 三列统计
        Spacer(Modifier.height(18.dp))
        Row(
            // IntrinsicSize.Min + 每张卡 fillMaxHeight：三张卡强制等高，
            // 不管内容是两行还是三行。光靠"内容一样长"是脆的。
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatCard(
                label = stringResource(R.string.stat_nudges),
                value = today.nudges.toString(),
                trend = trendText(today.nudges, yesterday?.nudges, hasYesterday),
                trendColor = trendColor(today.nudges, yesterday?.nudges, hasYesterday),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatCard(
                label = stringResource(R.string.stat_returned),
                value = today.returned.toString(),
                trend = trendText(today.returned, yesterday?.returned, hasYesterday),
                trendColor = trendColor(today.returned, yesterday?.returned, hasYesterday),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            StatCard(
                label = stringResource(R.string.stat_monitored),
                value = formatDurationTiny(today.monitoredMs),
                trend = trendText(
                    (today.monitoredMs / 60_000).toInt(),
                    yesterday?.let { (it.monitoredMs / 60_000).toInt() },
                    hasYesterday,
                ),
                trendColor = trendColor(
                    (today.monitoredMs / 60_000).toInt(),
                    yesterday?.let { (it.monitoredMs / 60_000).toInt() },
                    hasYesterday,
                ),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }

        // ------------------------------------------------------------ 一个应用都没选时的提示
        // 总开关开着、但一个应用都没勾，界面上会出现三个 0，看起来像"坏了"。
        // 实际原因是没有任何目标可监测——不提示的话，用户会以为统计不准。
        if (settings.masterEnabled && monitoredCount == 0) {
            Spacer(Modifier.height(12.dp))
            HintBlock(text = stringResource(R.string.home_no_apps_hint))
        }

        // ------------------------------------------------------------ 功能模块
        Spacer(Modifier.height(20.dp))
        SectionHeader(title = stringResource(R.string.home_section_modules))
        FeatureGrid(
            tiles = listOf(
                Triple(
                    R.drawable.ic_z_apps,
                    stringResource(R.string.feature_categories) to
                        stringResource(R.string.feature_categories_desc, monitoredCount),
                    onOpenCategories,
                ),
                Triple(
                    R.drawable.ic_z_timer,
                    stringResource(R.string.feature_break) to
                        stringResource(R.string.feature_break_desc),
                    onOpenBreak,
                ),
                Triple(
                    R.drawable.ic_z_chart,
                    stringResource(R.string.feature_stats) to
                        stringResource(R.string.feature_stats_desc),
                    onOpenStats,
                ),
                Triple(
                    R.drawable.ic_z_gear,
                    stringResource(R.string.feature_advanced) to
                        stringResource(R.string.feature_advanced_desc),
                    onOpenAdvanced,
                ),
                Triple(
                    R.drawable.ic_z_pulse,
                    stringResource(R.string.feature_diag) to
                        stringResource(R.string.feature_diag_desc),
                    onOpenDiagnostics,
                ),
                Triple(
                    R.drawable.ic_z_shield,
                    stringResource(R.string.feature_permissions) to
                        stringResource(R.string.feature_permissions_desc),
                    onOpenPermissions,
                ),
                Triple(
                    R.drawable.ic_z_info,
                    stringResource(R.string.feature_about) to
                        stringResource(R.string.feature_about_desc),
                    onOpenAbout,
                ),
            ),
        )

        // ------------------------------------------------------------ 学习时段与提醒阈值
        Spacer(Modifier.height(20.dp))
        CardSection(title = stringResource(R.string.home_section_schedule)) {
            SwitchRow(
                title = stringResource(R.string.home_schedule_enabled),
                checked = settings.schedule.enabled,
                onChange = { on -> update { it.copy(schedule = it.schedule.copy(enabled = on)) } },
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.home_schedule_range,
                        hhmm(settings.schedule.startMinute),
                        hhmm(settings.schedule.endMinute),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = {
                        pickTime(context, settings.schedule.startMinute) { minute ->
                            update { it.copy(schedule = it.schedule.copy(startMinute = minute)) }
                        }
                    },
                ) { Text(stringResource(R.string.home_schedule_start)) }
                OutlinedButton(
                    onClick = {
                        pickTime(context, settings.schedule.endMinute) { minute ->
                            update { it.copy(schedule = it.schedule.copy(endMinute = minute)) }
                        }
                    },
                ) { Text(stringResource(R.string.home_schedule_end)) }
            }
        }

        Spacer(Modifier.height(18.dp))
        CardSection(title = stringResource(R.string.home_section_reminder)) {
            SliderRow(
                title = stringResource(R.string.home_threshold),
                value = settings.firstNudgeAfterSec / 60f,
                range = 1f..30f,
                steps = 28,
                format = { stringResource(R.string.home_threshold_value, it.roundToInt()) },
                onCommit = { v -> update { it.copy(firstNudgeAfterSec = v.roundToInt() * 60) } },
            )
            Text(
                text = stringResource(R.string.home_threshold_more_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ------------------------------------------------------------ 箴言
        Spacer(Modifier.height(18.dp))
        HintBlock(
            text = stringResource(R.string.app_quote) + "\n—— " +
                stringResource(R.string.app_quote_source),
        )
        Spacer(Modifier.height(24.dp))
    }
}

/** 2 列功能网格。奇数个时最后一格左对齐，右半边留白。 */
@Composable
private fun FeatureGrid(tiles: List<Triple<Int, Pair<String, String>, () -> Unit>>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { rowTiles ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowTiles.forEach { (icon, text, onClick) ->
                    FeatureTile(
                        painterRes = icon,
                        title = text.first,
                        subtitle = text.second,
                        modifier = Modifier.weight(1f),
                        onClick = onClick,
                    )
                }
                if (rowTiles.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private fun currentDateText(pattern: String): String =
    SimpleDateFormat(pattern, Locale.CHINA).format(Date())

/**
 * 趋势文案与颜色。
 *
 * 颜色刻意带上产品判断：提醒次数下降 = 好事（绿），上升 = 需要注意（橙）。
 * 这和模板里 "+12% 绿 / 持平 橙" 的取向不同，但对这个工具才是有意义的。
 */
@Composable
private fun trendText(current: Int, previous: Int?, hasPrevious: Boolean): String? {
    // 没有昨天的数据就**整行不显示**。原来这里显示"今天开始记录"，
    // 结果只有前两张卡有第三行、监测卡没有，三张卡被撑成一高一低。
    //
    // 文案走 strings.xml：这三句原来在代码里硬写，而资源文件里同样三条
    // （stat_trend_up / down / flat）一直没人用——两处会各自漂移。
    if (!hasPrevious || previous == null) return null
    val diff = current - previous
    return when {
        diff > 0 -> stringResource(R.string.stat_trend_up, diff)
        diff < 0 -> stringResource(R.string.stat_trend_down, -diff)
        else -> stringResource(R.string.stat_trend_flat)
    }
}

@Composable
private fun trendColor(current: Int, previous: Int?, hasPrevious: Boolean): Color {
    if (!hasPrevious || previous == null) return MaterialTheme.colorScheme.onSurfaceVariant
    val diff = current - previous
    return when {
        diff < 0 -> ZhiZhiPalette.success
        diff > 0 -> ZhiZhiPalette.warning
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
fun permissionSummary(context: Context): String {
    val usage = Permissions.hasUsageAccess(context)
    val overlay = Permissions.canDrawOverlays(context)
    val usageLabel = stringResource(R.string.perm_usage_title)
    val overlayLabel = stringResource(R.string.perm_overlay_title)
    return when {
        usage && overlay -> stringResource(R.string.home_perm_ok)
        !usage && !overlay -> stringResource(R.string.home_perm_missing, "$usageLabel、$overlayLabel")
        !usage -> stringResource(R.string.home_perm_missing, usageLabel)
        else -> stringResource(R.string.home_perm_missing, overlayLabel)
    }
}

private fun pickTime(context: Context, minuteOfDay: Int, onPick: (Int) -> Unit) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onPick(hour * 60 + minute) },
        minuteOfDay / 60,
        minuteOfDay % 60,
        true,
    ).show()
}
