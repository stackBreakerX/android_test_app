package com.alex.studydemo.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Tab 的文案和强调色集中定义，切换分类时保持一致的视觉层次。 */
private data class Workspace(val title: String, val symbol: String, val heading: String, val detail: String, val color: Color)
private val workspaces = listOf(
    Workspace("交互", "◈", "让界面动起来", "从一条消息开始，探索布局、手势与动画。", Color(0xFF83E1CC)),
    Workspace("数据", "⇄", "让数据流动起来", "连接协程、数据库和网络，观察每一次状态变化。", Color(0xFFAABEFF)),
    Workspace("多媒体", "▧", "从像素到声音", "选择素材、调整参数，比较不同处理方式的结果。", Color(0xFFFFD091)),
    Workspace("工程", "⌘", "理解应用的内部", "观察帧耗时，实践路由和 Kotlin 语言能力。", Color(0xFFD3B3FF)),
)

/** 工作台持久化收藏、最近访问和主题；旋转时保留当前 Tab、筛选和搜索输入。 */
@Composable
fun LearningApp() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("learning_workspace", 0) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var topic by rememberSaveable { mutableStateOf("全部") }
    var onlySaved by rememberSaveable { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var theme by remember { mutableStateOf(preferences.getString("theme", "system") ?: "system") }
    var favorites by remember { mutableStateOf(preferences.getStringSet("favorites", emptySet())!!.toSet()) }
    var recent by remember { mutableStateOf(preferences.getString("recent", "") ?: "") }
    val dark = when (theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val workspace = workspaces[selected]
    val tabStates = rememberSaveableStateHolder()
    // 搜索跨专题，Tab 仍约束方向；仅收藏不会改变已有收藏集合。
    val entries = learningEntries.filter { it.tab == selected && (topic == "全部" || it.topic == topic) &&
        (!onlySaved || it.target in favorites) && (it.title + it.summary + it.topic).contains(query, ignoreCase = true) }
    val open: (LearningEntry) -> Unit = { entry ->
        try {
            context.startActivity(Intent().setClassName(context, "com.alex.studydemo.${entry.target}"))
            recent = entry.target
            preferences.edit().putString("recent", recent).apply()
        } catch (_: android.content.ActivityNotFoundException) {
            // 缺失入口时保留工作台，避免一次导航错误导致整个应用退出。
            Toast.makeText(context, "此实验暂时无法打开", Toast.LENGTH_SHORT).show()
        }
    }
    BackHandler(selected != 0 || query.isNotEmpty() || topic != "全部" || onlySaved) {
        query = ""; topic = "全部"; onlySaved = false; selected = 0
    }
    StudyTheme(dark) {
        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    workspaces.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selected == index,
                            onClick = { selected = index; topic = "全部"; query = "" },
                            icon = { Text(tab.symbol, fontSize = 24.sp) }, label = { Text(tab.title) },
                        )
                    }
                }
            },
        ) { insets ->
            // 每个工作台拥有独立滚动位置，切换 Tab 后回到上次浏览的区域。
            tabStates.SaveableStateProvider(selected) {
                Column(
                    Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("STUDY / ANDROID", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, letterSpacing = 2.sp)
                            Text("学习工作台", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { settings = true }) { Text("设置") }
                    }
                    Surface(shape = RoundedCornerShape(28.dp), color = Color(0xFF142D35)) {
                        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF142D35), Color(0xFF264C58)))).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("0${selected + 1}  /  ${workspace.title}实验室", color = workspace.color, style = MaterialTheme.typography.labelLarge)
                            Text(workspace.heading, color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                            Text(workspace.detail, color = Color(0xFFD1DFE1), style = MaterialTheme.typography.bodyMedium)
                            Text("${learningEntries.count { it.tab == selected }} 个实验   ·   动手实践，观察结果", color = workspace.color, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    learningEntries.firstOrNull { it.target == recent && it.tab == selected }?.let { last ->
                        OutlinedCard(onClick = { open(last) }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("继续探索", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                    Text(last.title, fontWeight = FontWeight.SemiBold)
                                }
                                Text("打开 →", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("搜索${workspace.title}实验") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                        trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("清除") } })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("探索专题", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        FilterChip(selected = onlySaved, onClick = { onlySaved = !onlySaved }, label = { Text("我的收藏") })
                    }
                    // 自适应换行让专题筛选在窄屏和大字体下仍可访问。
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (listOf("全部") + learningEntries.filter { it.tab == selected }.map { it.topic }.distinct()).forEach { label ->
                            FilterChip(selected = topic == label, onClick = { topic = label }, label = { Text(label) })
                        }
                    }
                    if (entries.isEmpty()) {
                        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                            Column(Modifier.padding(24.dp)) {
                                Text(if (onlySaved) "还没有匹配的收藏" else "没有找到相关实验", fontWeight = FontWeight.Bold)
                                Text("试试其他关键词或专题。收藏卡片后可在这里快速找到。", Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                    entries.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                            pair.forEach { entry ->
                                ExperimentCard(entry, entry.target in favorites, Modifier.weight(1f).fillMaxHeight(), { open(entry) }) {
                                    // 创建新集合触发重组，并复制到偏好存储，避免修改系统返回的集合。
                                    favorites = if (entry.target in favorites) favorites - entry.target else favorites + entry.target
                                    preferences.edit().putStringSet("favorites", favorites).apply()
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    Text("学会一个技术点，也把它放进一个完整的应用。", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 24.dp))
                }
            }
        }
        if (settings) AlertDialog(onDismissRequest = { settings = false }, title = { Text("工作台设置") }, text = {
            Column {
                Text("外观", fontWeight = FontWeight.Bold)
                listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (value, label) ->
                    // 整行共享单选语义，点击文字和圆点都更新外观，读屏也只呈现一个选项。
                    Row(Modifier.fillMaxWidth().selectable(selected = theme == value, role = Role.RadioButton,
                        onClick = { theme = value; preferences.edit().putString("theme", value).apply() }),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = theme == value, onClick = null)
                        Text(label)
                    }
                }
                Text("StudyDemo · Android 学习实验室\n${learningEntries.size} 个实验 · Compose 界面", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(onClick = { settings = false }) { Text("完成") } })
    }
}

/** 卡片点击进入实验，独立收藏按钮具有明确语义，不触发父卡片的导航。 */
@Composable
private fun ExperimentCard(entry: LearningEntry, saved: Boolean, modifier: Modifier, onOpen: () -> Unit, onSave: () -> Unit) {
    Card(onClick = onOpen, modifier = modifier, shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(entry.topic, Modifier.weight(1f), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = onSave, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(40.dp)) {
                    Text(if (saved) "★" else "☆", fontSize = 24.sp,
                        modifier = Modifier.semantics { contentDescription = if (saved) "取消收藏${entry.title}" else "收藏${entry.title}" })
                }
            }
            Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(entry.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("进入实验  ↗", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        }
    }
}
