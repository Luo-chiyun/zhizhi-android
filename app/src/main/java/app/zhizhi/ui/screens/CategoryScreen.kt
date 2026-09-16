package app.zhizhi.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zhizhi.Graph
import app.zhizhi.R
import app.zhizhi.data.AppCategory
import app.zhizhi.data.AppEntry
import app.zhizhi.data.PresetCatalog
import app.zhizhi.ui.components.SegmentedOption
import app.zhizhi.ui.components.ZhiZhiDivider
import kotlinx.coroutines.launch

private enum class CategoryFilter { ALL, SELECTED }

/** 监控哪些应用。默认全部"不监控"——不会因为你装了抖音就自动开始计时。 */
@Composable
fun CategoryScreen() {
    val scope = rememberCoroutineScope()
    val overrides by Graph.settings.overrides.collectAsStateWithLifecycle()

    var installed by remember { mutableStateOf<List<AppEntry>?>(null) }
    var matchedPresets by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf(CategoryFilter.ALL) }
    var query by remember { mutableStateOf("") }
    val selections: SnapshotStateMap<String, AppCategory> = remember {
        mutableStateMapOf<String, AppCategory>().apply { putAll(overrides) }
    }

    LaunchedEffect(Unit) {
        val list = Graph.installed.load()
        installed = list
        list.forEach { entry ->
            val resolved = Graph.classifier.resolvedWithLabelOverride(entry.packageName, entry.label)
            if (resolved != AppCategory.IGNORED && selections[entry.packageName] == null) {
                selections[entry.packageName] = resolved
            }
        }
        matchedPresets = PresetCatalog.matchInstalled(list).size
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.cat_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.cat_apply_presets_result, matchedPresets),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = {
                val list = installed ?: return@TextButton
                // 与 SettingsRepository.setCategories 保持一致：跳过用户已明确设为
                // 「不监控」的应用，避免界面说套用了、实际被跳过。
                val matched = PresetCatalog.matchInstalled(list)
                    .filterKeys { overrides[it] != AppCategory.IGNORED }
                if (matched.isEmpty()) return@TextButton
                val map = matched.mapValues { it.value.category }
                selections.putAll(map)
                scope.launch { Graph.settings.setCategories(map) }
            }) { Text(stringResource(R.string.cat_apply_presets)) }
        }

        Text(
            text = stringResource(R.string.cat_apply_presets_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text(stringResource(R.string.cat_search_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SegmentedOption(
                stringResource(R.string.cat_filter_all),
                filter == CategoryFilter.ALL,
            ) { filter = CategoryFilter.ALL }
            SegmentedOption(
                stringResource(R.string.cat_filter_selected),
                filter == CategoryFilter.SELECTED,
            ) { filter = CategoryFilter.SELECTED }
        }
        Spacer(Modifier.height(12.dp))

        val list = installed
        if (list == null) {
            Text("正在读取本机应用列表…", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
        } else {
            val visible = list.filter { entry ->
                val textOk = query.isBlank() ||
                    entry.label.contains(query, ignoreCase = true) ||
                    entry.packageName.contains(query, ignoreCase = true)
                val filterOk = when (filter) {
                    CategoryFilter.ALL -> true
                    // "已选"= 会被提醒的。预设里"不用监测"的应用也在 overrides 里，不该混进来。
                    CategoryFilter.SELECTED -> selections[entry.packageName]?.isMonitored == true
                }
                textOk && filterOk
            }

            if (visible.isEmpty()) {
                Text(
                    text = stringResource(R.string.cat_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(visible, key = { it.packageName }) { entry ->
                        AppRow(
                            entry = entry,
                            current = selections[entry.packageName] ?: AppCategory.IGNORED,
                            onSelect = { category ->
                                selections[entry.packageName] = category
                                scope.launch {
                                    Graph.settings.setCategory(entry.packageName, category)
                                }
                            },
                        )
                        ZhiZhiDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AppRow(
    entry: AppEntry,
    current: AppCategory,
    onSelect: (AppCategory) -> Unit,
) {
    Column(Modifier.padding(vertical = 10.dp)) {
        Text(
            text = entry.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = entry.packageName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppCategory.entries.forEach { category ->
                SegmentedOption(
                    text = categoryLabel(category),
                    selected = category == current,
                ) { onSelect(category) }
            }
        }
    }
}

@Composable
private fun categoryLabel(category: AppCategory): String = stringResource(
    when (category) {
        AppCategory.IGNORED -> R.string.cat_label_ignored
        AppCategory.ENTERTAINMENT -> R.string.cat_label_entertainment
        AppCategory.GAME -> R.string.cat_label_game
    },
)

