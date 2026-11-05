package com.alex.studydemo.module_compose.swipe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 仿 iPhone「信息」会话列表的左右侧滑按钮演示。
 * 侧滑组件与 iOS 实现难度对比见 [SwipeActionRow] 的 KDoc。
 */
class SwipeActionsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MessagesScreen(onBack = ::finish)
            }
        }
    }
}

private data class Conversation(
    val id: Long,
    val name: String,
    val preview: String,
    val time: String,
    val avatarColor: Color,
    val unread: Boolean = false,
    val pinned: Boolean = false,
    val muted: Boolean = false,
)

private object IosColors {
    val Blue = Color(0xFF007AFF)
    val Yellow = Color(0xFFFF9F0A)
    val Red = Color(0xFFFF3B30)
    val Purple = Color(0xFF5856D6)
    val SecondaryText = Color(0xFF8E8E93)
    val Separator = Color(0xFFE5E5EA)
}

private fun sampleConversations(): List<Conversation> {
    val names = listOf(
        "妈妈", "张三", "李四", "王五", "产品群", "快递", "银行", "Alex",
        "设计组", "老同学", "房东", "健身教练", "前端小组", "客服", "赵六",
        "钱七", "孙八", "周九", "吴十", "郑十一",
    )
    val previews = listOf(
        "晚上回家吃饭吗？", "明天的会议改到下午三点", "文件已经发你邮箱了",
        "好的，收到 👍", "新版本设计稿已更新", "您的快递已到驿站，取件码 3-2-1008",
        "您尾号 1234 的账户入账 100.00 元", "周末一起打球？",
    )
    val colors = listOf(
        Color(0xFF34C759), Color(0xFF007AFF), Color(0xFFFF9500), Color(0xFFAF52DE),
        Color(0xFFFF2D55), Color(0xFF5AC8FA), Color(0xFF8E8E93),
    )
    return names.mapIndexed { i, name ->
        Conversation(
            id = i.toLong(),
            name = name,
            preview = previews[i % previews.size],
            time = if (i < 4) "${10 + i}:2$i" else "昨天",
            avatarColor = colors[i % colors.size],
            unread = i % 3 == 0,
            pinned = i == 0,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessagesScreen(onBack: () -> Unit) {
    val conversations = remember { mutableStateListOf(*sampleConversations().toTypedArray()) }
    // 当前打开的行；同一时间只允许一行处于打开状态
    var openId by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()

    // 列表开始纵向滚动时关闭打开的行（iOS 系统行为）
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) openId = null
    }

    fun update(id: Long, transform: (Conversation) -> Conversation) {
        val index = conversations.indexOfFirst { it.id == id }
        if (index >= 0) conversations[index] = transform(conversations[index])
    }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = { Text("信息", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("‹ 返回", color = IosColors.Blue) }
                },
            )
        },
    ) { padding ->
        // 置顶会话排在前面；animateItem 让置顶 / 取消置顶时位置变化有动画
        val sorted = conversations.sortedByDescending { it.pinned }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            items(sorted, key = { it.id }) { item ->
                // 删除：先收缩行高并淡出，动画结束后再从数据中移除
                val visibleState = remember { MutableTransitionState(true) }
                LaunchedEffect(visibleState.isIdle, visibleState.currentState) {
                    if (visibleState.isIdle && !visibleState.currentState) {
                        conversations.removeAll { it.id == item.id }
                        if (openId == item.id) openId = null
                    }
                }
                AnimatedVisibility(
                    visibleState = visibleState,
                    exit = shrinkVertically() + fadeOut(),
                    modifier = Modifier.animateItem(),
                ) {
                    SwipeActionRow(
                        isOpen = openId == item.id,
                        onOpenChange = { open ->
                            if (open) openId = item.id else if (openId == item.id) openId = null
                        },
                        leading = listOf(
                            SwipeAction(
                                label = if (item.unread) "已读" else "未读",
                                icon = "💬",
                                background = IosColors.Blue,
                            ) { update(item.id) { it.copy(unread = !it.unread) } },
                            SwipeAction(
                                label = if (item.pinned) "取消置顶" else "置顶",
                                icon = "📌",
                                background = IosColors.Yellow,
                            ) { update(item.id) { it.copy(pinned = !it.pinned) } },
                        ),
                        trailing = listOf(
                            SwipeAction(
                                label = "删除",
                                icon = "🗑",
                                background = IosColors.Red,
                                destructive = true,
                            ) { visibleState.targetState = false },
                            SwipeAction(
                                label = if (item.muted) "显示提醒" else "隐藏提醒",
                                icon = "🔕",
                                background = IosColors.Purple,
                            ) { update(item.id) { it.copy(muted = !it.muted) } },
                        ),
                    ) {
                        ConversationRow(
                            item = item,
                            // iOS：有行打开时，点击任意行只负责关闭
                            onClick = { if (openId != null) openId = null },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(item: Conversation, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 未读蓝点
            Box(modifier = Modifier.width(16.dp), contentAlignment = Alignment.Center) {
                if (item.unread) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(IosColors.Blue),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(item.avatarColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(item.name.take(1), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = (if (item.pinned) "📌 " else "") + item.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.muted) Text("🔕 ", fontSize = 12.sp)
                    Text(item.time, fontSize = 14.sp, color = IosColors.SecondaryText)
                }
                Text(
                    text = item.preview,
                    fontSize = 14.sp,
                    color = IosColors.SecondaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(start = 78.dp), color = IosColors.Separator)
    }
}
