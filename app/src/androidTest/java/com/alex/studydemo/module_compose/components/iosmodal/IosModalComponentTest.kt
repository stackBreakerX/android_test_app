package com.alex.studydemo.module_compose.components.iosmodal

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 直接调用通用组件，验证其不依赖演示 Activity，并覆盖可配置关闭策略。 */
@RunWith(AndroidJUnit4::class)
class IosModalComponentTest {
    @get:Rule
    val compose = createComposeRule()

    /** 自定义顶部替换默认按钮，业务内容仍可通过组件提供的回调请求关闭。 */
    @Test
    fun customHeaderAndContentDismiss() {
        var visible by mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                IosModal(
                    visible = visible,
                    onDismissRequest = { visible = false },
                    title = "自定义弹窗",
                    header = { Text("自定义顶部", Modifier.padding(24.dp)) },
                    sheetContent = { dismiss ->
                        TextButton(onClick = dismiss) { Text("关闭自定义内容") }
                    },
                ) { Text("背景内容") }
            }
        }
        compose.onNodeWithText("自定义顶部").assertIsDisplayed()
        compose.onNodeWithText("完成").assertDoesNotExist()
        compose.onNodeWithText("关闭自定义内容").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("ios_modal_sheet").assertDoesNotExist()
    }

    /** 禁用关闭手势后，遮罩、返回键、标题及内容下拉都不应发出关闭请求。 */
    @Test
    fun disabledDismissPoliciesKeepSheetOpen() {
        var dismissCalls = 0
        compose.setContent {
            MaterialTheme {
                IosModal(
                    visible = true,
                    onDismissRequest = { dismissCalls += 1 },
                    title = "受控弹窗",
                    properties = IosModalProperties(0.6f, false, false, false),
                    sheetContent = {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            repeat(30) { Text("内容 $it", Modifier.padding(20.dp)) }
                        }
                    },
                ) { Text("背景") }
            }
        }
        val originalTop = sheetTop()
        clickScrim()
        Espresso.pressBack()
        compose.onNodeWithTag("ios_modal_handle").performTouchInput {
            swipe(center, center + Offset(0f, 500f), durationMillis = 800)
        }
        compose.onNodeWithText("内容 0").performTouchInput {
            swipe(center, center + Offset(0f, 500f), durationMillis = 800)
        }
        compose.waitForIdle()
        assertEquals(originalTop, sheetTop(), 1f)
        compose.runOnIdle { assertEquals(0, dismissCalls) }
        // 手势开关不限制显式业务操作，默认顶部按钮仍可通知调用方。
        compose.onNodeWithText("完成").performClick()
        compose.runOnIdle { assertEquals(1, dismissCalls) }
    }

    /** 显示过程中更新关闭开关，确保回调和配置不会被 remember 捕获为旧值。 */
    @Test
    fun outsideDismissPolicyUpdatesWhileVisible() {
        var visible by mutableStateOf(true)
        var allowOutside by mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                IosModal(
                    visible = visible,
                    onDismissRequest = { visible = false },
                    title = "动态配置",
                    properties = IosModalProperties(heightFraction = 0.6f, dismissOnClickOutside = allowOutside),
                    sheetContent = { Text("弹窗内容") },
                ) { Text("背景") }
            }
        }
        clickScrim()
        compose.onNodeWithTag("ios_modal_sheet").assertIsDisplayed()
        compose.runOnIdle { allowOutside = true }
        clickScrim()
        compose.waitForIdle()
        compose.onNodeWithTag("ios_modal_sheet").assertDoesNotExist()
    }

    /** 拖动中途禁用手势时恢复展开位置，不能停在部分隐藏状态。 */
    @Test
    fun disablingDragRestoresExpandedPosition() {
        var allowDrag by mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                IosModal(
                    visible = true,
                    onDismissRequest = {},
                    title = "动态拖动配置",
                    properties = IosModalProperties(dragToDismiss = allowDrag),
                    sheetContent = { Text("内容") },
                ) { Text("背景") }
            }
        }
        val originalTop = sheetTop()
        compose.onNodeWithTag("ios_modal_handle").performTouchInput {
            down(center)
            moveBy(Offset(0f, 100f), delayMillis = 500)
        }
        compose.waitForIdle()
        assertTrue(sheetTop() > originalTop + 20f)
        compose.runOnIdle { allowDrag = false }
        compose.waitForIdle()
        assertEquals(originalTop, sheetTop(), 1f)
        compose.onNodeWithTag("ios_modal_handle").performTouchInput { up() }
    }

    /** 点击卡片上方的实际遮罩区域，不直接调用语义动作，以验证触摸命中。 */
    private fun clickScrim() {
        compose.onNodeWithTag("ios_modal_scrim").performTouchInput {
            click(Offset(centerX, height * 0.15f))
        }
        compose.waitForIdle()
    }

    /** 在动画空闲后获取实际卡片位置，验证禁用手势不会改变布局。 */
    private fun sheetTop(): Float {
        compose.waitForIdle()
        return compose.onNodeWithTag("ios_modal_sheet").fetchSemanticsNode().boundsInRoot.top
    }
}
