package com.alex.studydemo.module_compose.components.iosmodal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/**
 * 仿 iOS 卡片模态容器；须放在页面根部并获得有限高度，背景 [content] 始终保留在组合中。
 *
 * [visible] 由调用方控制，收到 [onDismissRequest] 后应设为 false；退场动画由容器完成。
 * [properties] 控制高度和各个关闭入口；配置可在显示期间更新。
 * [header] 可替换顶部区域，组件仍会为其提供拖动能力；[title] 同时作为无障碍窗格名称。
 * [shape]、[containerColor]、[contentColor] 和 [scrimColor] 只影响外观，不改变业务内容。
 * [sheetContent] 提供的关闭回调与遮罩、返回键、下拉关闭行为相同。
 * 内容可使用 LazyColumn 或 verticalScroll；滚动到顶后的下拉由卡片接管。
 */
@Composable
fun IosModal(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    properties: IosModalProperties = IosModalProperties(),
    shape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    scrimColor: Color = Color.Black.copy(alpha = 0.32f),
    dismissLabel: String = "关闭弹窗",
    header: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit = {
        IosModalDefaults.Header(title = title, onDismissRequest = it)
    },
    sheetContent: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val motion = remember(scope) { IosModalMotion(scope) }
    val latestDismiss by rememberUpdatedState(onDismissRequest)
    val latestVisible by rememberUpdatedState(visible)
    val latestDragEnabled by rememberUpdatedState(visible && properties.dragToDismiss)
    val requestDismiss = remember {
        // 退场期间仍保留 Dialog 来拦截触摸，但不重复通知调用方。
        { if (latestVisible) latestDismiss() }
    }
    // 显示期间禁用拖动时恢复展开位置，避免卡片停在半途；关闭仍统一播放退场。
    LaunchedEffect(visible, properties.dragToDismiss) { motion.animateTo(if (visible) 0f else 1f) }
    val progress = 1f - motion.hiddenFraction
    val density = LocalDensity.current
    val backgroundRadius = with(density) { 16.dp.toPx() }
    val backgroundShift = with(density) { 12.dp.toPx() }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    // 背景和卡片共用进度，拖动时背景同步恢复原来的大小。
                    scaleX = 1f - 0.055f * progress
                    scaleY = 1f - 0.055f * progress
                    translationY = backgroundShift * progress
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    this.shape = RoundedCornerShape(backgroundRadius * progress)
                    clip = progress > 0f
                }
                .then(if (visible || progress > 0f) Modifier.clearAndSetSemantics { } else Modifier),
        ) { content() }

        // visible 变为 false 后等待进度到达终点，避免关闭动画被移出组合而截断。
        if (visible || motion.hiddenFraction < 1f) {
            Dialog(
                onDismissRequest = requestDismiss,
                properties = DialogProperties(
                    dismissOnBackPress = properties.dismissOnBackPress,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                ),
            ) {
                val view = LocalView.current
                SideEffect {
                    // 遮罩由 Compose 绘制并跟随拖动，关闭系统默认的第二层调暗和窗口动画。
                    (view.parent as? DialogWindowProvider)?.window?.apply {
                        setDimAmount(0f)
                        setWindowAnimations(0)
                    }
                }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val topInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
                    val sheetHeight = minOf(maxHeight * properties.heightFraction, maxHeight - topInset - 12.dp)
                        .coerceAtLeast(1.dp)
                    val heightPx = with(density) { sheetHeight.toPx() }
                    val velocityThreshold = with(density) { 1000.dp.toPx() }
                    val nestedScroll = remember(motion, heightPx, velocityThreshold) {
                        modalNestedScroll(motion, heightPx, velocityThreshold, { latestDragEnabled }, requestDismiss)
                    }
                    Box(
                        Modifier.fillMaxSize()
                            .background(scrimColor.copy(alpha = scrimColor.alpha * progress))
                            .clickable(
                                enabled = visible && properties.dismissOnClickOutside,
                                onClickLabel = dismissLabel,
                                onClick = requestDismiss,
                            )
                            .testTag("ios_modal_scrim"),
                    )
                    Column(
                        Modifier.align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                            .fillMaxWidth()
                            .height(sheetHeight)
                            .graphicsLayer { translationY = heightPx * motion.hiddenFraction }
                            .clip(shape)
                            .background(containerColor)
                            // 空白区域参与命中以挡住遮罩；不消费按下事件，让标题拖动正常识别。
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    waitForUpOrCancellation()?.consume()
                                }
                            }
                            .semantics {
                                paneTitle = title
                                dismiss { requestDismiss(); true }
                            }
                            .testTag("ios_modal_sheet")
                            // 底部安全区与键盘取并集，键盘出现时只压缩内容区，不遮挡操作。
                            .windowInsetsPadding(
                                WindowInsets.navigationBars.union(WindowInsets.ime)
                                    .only(WindowInsetsSides.Bottom),
                            ),
                    ) {
                        CompositionLocalProvider(LocalContentColor provides contentColor) {
                            Column(
                                Modifier.fillMaxWidth()
                                    .draggable(
                                        state = rememberDraggableState { delta ->
                                            if (latestDragEnabled) motion.dragBy(delta, heightPx)
                                        },
                                        orientation = Orientation.Vertical,
                                        enabled = visible && properties.dragToDismiss,
                                        onDragStarted = { motion.stop() },
                                        onDragStopped = { velocity ->
                                            if (latestDragEnabled) {
                                                motion.settle(velocity, heightPx, velocityThreshold, requestDismiss)
                                            }
                                        },
                                    )
                                    .testTag("ios_modal_handle"),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                header(requestDismiss)
                            }
                            Column(Modifier.weight(1f).nestedScroll(nestedScroll)) {
                                sheetContent(requestDismiss)
                            }
                        }
                    }
                }
            }
        }
    }
}
