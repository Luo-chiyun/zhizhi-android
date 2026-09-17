package app.zhizhi.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * iOS 闹钟那种上下滚动的转盘式时间选择器。
 *
 * 三行可见、中间那行是选中值，手指一拨就滑，停下自动吸附到最近一格。
 * 12 小时制时多一列 a.m./p.m.。
 *
 * ## 为什么不用 LazyColumn
 *
 * 最早这版是 `LazyColumn` + `rememberSnapFlingBehavior`，在真机上两个毛病：
 *
 *  1. **手势被父级抢走。** 首页本身是 `verticalScroll`，滚轮也是竖直可滚动，
 *     同一根手指的竖直拖动会在两者之间分派——滑时间滑到一半整页开始动，
 *     落点跟着漂，于是"某些值特别难设"（其实是拖不到位）。
 *  2. **定位要靠猜。** `scrollToItem(index)` 与 `contentPadding` 的配合决定了
 *     某一项到底落在顶行还是中行，一旦差一行，转盘显示的值和它回报的值就不一致，
 *     轻则显示错位，重则**静默改掉用户设的时间**。
 *
 * 现在改成自己接管手势与坐标：位移只有一个浮点状态 [scroll]（单位是"第几项"），
 * 拖动 1:1 跟手，落点由 `round()` 直接算出来，不存在差一行的问题。
 *
 * [onDraggingChange] 会回报"是否正在拖转盘"，首页据此把 `verticalScroll` 关掉，
 * 彻底杜绝"拖时间拖动整页"。
 */
@Composable
fun WheelTimePicker(
    minuteOfDay: Int,
    use24Hour: Boolean,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onDraggingChange: (Boolean) -> Unit = {},
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

        // 用 key 把整组滚轮挂在"当前格式"上：切 12/24 小时制时小时列的项数会变
        // （24 项 ↔ 12 项），不重建的话项目下标会错位。
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
                    onDraggingChange = onDraggingChange,
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
                    onDraggingChange = onDraggingChange,
                ) { idx -> emit(h24, idx) }

                if (!use24Hour) {
                    // ---- a.m. / p.m. ----
                    WheelColumn(
                        items = listOf("a.m.", "p.m."),
                        index = if (pm) 1 else 0,
                        width = 64.dp,
                        onDraggingChange = onDraggingChange,
                    ) { idx -> emit(h24 % 12 + if (idx == 1) 12 else 0, minute) }
                }
            }
        }
    }
}

private val WheelRowHeight = 40.dp
private const val WHEEL_ROWS = 3

/** 甩一下的惯性折算成"还会滑多远"的秒数。越大越容易一甩翻好几格。 */
private const val FLING_PROJECTION_SEC = 0.22f

/** 一次甩动最多翻这么多格，防止在小列表上一把冲到底。 */
private const val MAX_FLING_ITEMS = 5f

/**
 * 单列转盘。
 *
 * [scroll] 的单位是"第几项"：`scroll == 2f` 表示第 2 项正好停在中间。
 * 用项数而不是像素当单位，改行高或换密度都不用重新换算。
 */
@Composable
private fun WheelColumn(
    items: List<String>,
    index: Int,
    width: Dp,
    onDraggingChange: (Boolean) -> Unit,
    onSettled: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val itemPx = with(density) { WheelRowHeight.toPx() }
    val lastIndex = items.lastIndex
    val scope = rememberCoroutineScope()

    var scroll by remember { mutableFloatStateOf(index.toFloat()) }
    var settleJob by remember { mutableStateOf<Job?>(null) }

    fun clamp(v: Float) = v.coerceIn(0f, lastIndex.toFloat())

    fun settle(velocityPxPerSec: Float) {
        settleJob?.cancel()
        // 把甩动速度折算成"还会滑多远"，再吸附到最近的整数格。
        // 没有这一段的话快速轻扫只会挪一格，手感像"不敏感"。
        // 但上限压到 ±[MAX_FLING_ITEMS] 格：12 项的小时列上，一个快甩就能冲到尽头，
        // 过头了还得往回拨，比"不敏感"更烦人。
        val momentumPx = -velocityPxPerSec * FLING_PROJECTION_SEC
        val momentumItems = (momentumPx / itemPx).coerceIn(-MAX_FLING_ITEMS, MAX_FLING_ITEMS)
        val target = clamp(scroll + momentumItems).roundToInt().coerceIn(0, lastIndex)
        settleJob = scope.launch {
            animate(
                initialValue = scroll,
                targetValue = target.toFloat(),
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) { value, _ -> scroll = value }
            scroll = target.toFloat()
            onSettled(target)
        }
    }

    // 外部值变化时对位：首次组合，以及切换 12/24 小时制被 key 重建时。
    // 吸附动画进行中不插手，免得和用户的手指打架。
    LaunchedEffect(index) {
        if (settleJob?.isActive == true) return@LaunchedEffect
        val target = clamp(index.toFloat())
        if (abs(scroll - target) > 0.01f) scroll = target
    }

    val dragState = rememberDraggableState { delta ->
        settleJob?.cancel()
        scroll = clamp(scroll - delta / itemPx)
    }

    Box(
        modifier = Modifier
            .width(width)
            .height(WheelRowHeight * WHEEL_ROWS)
            .clipToBounds()
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                // 不等待 touch slop：手指一落到转盘上就归它管。
                // 否则首页的竖直滚动会先一步抢走手势——这正是"滑时间把整页滑走"的成因。
                startDragImmediately = true,
                onDragStarted = { onDraggingChange(true) },
                onDragStopped = { velocity ->
                    onDraggingChange(false)
                    settle(velocity)
                },
            ),
    ) {
        // 中间那行的上边缘：三行时等于 1 个行高
        val middleTopPx = itemPx * (WHEEL_ROWS / 2)
        items.forEachIndexed { i, label ->
            val distance = abs(i - scroll)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WheelRowHeight)
                    .offset { IntOffset(0, (middleTopPx + (i - scroll) * itemPx).roundToInt()) }
                    .graphicsLayer {
                        // 离中间越远越淡、越小，形成转盘的纵深感
                        alpha = (1f - distance * 0.38f).coerceIn(0.12f, 1f)
                        val s = (1f - distance * 0.07f).coerceIn(0.8f, 1f)
                        scaleX = s
                        scaleY = s
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 17.sp,
                    fontWeight = if (i == index) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * 转盘拖动的看门狗。
 *
 * 首页把竖直滚动挂在这个状态上；万一某次手势被系统取消、没走到 `onDragStopped`，
 * 页面会一直滚不动。这里给一个兜底：最多按住 [WHEEL_DRAG_WATCHDOG_MS] 就放行，
 * 期间转盘自己的手势仍然会消费掉手指，不存在"拖时间拖动整页"。
 */
@Composable
fun rememberWheelDragGuard(): Pair<Boolean, (Boolean) -> Unit> {
    var dragging by remember { mutableStateOf(false) }
    // 首页每秒都会重组一次（状态那条倒计时要走秒），lambda 用 remember 固定下来，
    // 免得每秒生成新实例、白白把整组滚轮也带着重组一遍。
    val setter: (Boolean) -> Unit = remember { { v -> dragging = v } }
    LaunchedEffect(dragging) {
        if (dragging) {
            delay(WHEEL_DRAG_WATCHDOG_MS)
            dragging = false
        }
    }
    return dragging to setter
}

private const val WHEEL_DRAG_WATCHDOG_MS = 6_000L
