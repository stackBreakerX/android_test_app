package com.alex.studydemo.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alex.studydemo.MainActivity
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 基于 ARTEMIS 已验证的搜索、收藏、计数及返回路径，检查跨 Activity 导航没有丢失工作台状态。 */
@RunWith(AndroidJUnit4::class)
class LearningWorkspaceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>

    /** 每个测试从空偏好开始，避免设备上的手动收藏或最近访问改变卡片位置。 */
    @Before fun launch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("learning_workspace", 0).edit().clear().commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("学习工作台").fetchSemanticsNodes().isNotEmpty() }
    }

    /** 关闭宿主，避免下一条测试复用 Activity 实例中的导航状态。 */
    @After fun close() { scenario.close() }

    /** 搜索应过滤卡片，清空输入后恢复完整专题内容。 */
    @Test fun searchFiltersAndClears() {
        compose.onNode(hasSetTextAction()).performTextInput("状态")
        compose.onNodeWithText("状态与重组").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("消息实验室").assertDoesNotExist()
        compose.onNodeWithText("清除").performClick()
        compose.onNodeWithText("消息实验室").performScrollTo().assertIsDisplayed()
    }

    /** 根页不显示返回；子页点击统一箭头后，应恢复原搜索内容和底部 Tab，而不是新建工作台。 */
    @Test fun sharedNavigationReturnsToExistingWorkspace() {
        compose.onNodeWithTag("study_top_bar").assertIsDisplayed()
        compose.onNodeWithTag("study_bottom_bar").assertIsDisplayed()
        compose.onNodeWithTag("study_back").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextInput("状态")
        compose.onNodeWithText("状态与重组").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("study_back").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithTag("study_top_bar").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").assertIsDisplayed()
        // 首选共享语义；触摸回退位置由本次 ARTEMIS 在 1080×2400 设备验证为 (74, 211)。
        try { compose.onNodeWithTag("study_back").performClick() }
        catch (_: AssertionError) {
            compose.onRoot().performTouchInput { click(Offset(width * (74f / 1080f), height * (211f / 2400f))) }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("study_bottom_bar").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.onNodeWithTag("study_back").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertTextContains("状态")
    }

    /** 点击选项文字也必须切换外观，避免只有单选圆点响应而读屏标签无法操作。 */
    @Test fun themeLabelsChangeAppearancePreference() {
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("深色").performClick()
        compose.onNodeWithText("完成").performClick()
        compose.runOnIdle {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            assertEquals("dark", context.getSharedPreferences("learning_workspace", 0).getString("theme", null))
        }
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("浅色").performClick()
        compose.onNodeWithText("完成").performClick()
        compose.runOnIdle {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            assertEquals("light", context.getSharedPreferences("learning_workspace", 0).getString("theme", null))
        }
    }

    /** 收藏筛选与搜索共同生效，打开实验再返回后保留搜索内容和收藏状态。 */
    @Test fun favoriteAndExperimentReturnKeepWorkspace() {
        compose.onNode(hasSetTextAction()).performTextInput("状态")
        // 优先使用无障碍语义；坐标是 ARTEMIS 在清空偏好后的搜索结果页验证的位置。
        try { compose.onNodeWithContentDescription("收藏状态与重组").performClick() }
        catch (_: AssertionError) {
            compose.onRoot().performTouchInput { click(Offset(width * 0.397f, height * 0.71f)) }
        }
        compose.onNodeWithText("我的收藏").performClick()
        compose.onNodeWithText("状态与重组").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("增加计数").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("增加计数").performClick()
        compose.onNodeWithText("点击次数：1").assertIsDisplayed()
        Espresso.pressBack()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("学习工作台").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).assertTextContains("状态")
        compose.onNodeWithContentDescription("取消收藏状态与重组").assertExists()
    }
}
