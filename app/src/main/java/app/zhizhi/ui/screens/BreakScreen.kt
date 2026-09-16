package app.zhizhi.ui.screens

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zhizhi.Graph
import app.zhizhi.R
import app.zhizhi.ui.components.CardSection
import app.zhizhi.ui.components.HintBlock
import app.zhizhi.ui.components.SliderRow
import app.zhizhi.util.formatCountdown
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 休息计时器。休息期间完全不监测、不提醒；到点会弹「知止不殆 · 休息有度」。 */
@Composable
fun BreakScreen() {
    val scope = rememberCoroutineScope()
    val settings by Graph.settings.settings.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val running = settings.breakEndsAtMs > now

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        CardSection(title = stringResource(R.string.break_title)) {
            if (running) {
                Text(
                    text = stringResource(
                        R.string.break_running,
                        formatCountdown(settings.breakEndsAtMs - now),
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch { Graph.settings.edit { it.copy(breakEndsAtMs = 0L) } }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.break_stop)) }
            } else {
                SliderRow(
                    title = stringResource(R.string.break_pick),
                    value = settings.breakDurationMin.toFloat(),
                    range = 5f..60f,
                    steps = 54,
                    format = { stringResource(R.string.home_threshold_value, it.roundToInt()) },
                    onCommit = { v ->
                        scope.launch {
                            Graph.settings.edit { it.copy(breakDurationMin = v.roundToInt()) }
                        }
                    },
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        val minutes = settings.breakDurationMin
                        scope.launch {
                            Graph.settings.edit {
                                it.copy(
                                    breakEndsAtMs = System.currentTimeMillis() + minutes * 60_000L,
                                )
                            }
                            Graph.stats.bump { day -> day.copy(breakTaken = day.breakTaken + 1) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.break_start, settings.breakDurationMin)) }
            }
        }

        Spacer(Modifier.height(12.dp))
        HintBlock(
            text = stringResource(R.string.break_desc) + "\n" +
                stringResource(R.string.advanced_break_hint),
        )
        Spacer(Modifier.height(24.dp))
    }
}
