package com.alex.studydemo.module_compose.components.iosmodal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 验证实际绘制边界，避免只检查配置值而遗漏仍在执行的背景缩放或平移动画。 */
@RunWith(AndroidJUnit4::class)
class IosModalPresentationTest {
    @get:Rule
    val compose = createComposeRule()

    /** 浮层打开、跟手、回弹、退场后背景都保持原边界；长拖仍能关闭卡片。 */
    @Test
    fun overlayKeepsBackgroundBoundsThroughoutPresentation() {
        setModal(IosModalPresentationStyle.Overlay)
        val original = backgroundBounds()
        compose.onNodeWithTag("open_presentation_test").performClick()
        assertBackgroundBounds(original)
        val sheet = compose.onNodeWithTag("ios_modal_sheet").fetchSemanticsNode().boundsInRoot
        // Dialog 与 Activity 拥有不同窗口坐标，卡片留白应在 Dialog 自己的窗口内比较。
        val dialog = compose.onNodeWithTag("ios_modal_scrim").fetchSemanticsNode().boundsInRoot
        assertTrue("浮层左右应有留白", sheet.left > dialog.left && sheet.right < dialog.right)
        assertTrue("浮层底部应避开导航区并保留留白", sheet.bottom < dialog.bottom)

        compose.onNodeWithTag("ios_modal_handle").performTouchInput {
            down(center)
            moveBy(Offset(0f, 80f), delayMillis = 500)
        }
        assertBackgroundBounds(original)
        val shortDragTop = compose.onNodeWithTag("ios_modal_sheet").fetchSemanticsNode().boundsInRoot.top
        assertTrue("短拖应带动浮层，实际位移为 ${shortDragTop - sheet.top}", shortDragTop > sheet.top + 20f)
        compose.onNodeWithTag("ios_modal_handle").performTouchInput { up() }
        assertBackgroundBounds(original)
        assertEquals(sheet.top, compose.onNodeWithTag("ios_modal_sheet").fetchSemanticsNode().boundsInRoot.top, 1f)

        compose.onNodeWithTag("ios_modal_handle").performTouchInput {
            down(center)
            repeat(20) { moveBy(Offset(0f, sheet.height * 0.02f), delayMillis = 50) }
        }
        assertBackgroundBounds(original)
        val longDragTop = compose.onNodeWithTag("ios_modal_sheet").fetchSemanticsNode().boundsInRoot.top
        assertTrue("长拖应超过关闭阈值，实际位移为 ${longDragTop - sheet.top}", longDragTop > sheet.top + sheet.height * 0.3f)
        compose.onNodeWithTag("ios_modal_handle").performTouchInput { up() }
        compose.waitForIdle()
        compose.onNodeWithTag("ios_modal_sheet").assertDoesNotExist()
        assertBackgroundBounds(original)
    }

    /** 默认缩放样式继续让背景退后，避免增加 Overlay 时改变已有呈现行为。 */
    @Test
    fun stackedStillShrinksBackground() {
        setModal(IosModalPresentationStyle.Stacked)
        val original = backgroundBounds()
        compose.onNodeWithTag("open_presentation_test").performClick()
        val presented = backgroundBounds()
        assertTrue(presented.width < original.width)
        assertTrue(presented.height < original.height)
        compose.onNodeWithText("完成").performClick()
        assertBackgroundBounds(original)
    }

    /** 两种样式只改变呈现配置，共用内容和外部显示状态，验证组件可直接复用。 */
    private fun setModal(style: IosModalPresentationStyle) {
        val visible = mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                IosModal(
                    visible = visible.value,
                    onDismissRequest = { visible.value = false },
                    title = "呈现样式测试",
                    properties = IosModalProperties(heightFraction = 0.68f, presentationStyle = style),
                    sheetContent = { Text("卡片内容") },
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Button(
                            onClick = { visible.value = true },
                            modifier = Modifier.testTag("open_presentation_test"),
                        ) { Text("打开") }
                    }
                }
            }
        }
    }

    /** 即使背景业务语义被隐藏，容器边界仍可用于检查实际渲染变换。 */
    private fun backgroundBounds(): Rect {
        compose.waitForIdle()
        return compose.onNodeWithTag("ios_modal_background").fetchSemanticsNode().boundsInRoot
    }

    /** 同时比较位置和尺寸，防止仅验证宽高而漏掉背景下移。 */
    private fun assertBackgroundBounds(expected: Rect) {
        val actual = backgroundBounds()
        assertEquals(expected.left, actual.left, 0.5f)
        assertEquals(expected.top, actual.top, 0.5f)
        assertEquals(expected.right, actual.right, 0.5f)
        assertEquals(expected.bottom, actual.bottom, 0.5f)
    }
}
