package app.zhizhi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * iOS 闹钟那种上下滚动的转盘式时间选择器。
 *
 * 三行可见、中间那行是选中值，手指一拨就滑，停下自动吸附到最近一格。
 * 12 小时制时多一列 a.m./p.m.。
 *
 * 只在**滚动停止后**回调一次 [onChange]，不是每滑过一格就写一次 DataStore。
 */
@Composable
fun WheelTimePicker(
    minuteOfDay: Int,
    use24Hour: Boolean,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val h24 = (minuteOfDay / 60).mod(24)
    val minute = minuteOfDay.mod(60)
    val pm = h24 >= 12

    fun emit(hour: Int, min: Int) {
        val v = (hour.mod(24) * 60 + min).coerceIn(0, 24 * 60 - 1)
        if (v != minuteOfDay) onChange(v)
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        // 中间那条"选中带"，纯装饰，让哪一行是当前值一眼可见
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WheelRowHeight)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        )

        // 用 key 把整组滚轮挂在"当前格式"上：切 12/24 小时制时小时列的项目数会变
        // （24 项 ↔ 12 项），不重建的话 LazyList 会保留旧的滚动下标——
        // 轻则显示错位，重则下标越界落到最后一格，把时间**静默改成别的值**。
        key(use24Hour) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // ---- 小时 ----
                WheelColumn(
                    items = if (use24Hour) {
                        List(24) { it.toString().padStart(2, '0') }
                    } else {
                        List(12) { (it + 1).toString() }
                    },
                    index = if (use24Hour) h24 else (h24 % 12 + 11) % 12,
                    width = 50.dp,
                ) { idx ->
                    // 12 小时制下把 1..12 映射回 0..11，再按 am/pm 补 12
                    val newHour = if (use24Hour) idx else (idx + 1) % 12 + if (pm) 12 else 0
                    emit(newHour, minute)
                }

                Text(
                    text = ":",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // ---- 分钟 ----
                WheelColumn(
                    items = List(60) { it.toString().padStart(2, '0') },
                    index = minute,
                    width = 50.dp,
                ) { idx -> emit(h24, idx) }

                if (!use24Hour) {
                    // ---- a.m. / p.m. ----
                    WheelColumn(
                        items = listOf("a.m.", "p.m."),
                        index = if (pm) 1 else 0,
                        width = 64.dp,
                    ) { idx -> emit(h24 % 12 + if (idx == 1) 12 else 0, minute) }
                }
            }
        }
    }
}

private val WheelRowHeight = 38.dp
private const val VISIBLE_ROWS = 3

/**
 * 单列滚轮。
 *
 * 关键在 [derivedStateOf]：选中项不是"自己维护一个值"，而是每次布局都从
 * `layoutInfo` 里**算出离正中最近的那一行**。这样手指还在滑的时候高亮就跟着走，
 * 不用等滚动结束。
 */
@Composable
private fun WheelColumn(
    items: List<String>,
    index: Int,
    width: Dp,
    onSettled: (Int) -> Unit,
) {
    val state = rememberLazyListState()
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val pad = WheelRowHeight * (VISIBLE_ROWS / 2)

    LaunchedEffect(Unit) {
        state.scrollToItem(index.coerceIn(0, items.lastIndex))
    }

    // 兜底值要跟着当前的 index 走：derivedStateOf 的闭包只在首次组合时建立，
    // 直接捕获 index 会一直用第一次的那个值。
    val fallback by rememberUpdatedState(index)
    val centered by remember {
        derivedStateOf {
            val info = state.layoutInfo
            if (info.visibleItemsInfo.isEmpty()) {
                fallback
            } else {
                val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
                info.visibleItemsInfo
                    .minByOrNull { abs(it.offset + it.size / 2 - mid) }
                    ?.index ?: fallback
            }
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { centered to state.isScrollInProgress }
            .filter { (_, scrolling) -> !scrolling }
            .map { (c, _) -> c }
            .distinctUntilChanged()
            .collect { i -> if (i in items.indices) onSettled(i) }
    }

    LazyColumn(
        state = state,
        flingBehavior = fling,
        modifier = Modifier
            .width(width)
            .height(WheelRowHeight * VISIBLE_ROWS),
        contentPadding = PaddingValues(vertical = pad),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        itemsIndexed(items) { i, text ->
            // 用下标比对，不用 indexOf(text)：列值一旦出现重复（比如以后改成
            // 5 分钟一档），按内容查找会把高亮打到错误的那一行上。
            val selected = i == centered
            Box(
                modifier = Modifier.height(WheelRowHeight),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text,
                    fontSize = if (selected) 17.sp else 15.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        // 未选中的行淡下去，视觉上形成"离中心越远越虚"的效果
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    },
                )
            }
        }
    }
}
