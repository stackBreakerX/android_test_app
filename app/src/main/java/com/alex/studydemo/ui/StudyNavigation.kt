package com.alex.studydemo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 返回图标按布局方向镜像，不依赖字体中的箭头字符；交互区域由 IconButton 保证。 */
private val backIcon = ImageVector.Builder("StudyBack", 24.dp, 24.dp, 24f, 24f, autoMirror = true).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(20f, 11f); horizontalLineTo(7.83f); lineTo(13.42f, 5.41f)
        lineTo(12f, 4f); lineTo(4f, 12f); lineTo(12f, 20f)
        lineTo(13.42f, 18.59f); lineTo(7.83f, 13f); horizontalLineTo(20f); close()
    }
}.build()

/**
 * 全应用顶部导航：空 onBack 表示根页面，无返回图标；回调由宿主决定退出还是关闭浮层。
 * 标题及副标题各占一行，过长时省略；操作槽保留业务菜单。浮层传入零 insets，避免重复消费状态栏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    backLabel: String = "返回",
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    actions: @Composable RowScope.() -> Unit = {},
) {
    CenterAlignedTopAppBar(
        modifier = Modifier.testTag("study_top_bar"),
        title = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall)
            }
        },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.testTag("study_back")) {
                Icon(backIcon, contentDescription = backLabel)
            }
        },
        actions = actions,
        windowInsets = windowInsets,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = containerColor,
            titleContentColor = contentColor, navigationIconContentColor = contentColor, actionIconContentColor = contentColor),
    )
}

/** Tab 只包含展示数据；选中状态与切换规则仍由工作台持有，避免组件内出现第二份导航状态。 */
data class StudyTab(val label: String, val symbol: String)

/** 底部栏集中管理安全区和选中语义，回调返回条目索引；列表应在一次页面会话中保持稳定顺序。 */
@Composable
fun StudyBottomBar(tabs: List<StudyTab>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    NavigationBar(modifier = Modifier.testTag("study_bottom_bar"), containerColor = MaterialTheme.colorScheme.surface) {
        tabs.forEachIndexed { index, tab ->
            NavigationBarItem(selected = selectedIndex == index, onClick = { onSelect(index) },
                icon = { Text(tab.symbol, style = MaterialTheme.typography.headlineSmall) }, label = { Text(tab.label) })
        }
    }
}

/** 页面壳统一安装顶部栏与底部槽；content 收到安全区内边距，只由具体容器应用一次。此处不覆盖业务主题。 */
@Composable
fun StudyScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.background,
    barColor: Color = MaterialTheme.colorScheme.surface,
    barContentColor: Color = MaterialTheme.colorScheme.onSurface,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(topBar = { StudyTopBar(title, onBack, subtitle, containerColor = barColor, contentColor = barContentColor, actions = actions) },
        bottomBar = bottomBar, containerColor = containerColor, content = content)
}

/**
 * 常规实验页面的 Column 容器。固定列表页传 scrollable=false，避免给 RecyclerView 或 LazyColumn 无限高度。
 * 容器消费 Scaffold 边距后再处理键盘，内容区不再添加 safeDrawingPadding；权重仍相对于本 Column 计算。
 */
@Composable
fun StudyPage(
    title: String,
    onBack: () -> Unit,
    scrollable: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    StudyScaffold(title = title, onBack = onBack) { insets ->
        val base = Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).imePadding()
        val scrolling = if (scrollable) base.verticalScroll(rememberScrollState()) else base
        Column(scrolling.padding(contentPadding), verticalArrangement = verticalArrangement, content = content)
    }
}
