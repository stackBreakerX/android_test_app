package com.alex.studydemo.module_compose

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 从统一目录验证实际 Activity 跳转和返回路径，覆盖包迁移后的 Manifest 注册。 */
@RunWith(AndroidJUnit4::class)
class ComposeEntryTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComposeDemoActivity>()

    /** 模态与侧滑均从 Compose 目录进入，系统返回后还能继续选择下一个示例。 */
    @Test
    fun modalAndSwipeShareOneEntry() {
        compose.onNodeWithTag("compose_entry_modal").performClick()
        waitForScreen(hasTestTag("open_compact"))
        Espresso.pressBack()
        waitForScreen(hasTestTag("compose_entry_swipe"))
        compose.onNodeWithTag("compose_entry_swipe").performClick()
        waitForScreen(hasText("信息"))
        Espresso.pressBack()
        waitForScreen(hasTestTag("compose_entry_modal"))
    }

    /** 状态示例已经替换空白占位，修改状态后应立即更新界面。 */
    @Test
    fun stateExampleUpdatesCount() {
        compose.onNodeWithTag("compose_entry_state").performClick()
        waitForScreen(hasText("点击次数：0"))
        compose.onNodeWithText("增加计数").performClick()
        compose.onNodeWithText("点击次数：1").assertIsDisplayed()
    }

    /** 跨 Activity 切换可能短暂无 Compose 根节点，需要等待目标页面首帧而非立即断言。 */
    private fun waitForScreen(matcher: SemanticsMatcher) {
        compose.waitUntil(timeoutMillis = 5000) {
            compose.onAllNodes(matcher).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNode(matcher).assertIsDisplayed()
    }
}
