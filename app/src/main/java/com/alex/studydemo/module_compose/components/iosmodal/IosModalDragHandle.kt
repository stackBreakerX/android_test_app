package com.alex.studydemo.module_compose.components.iosmodal

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * 标题随卡片移动，所以拖动位移与速度统一在窗口根坐标中计算，避免局部坐标变化抵消手指位移。
 * [travelPx] 为完全隐藏所需距离，单位 px；[velocityThreshold] 单位 px/s。
 * 手势取消时回弹；因禁用或关闭而取消时由容器的状态动画接管，不能再次覆盖退场动画。
 */
@Composable
internal fun Modifier.iosModalDragHandle(
    enabled: Boolean,
    motion: IosModalMotion,
    travelPx: Float,
    velocityThreshold: Float,
    onDismiss: () -> Unit,
): Modifier {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val tracker = remember { VelocityTracker() }
    val latestEnabled by rememberUpdatedState(enabled)
    val latestDismiss by rememberUpdatedState(onDismiss)
    if (!enabled) return this

    return onGloballyPositioned { coordinates = it }
        .pointerInput(motion, travelPx, velocityThreshold) {
            var previousRootY: Float? = null
            detectVerticalDragGestures(
                onDragStart = {
                    if (latestEnabled) {
                        motion.stop()
                        previousRootY = null
                        tracker.resetTracking()
                    }
                },
                onVerticalDrag = { change, overSlop ->
                    if (latestEnabled) {
                        val rootPosition = coordinates?.localToRoot(change.position) ?: change.position
                        // 首个回调使用越过识别阈值的部分；后续回调比较固定坐标中的实际手指位置。
                        val delta = previousRootY?.let { rootPosition.y - it } ?: overSlop
                        previousRootY = rootPosition.y
                        tracker.addPosition(change.uptimeMillis, rootPosition)
                        motion.dragBy(delta, travelPx)
                        change.consume()
                    }
                },
                onDragEnd = {
                    if (latestEnabled) {
                        motion.settle(tracker.calculateVelocity().y, travelPx, velocityThreshold, latestDismiss)
                    }
                },
                onDragCancel = {
                    if (latestEnabled) motion.animateTo(0f)
                },
            )
        }
}
