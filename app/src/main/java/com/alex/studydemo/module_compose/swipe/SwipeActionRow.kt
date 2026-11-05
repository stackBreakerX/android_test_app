package com.alex.studydemo.module_compose.swipe

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * 侧滑按钮。列表的第 0 个按钮放在最靠屏幕边缘的位置，也是 full swipe 时触发的那个。
 *
 * @param destructive 为 true 时，触发后行保持滑出状态（交给列表做收缩删除），否则回弹关闭。
 */
@Immutable
data class SwipeAction(
    val label: String,
    val icon: String,
    val background: Color,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * 仿 iPhone「信息」的列表左右侧滑按钮（Compose 实现）。
 *
 * 行为：按钮跟手按比例展开；超出按钮总宽后有阻尼；拖过 [FULL_SWIPE_FRACTION] 行宽时
 * 最外侧按钮吞掉其余按钮并震动，松手直接执行；松手时带速度用弹簧吸附；同一时间只开一行。
 *
 * ## 与 iOS 的实现难度对比
 * iOS（UIKit `trailingSwipeActionsConfigurationForRowAt` / SwiftUI `.swipeActions`）里，
 * 以下能力全是系统自带，配置 UIContextualAction 数组 + `performsFirstActionWithFullSwipe`
 * 开关即可，约 10 行代码：
 * - 跟手展开 + 按钮按比例分配宽度
 * - 阻尼（rubber band）、弹簧吸附、带速度松手
 * - full swipe：最外侧按钮扩展铺满 + 触感反馈 + 删除行收缩
 * - 同一时间只开一行、列表滚动或点击别处时自动关闭
 *
 * Android 没有对应的官方组件：Material3 `SwipeToDismissBox` 只能整行滑走，不能停在按钮位置；
 * `AnchoredDraggable` 会把 offset 夹在锚点之间，做不出阻尼和 full swipe。所以这里全部手写：
 * - 手势：`draggable` 处理横向拖动与 touch slop，纵向滑动仍交给 LazyColumn
 * - 动画：`Animatable` 存 offset，拖动时 snapTo，松手 animateTo(spring, initialVelocity)
 * - 阻尼：自己实现 UIScrollView 的公式 [rubberBand] 及其反函数 [inverseRubberBand]
 * - full swipe：自己判阈值、做按钮宽度扩展动画、调 haptic
 * - 只开一行：由列表层用 openId 状态协调（见 SwipeActionsActivity）
 * 难点集中在手势冲突、跨行状态协调和 full swipe 的细节动效，组件约 250 行。
 */
@Composable
fun SwipeActionRow(
    isOpen: Boolean,
    onOpenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    leading: List<SwipeAction> = emptyList(),
    trailing: List<SwipeAction> = emptyList(),
    actionWidth: Dp = 74.dp,
    contentBackground: Color = Color.White,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val actionWidthPx = with(density) { actionWidth.toPx() }
    val latestOnOpenChange by rememberUpdatedState(onOpenChange)

    var rowWidth by remember { mutableIntStateOf(0) }
    // 正值 = 向右拖，露出 leading；负值 = 向左拖，露出 trailing
    val offset = remember { Animatable(0f) }
    // 不含阻尼的手指累计位移
    var rawOffset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    // full swipe 是否已触发阈值：0 无，1 leading，-1 trailing
    var armedSide by remember { mutableIntStateOf(0) }
    // 执行 full swipe / 删除动画时，忽略外部关闭信号
    var committing by remember { mutableStateOf(false) }

    val expand by animateFloatAsState(
        targetValue = if (armedSide != 0) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "fullSwipeExpand",
    )

    fun actionsFor(side: Float): List<SwipeAction> = when {
        side > 0f -> leading
        side < 0f -> trailing
        else -> emptyList()
    }

    fun maxRevealFor(side: Float): Float = actionsFor(side).size * actionWidthPx

    // full swipe 或点击删除时，铺满的那个按钮下标
    var expandedIndex by remember { mutableIntStateOf(0) }

    /** 手指位移 -> 实际显示的 offset（加阻尼或 full swipe 跟手）。 */
    fun displayFor(raw: Float): Float {
        val side = sign(raw)
        val max = maxRevealFor(side)
        val width = rowWidth.toFloat()
        val magnitude = abs(raw)
        val shown = when {
            magnitude <= max -> magnitude
            // 有按钮的一侧支持 full swipe：超出后继续 1:1 跟手，直到行宽
            actionsFor(side).isNotEmpty() -> magnitude.coerceAtMost(width)
            else -> max + rubberBand(magnitude - max, width)
        }
        return side * shown
    }

    /** [displayFor] 的反函数，动画中途被手指接住时用来还原手指位移，避免跳动。 */
    fun rawFor(display: Float): Float {
        val side = sign(display)
        val max = maxRevealFor(side)
        val magnitude = abs(display)
        if (magnitude <= max || actionsFor(side).isNotEmpty()) return display
        return side * (max + inverseRubberBand(magnitude - max, rowWidth.toFloat()))
    }

    fun isPastFullSwipe(display: Float): Boolean {
        val side = sign(display)
        if (actionsFor(side).isEmpty()) return false
        val threshold = maxOf(rowWidth * FULL_SWIPE_FRACTION, maxRevealFor(side) + actionWidthPx)
        return abs(display) >= threshold
    }

    suspend fun animateOffsetTo(target: Float, velocity: Float = 0f) {
        // 关闭时临界阻尼，不越过 0，否则会闪出另一侧的按钮；打开时允许轻微回弹
        val spec = if (target == 0f) {
            spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)
        } else {
            spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
        }
        offset.animateTo(target, spec, initialVelocity = velocity)
        if (target == 0f) latestOnOpenChange(false)
    }

    /** 执行 [action]：按钮铺满并滑到边缘；非破坏性动作执行后回弹关闭。 */
    suspend fun commit(action: SwipeAction, index: Int, side: Float, velocity: Float) {
        committing = true
        expandedIndex = index
        armedSide = side.toInt()
        try {
            offset.animateTo(
                targetValue = side * rowWidth,
                animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium),
                initialVelocity = velocity,
            )
            action.onClick()
            if (!action.destructive) {
                armedSide = 0
                animateOffsetTo(0f)
            }
        } finally {
            committing = false
        }
    }

    suspend fun settle(velocity: Float) {
        val current = offset.value
        val side = sign(current)
        if (armedSide != 0 && side.toInt() == armedSide) {
            commit(actionsFor(side).first(), 0, side, velocity)
            return
        }
        armedSide = 0
        val max = maxRevealFor(side)
        // 按松手速度预测落点（类似 UIScrollView 的减速投影），超过一半按钮宽就打开
        val projected = current + velocity * PROJECTION_SECONDS
        val open = max > 0f && sign(projected) == side && abs(projected) > max / 2f
        animateOffsetTo(if (open) side * max else 0f, velocity)
    }

    val dragState = rememberDraggableState { delta ->
        if (rowWidth == 0) return@rememberDraggableState
        rawOffset += delta
        val target = displayFor(rawOffset)
        // UNDISPATCHED：没有竞争时 snapTo 同步完成，保证跟手不掉帧
        scope.launch(start = CoroutineStart.UNDISPATCHED) { offset.snapTo(target) }
        val newArmed = if (isPastFullSwipe(target)) sign(target).toInt() else 0
        if (newArmed != armedSide) {
            if (newArmed != 0) {
                expandedIndex = 0
                haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            }
            armedSide = newArmed
        }
    }

    // 列表让别的行打开、或开始滚动时，isOpen 变为 false，本行自动收回
    LaunchedEffect(isOpen) {
        if (!isOpen && !dragging && !committing && offset.value != 0f) {
            armedSide = 0
            animateOffsetTo(0f)
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { rowWidth = it.width }
            .clipToBounds()
            // draggable 放在不移动的外层，放在位移的内容上会导致坐标反馈抖动
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                enabled = !committing,
                onDragStarted = {
                    dragging = true
                    offset.stop()
                    rawOffset = rawFor(offset.value)
                    latestOnOpenChange(true)
                },
                onDragStopped = { velocity ->
                    dragging = false
                    settle(velocity)
                },
            ),
    ) {
        val shown = offset.value
        val side = sign(shown)
        val actions = actionsFor(side)
        val revealed = abs(shown)
        if (actions.isNotEmpty() && revealed > 0f) {
            val base = revealed / actions.size
            val ordered = actions.withIndex().let { if (side < 0f) it.reversed() else it.toList() }
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .background(actions.first().background),
                horizontalArrangement = if (side > 0f) Arrangement.Start else Arrangement.End,
            ) {
                ordered.forEach { (index, action) ->
                    val target = if (index == expandedIndex) revealed else 0f
                    ActionButton(
                        action = action,
                        widthPx = lerp(base, target, expand),
                        actionWidth = actionWidth,
                        // 按钮比标准宽时，内容贴着行内容那一侧（iOS 的 full swipe 效果）
                        innerAlignment = if (side > 0f) Alignment.CenterEnd else Alignment.CenterStart,
                        onClick = {
                            scope.launch {
                                if (action.destructive) {
                                    commit(action, index, side, 0f)
                                } else {
                                    action.onClick()
                                    animateOffsetTo(0f)
                                }
                            }
                        },
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .background(contentBackground),
        ) {
            content()
        }
    }
}

@Composable
private fun ActionButton(
    action: SwipeAction,
    widthPx: Float,
    actionWidth: Dp,
    innerAlignment: Alignment,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val actionWidthPx = with(density) { actionWidth.toPx() }
    Box(
        modifier = Modifier
            .width(with(density) { widthPx.toDp() })
            .fillMaxHeight()
            .background(action.background)
            .clipToBounds()
            .clickable(onClick = onClick),
        contentAlignment = if (widthPx > actionWidthPx) innerAlignment else Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .requiredWidth(actionWidth)
                // 露出不到一半时图标文字淡入
                .alpha((widthPx / actionWidthPx * 2f - 0.4f).coerceIn(0f, 1f)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = action.icon, fontSize = 20.sp)
            Text(
                text = action.label,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

private const val FULL_SWIPE_FRACTION = 0.6f
private const val PROJECTION_SECONDS = 0.12f
private const val RUBBER_BAND_COEFFICIENT = 0.55f

/** UIScrollView 的阻尼公式：越拉越难拉，最多趋近 [dimension]。 */
private fun rubberBand(overshoot: Float, dimension: Float): Float {
    if (dimension <= 0f) return 0f
    return (1f - 1f / (overshoot * RUBBER_BAND_COEFFICIENT / dimension + 1f)) * dimension
}

private fun inverseRubberBand(shown: Float, dimension: Float): Float {
    if (dimension <= 0f) return 0f
    val ratio = (shown / dimension).coerceAtMost(0.99f)
    return (1f / (1f - ratio) - 1f) * dimension / RUBBER_BAND_COEFFICIENT
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction
