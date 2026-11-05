package com.alex.studydemo.module_compose.modal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alex.studydemo.module_compose.modal.IosModalDemoActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 在真实 Compose 滚动容器中验证关闭入口与手势分配，避免只测试阈值而漏掉嵌套滚动。 */
@RunWith(AndroidJUnit4::class)
class IosModalHostTest {
    @get:Rule
    val compose = createAndroidComposeRule<IosModalDemoActivity>()

    /** 完成后可重新打开，验证退场移除以及新的入场过程。 */
    @Test
    fun doneDismissesAndCanReopen() {
        open("open_compact")
        compose.onNodeWithText("完成").performClick()
        assertClosed()
        open("open_compact")
        compose.onNodeWithText("分享你的下一站").assertIsDisplayed()
    }

    /** 使用实际触摸点击顶部遮罩，检查卡片之外的区域确实可以关闭。 */
    @Test
    fun scrimDismisses() {
        open("open_compact")
        compose.onNodeWithTag("ios_modal_scrim").performTouchInput {
            click(Offset(centerX, height * 0.15f))
        }
        assertClosed()
    }

    /** 卡片内的留白不能把点击穿透给背后的关闭遮罩。 */
    @Test
    fun blankAreaInsideSheetDoesNotDismiss() {
        open("open_compact")
        compose.onNodeWithTag("ios_modal_sheet").performTouchInput {
            click(Offset(width * 0.02f, height * 0.5f))
        }
        compose.onNodeWithTag("ios_modal_sheet").assertIsDisplayed()
    }

    /** 返回键优先关闭 Dialog，背景 Activity 仍可再次打开模态。 */
    @Test
    fun backDismissesWithoutLeavingPage() {
        open("open_compact")
        Espresso.pressBack()
        assertClosed()
        compose.onNodeWithTag("open_compact").assertIsDisplayed()
    }

    /** 慢速短拖不足阈值时必须回弹到原位，不能仅检查卡片仍然存在。 */
    @Test
    fun shortDragSpringsBack() {
        open("open_compact")
        val originalTop = sheetTop()
        compose.onNodeWithTag("ios_modal_handle").performTouchInput {
            down(center)
            moveBy(Offset(0f, 70f), delayMillis = 500)
        }
        compose.waitForIdle()
        assertTrue("短拖应先带动卡片，再在松手后回弹", sheetTop() > originalTop + 20f)
        compose.onNodeWithTag("ios_modal_handle").performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(originalTop, sheetTop(), 1f)
    }

    /** 慢速拖过卡片高度的四成，即使没有快速甩动也会关闭。 */
    @Test
    fun longDragDismisses() {
        open("open_compact")
        val originalTop = sheetTop()
        val sheetHeight = compose.onNodeWithTag("ios_modal_sheet").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithTag("ios_modal_handle").performTouchInput {
            down(center)
            repeat(20) { moveBy(Offset(0f, sheetHeight * 0.02f), delayMillis = 50) }
        }
        compose.waitForIdle()
        assertTrue("长拖实际位移为 ${sheetTop() - originalTop}，应超过 ${sheetHeight * 0.25f}", sheetTop() - originalTop > sheetHeight * 0.25f)
        compose.onNodeWithTag("ios_modal_handle").performTouchInput { up() }
        assertClosed()
    }

    /** 列表中间下拉只滚动列表；回到顶部后同样的下拉会带走卡片。 */
    @Test
    fun listOnlyDragsSheetAtTop() {
        open("open_list")
        val originalTop = sheetTop()
        val list = compose.onNodeWithTag("destination_list")
        list.performScrollToIndex(18)
        list.performTouchInput {
            swipe(center, center + Offset(0f, height * 0.3f), durationMillis = 1000)
        }
        compose.waitForIdle()
        assertEquals(originalTop, sheetTop(), 1f)
        list.performScrollToIndex(0)
        list.performTouchInput {
            swipe(Offset(centerX, height * 0.2f), Offset(centerX, height * 0.7f), durationMillis = 1000)
        }
        assertClosed()
    }

    /** 选择后关闭，并把结果显示到背景设置行；配置变化也应保留该选择。 */
    @Test
    fun selectionSurvivesRecreation() {
        open("open_list")
        compose.onNodeWithText("上海").performClick()
        assertClosed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("上海  ›").assertIsDisplayed()
    }

    /** 通过首页实际入口打开，等待动画完成后再测量位置，避免把入场过程算作手势位移。 */
    private fun open(tag: String) {
        compose.onNodeWithTag(tag).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("ios_modal_sheet").assertIsDisplayed()
    }

    /** 等待退场动画完成后检查 Dialog 内容已经移除。 */
    private fun assertClosed() {
        compose.waitForIdle()
        compose.onNodeWithTag("ios_modal_sheet").assertDoesNotExist()
    }

    /** 返回卡片在窗口内的实际顶部像素坐标，用于比较回弹与滚动前后的位置。 */
    private fun sheetTop(): Float =
        compose.onNodeWithTag("ios_modal_sheet").fetchSemanticsNode().boundsInRoot.top
}
