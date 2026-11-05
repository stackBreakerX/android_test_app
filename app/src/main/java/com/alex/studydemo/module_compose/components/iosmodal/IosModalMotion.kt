package com.alex.studydemo.module_compose.components.iosmodal

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 统一管理归一化位移，0 为展开、1 为隐藏。尺寸改变时沿用比例，避免横竖屏切换越界。
 * 拖动同步写入状态；动画使用单一 Job，任何新手势都会取消旧动画，避免逐帧协程竞争。
 */
@Stable
internal class IosModalMotion(private val scope: CoroutineScope) {
    var hiddenFraction by mutableFloatStateOf(1f)
        private set
    private var animation: Job? = null

    /** 新手势接管当前位置，不重置到锚点，因此可以接住正在回弹的卡片。 */
    fun stop() { animation?.cancel() }

    /** [delta] 与 [height] 单位均为 px，返回实际消费的位移，剩余量交回滚动内容。 */
    fun dragBy(delta: Float, height: Float): Float {
        stop()
        val old = hiddenFraction
        hiddenFraction = (old + delta / height).coerceIn(0f, 1f)
        return (hiddenFraction - old) * height
    }

    /** 关闭使用定时退场，打开和回弹使用无超调弹簧，确保卡片底部不露出背景。 */
    fun animateTo(target: Float, initialVelocity: Float = 0f) {
        stop()
        animation = scope.launch {
            animate(
                initialValue = hiddenFraction,
                targetValue = target,
                initialVelocity = initialVelocity,
                animationSpec = if (target == 1f) tween(240) else spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) { value, _ -> hiddenFraction = value.coerceIn(0f, 1f) }
        }
    }

    /**
     * 位移超过 25% 或向下快速甩动即可关闭；明确向上甩动优先回弹。
     * [velocity] 和 [velocityThreshold] 单位为 px/s，阈值按密度换算以保持设备间手感一致。
     */
    fun settle(velocity: Float, height: Float, velocityThreshold: Float, dismiss: () -> Unit) {
        if (velocity > -velocityThreshold &&
            (hiddenFraction >= 0.25f || velocity >= velocityThreshold)
        ) {
            dismiss()
        } else {
            animateTo(0f, velocity / height)
        }
    }
}

/** 内容先消费下拉；只有到顶剩余的手指位移才移动卡片，上推时则先恢复卡片位置。 */
internal fun modalNestedScroll(
    motion: IosModalMotion,
    height: Float,
    velocityThreshold: Float,
    enabled: () -> Boolean,
    dismiss: () -> Unit,
): NestedScrollConnection = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // 只响应用户拖动，列表自身的惯性滚动不能意外带动整个模态。
        return if (enabled() && source == NestedScrollSource.UserInput && available.y < 0 && motion.hiddenFraction > 0) {
            Offset(0f, motion.dragBy(available.y, height))
        } else Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        // 关闭动画期间不再接管手势，否则残留的滚动事件可能取消退场动画。
        return if (enabled() && source == NestedScrollSource.UserInput && available.y > 0) {
            Offset(0f, motion.dragBy(available.y, height))
        } else Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        // 卡片已经离开展开位置时接管松手速度，避免同时触发列表惯性和卡片回弹。
        return if (enabled() && motion.hiddenFraction > 0f) {
            motion.settle(available.y, height, velocityThreshold, dismiss)
            available
        } else Velocity.Zero
    }
}
