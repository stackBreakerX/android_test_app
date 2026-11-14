package com.alex.studydemo.chat_tg

import com.alex.studydemo.ui.StudyScaffold
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 原发送菜单的九个选项；批量操作只发送无需系统选择器的八个样本。 */
private val demoOptions = listOf(
    "全部元素（用户名+引用+正文+翻译+点赞）", "纯文本", "用户名 + 正文", "用户名 + 引用 + 正文",
    "用户名 + 正文 + 翻译", "用户名 + 引用 + 正文 + 翻译 + 点赞", "图片消息（相册，可选 2 张）", "视频消息", "文件消息"
)

/**
 * 聊天页面仅接收消息快照和事件，不持有 Activity 或旧 View。
 * 列表以消息编号为键，追加才滚动到底部；编辑同一条消息不会把用户从历史位置拉走。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TgComposeChat(
    messages: List<TgMessageItem>, draft: String, editing: Boolean, reply: String?, error: String?,
    onBack: () -> Unit, onDraft: (String) -> Unit, onSend: () -> Unit, onCancel: () -> Unit,
    onEdit: (TgMessageItem.Text) -> Unit, onReply: (TgMessageItem.Text) -> Unit,
    onDemo: (Int) -> Unit, onBatch: () -> Unit, onEditLast: () -> Unit, onDismissError: () -> Unit
) {
    var demoMenu by rememberSaveable { mutableStateOf(false) }
    var actions by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var fontSize by rememberSaveable { mutableStateOf(TgSharedConfig.fontSize.toFloat()) }
    var radius by rememberSaveable { mutableStateOf(TgSharedConfig.bubbleRadius.toFloat()) }
    var showReply by rememberSaveable { mutableStateOf(true) }
    var showTranslation by rememberSaveable { mutableStateOf(true) }
    var showNames by rememberSaveable { mutableStateOf(true) }
    var showReactions by rememberSaveable { mutableStateOf(true) }
    // 首项是历史标题，因此最后一条消息的列表索引等于消息数量。
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = messages.size)
    val keyboard = LocalSoftwareKeyboardController.current
    val inputFocus = remember { FocusRequester() }
    val initialLastId = remember { messages.maxOfOrNull { it.id } ?: 0L }
    var inputOrigin by remember { mutableStateOf(Offset.Zero) }
    // 从气泡进入编辑或回复时主动聚焦，键盘弹出后仍由 Scaffold 为输入区避让。
    LaunchedEffect(editing, reply) {
        if (editing || reply != null) {
            inputFocus.requestFocus()
            keyboard?.show()
        }
    }
    // 协程归属于组合生命周期；退出页面或下一次追加时取消旧滚动，避免并发滚动抢占。
    LaunchedEffect(messages.lastOrNull()?.id) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size)
    }
    val dark = com.alex.studydemo.ui.StudyDarkTheme()
    com.alex.studydemo.ui.StudySystemBars(dark)
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme(
        primary = Color(0xFF287A9C), secondaryContainer = Color(0xFFD8F0DF), background = Color(0xFFEAF0EC)
    )) {
        StudyScaffold(
            title = "TG 聊天", subtitle = "${messages.size} 条消息 · Compose", onBack = onBack,
            actions = {
                        Box {
                            TextButton(onClick = { actions = true }) { Text("选项") }
                            DropdownMenu(expanded = actions, onDismissRequest = { actions = false }) {
                                DropdownMenuItem(text = { Text("文本消息编辑功能") }, onClick = { actions = false; onEditLast() })
                                DropdownMenuItem(text = { Text("批量发送 8 类示例") }, onClick = { actions = false; onBatch() })
                                DropdownMenuItem(text = { Text("聊天显示设置") }, onClick = { actions = false; settings = true })
                            }
                        }

            },
            bottomBar = {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 8.dp, vertical = 6.dp)) {
                        if (editing || reply != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (editing) "正在编辑消息" else "回复：$reply", Modifier.weight(1f), maxLines = 2, style = MaterialTheme.typography.labelMedium)
                                TextButton(onClick = onCancel) { Text("取消") }
                            }
                        }
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { demoMenu = true }, modifier = Modifier.heightIn(min = 52.dp)) { Text("添加") }
                            OutlinedTextField(
                                value = draft, onValueChange = onDraft, modifier = Modifier.weight(1f).focusRequester(inputFocus).onGloballyPositioned { inputOrigin = it.positionInRoot() },
                                placeholder = { Text("输入消息") }, maxLines = 6,
                                shape = RoundedCornerShape(24.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { onSend() })
                            )
                            FilledTonalButton(onClick = onSend, modifier = Modifier.heightIn(min = 52.dp)) { Text(if (editing) "保存" else "发送") }
                        }
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                state = listState, modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom)
            ) {
                item(key = "history") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("学习会话 · 本地演示", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(messages, key = { it.id }, contentType = { it::class.simpleName }) { message ->
                    TgComposeBubble(message, fontSize, radius, showNames, showReply, showTranslation, showReactions,
                        onEdit = onEdit, onReply = onReply, inputOrigin = inputOrigin, animateSend = message.id > initialLastId)
                }
            }
        }
        if (demoMenu) AlertDialog(
            onDismissRequest = { demoMenu = false }, title = { Text("选择要发送的内容") },
            text = { LazyColumn { items(demoOptions.size) { index ->
                TextButton(onClick = { demoMenu = false; onDemo(index) }, modifier = Modifier.fillMaxWidth()) { Text(demoOptions[index], Modifier.fillMaxWidth()) }
            } } }, confirmButton = { TextButton(onClick = { demoMenu = false }) { Text("关闭") } }
        )
        if (settings) AlertDialog(
            onDismissRequest = { settings = false }, title = { Text("聊天显示设置") },
            text = {
                Column {
                    Text("字体大小：${fontSize.toInt()} sp")
                    Slider(value = fontSize, onValueChange = { fontSize = it; TgSharedConfig.fontSize = it.toInt() }, valueRange = 12f..24f, steps = 11)
                    Text("气泡圆角：${radius.toInt()} dp")
                    Slider(value = radius, onValueChange = { radius = it; TgSharedConfig.bubbleRadius = it.toInt() }, valueRange = 0f..28f)
                    TgSettingToggle("显示用户名", showNames) { showNames = it }
                    TgSettingToggle("显示引用", showReply) { showReply = it }
                    TgSettingToggle("显示翻译", showTranslation) { showTranslation = it }
                    TgSettingToggle("显示反应", showReactions) { showReactions = it }
                }
            }, confirmButton = { TextButton(onClick = { settings = false }) { Text("完成") } }
        )
        if (error != null) AlertDialog(onDismissRequest = onDismissError, title = { Text("相册不可用") }, text = { Text(error) }, confirmButton = { TextButton(onClick = onDismissError) { Text("关闭") } })
    }
}

/** 设置只影响可见内容，不删除消息上的引用、翻译或反应，重新开启即可恢复显示。 */
@Composable
private fun TgSettingToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** 四类气泡共用方向、时间与已发送标识；点击文本提供编辑/回复，媒体点击显示预览或数据详情。 */
@Composable
private fun TgComposeBubble(
    item: TgMessageItem, fontSize: Float, radius: Float, showNames: Boolean, showReply: Boolean,
    showTranslation: Boolean, showReactions: Boolean, onEdit: (TgMessageItem.Text) -> Unit,
    onReply: (TgMessageItem.Text) -> Unit, inputOrigin: Offset, animateSend: Boolean
) {
    val (fromMe, time) = when (item) {
        is TgMessageItem.Text -> item.fromMe to item.time
        is TgMessageItem.Image -> item.fromMe to item.time
        is TgMessageItem.Video -> item.fromMe to item.time
        is TgMessageItem.File -> item.fromMe to item.time
    }
    var menu by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var previewIndex by rememberSaveable(item.id) { mutableStateOf(-1) }
    var translationExpanded by rememberSaveable(item.id) { mutableStateOf(true) }
    var quoteExpanded by rememberSaveable(item.id) { mutableStateOf(true) }
    var liked by rememberSaveable(item.id) { mutableStateOf(false) }
    // 新发出的文本从输入位置飞入；动画完成标记可恢复，滚回历史不会重复播放。
    var entered by rememberSaveable(item.id) { mutableStateOf(!animateSend || !fromMe || item !is TgMessageItem.Text) }
    val entry = remember(item.id) { Animatable(if (entered) 1f else 0f) }
    var bubbleOrigin by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(bubbleOrigin != null) {
        if (!entered && bubbleOrigin != null) {
            entry.animateTo(1f, tween(380))
            entered = true
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (fromMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom) {
        if (!fromMe) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Text("R", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(6.dp))
        }
        Box(Modifier.widthIn(max = 310.dp).weight(1f, fill = false)) {
            Surface(
                shape = RoundedCornerShape(topStart = radius.dp, topEnd = radius.dp, bottomStart = if (fromMe) radius.dp else 3.dp, bottomEnd = if (fromMe) 3.dp else radius.dp),
                color = if (fromMe) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.onGloballyPositioned { bubbleOrigin = it.positionInRoot() }.graphicsLayer {
                    val target = bubbleOrigin ?: inputOrigin
                    val remaining = 1f - entry.value
                    translationX = (inputOrigin.x - target.x) * remaining
                    translationY = (inputOrigin.y - target.y) * remaining
                    alpha = entry.value
                    scaleX = .94f + .06f * entry.value
                    scaleY = scaleX
                }.animateContentSize().clickable { if (item is TgMessageItem.Text) menu = true else if (item is TgMessageItem.Image) previewIndex = 0 else details = true }
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    when (item) {
                        is TgMessageItem.Text -> {
                            if (showNames && !item.userName.isNullOrBlank()) Text(item.userName, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            if (showReply && !item.quote.isNullOrBlank()) {
                                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = .09f), shape = RoundedCornerShape(6.dp), modifier = Modifier.clickable { quoteExpanded = !quoteExpanded }) {
                                    Column(Modifier.padding(8.dp)) {
                                        Text(if (quoteExpanded) "引用 ▾" else "引用 ▸", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                        if (quoteExpanded) Text(item.quote, fontSize = 14.sp)
                                    }
                                }
                            }
                            Text(item.text, fontSize = fontSize.sp)
                            if (showTranslation && !item.translation.isNullOrBlank()) {
                                TextButton(onClick = { translationExpanded = !translationExpanded }, contentPadding = PaddingValues(0.dp)) { Text(if (translationExpanded) "收起翻译" else "显示翻译") }
                                if (translationExpanded) Text(item.translation, fontSize = (fontSize - 1).sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (showReactions && !item.reactions.isNullOrBlank()) {
                                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .12f), modifier = Modifier.clickable { liked = !liked }) {
                                    Text(item.reactions + if (liked) "  +1" else "", Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                                }
                            }
                        }
                        is TgMessageItem.Image -> {
                            val dual = item.albumDual || item.secondImageUri != null
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                TgComposePhoto(item.imageUri, Modifier.weight(1f), onClick = { previewIndex = 0 })
                                if (dual) TgComposePhoto(item.secondImageUri, Modifier.weight(1f), onClick = { previewIndex = 1 })
                            }
                        }
                        is TgMessageItem.Video -> Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("▶", fontSize = 32.sp); Text("视频消息示例", style = MaterialTheme.typography.labelMedium) }
                        }
                        is TgMessageItem.File -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("▤", fontSize = 32.sp, color = MaterialTheme.colorScheme.primary)
                            Column { Text(item.name, fontWeight = FontWeight.Medium); Text(item.size, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    Text(time + if (fromMe) "  ✓✓" else "", Modifier.align(Alignment.End), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (item is TgMessageItem.Text) DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("编辑消息") }, onClick = { menu = false; onEdit(item) })
                DropdownMenuItem(text = { Text("回复消息") }, onClick = { menu = false; onReply(item) })
                if (!item.quote.isNullOrBlank()) DropdownMenuItem(text = { Text(if (quoteExpanded) "收起引用" else "展开引用") }, onClick = { menu = false; quoteExpanded = !quoteExpanded })
                if (!item.translation.isNullOrBlank()) DropdownMenuItem(text = { Text(if (translationExpanded) "收起翻译" else "显示翻译") }, onClick = { menu = false; translationExpanded = !translationExpanded })
            }
        }
    }
    if (item is TgMessageItem.Image && previewIndex >= 0) Dialog(
        onDismissRequest = { previewIndex = -1 }, properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            StudyScaffold("相册预览", containerColor = Color.Black, barColor = Color.Black, barContentColor = Color.White,
                actions = { TextButton(onClick = { previewIndex = -1 }) { Text("关闭", color = Color.White) } }) { insets ->
                Column(Modifier.fillMaxSize().padding(insets).padding(16.dp)) {
                TgComposePhoto(if (previewIndex == 0) item.imageUri else item.secondImageUri, Modifier.weight(1f).fillMaxWidth(), preview = true)
                if (item.albumDual || item.secondImageUri != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = { previewIndex = 0 }) { Text("第 1 张", color = Color.White) }
                    TextButton(onClick = { previewIndex = 1 }) { Text("第 2 张", color = Color.White) }
                }

                }
            }
        }
    }
    if (details) AlertDialog(onDismissRequest = { details = false },
        title = { Text(if (item is TgMessageItem.File) "文件详情" else "视频详情") },
        text = { Text(if (item is TgMessageItem.File) "${item.name}\n${item.size}\n此消息是文件展示样本，没有关联可打开的本地文件。" else "此消息是视频展示样本，没有关联可播放的视频源。") },
        confirmButton = { TextButton(onClick = { details = false }) { Text("关闭") } })
}

/**
 * 图片在 IO 线程按尺寸采样读取，不把原始大图留在主线程；组合销毁后不再提交加载结果。
 * 空 URI 是原有媒体样本，读取失败与加载中分别展示，用户可以点击重试；预览保持完整比例。
 */
@Composable
private fun TgComposePhoto(uri: Uri?, modifier: Modifier = Modifier, preview: Boolean = false, onClick: () -> Unit = {}) {
    val resolver = LocalContext.current.applicationContext.contentResolver
    var retry by remember(uri) { mutableIntStateOf(0) }
    var loading by remember(uri, retry) { mutableStateOf(uri != null) }
    val bitmap by produceState<Bitmap?>(initialValue = null, uri, retry) {
        value = null
        if (uri != null) {
            value = withContext(Dispatchers.IO) {
                try {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    val options = BitmapFactory.Options().apply {
                        var sample = 1
                        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
                        inSampleSize = sample
                    }
                    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                } catch (_: java.io.IOException) {
                    null
                } catch (_: SecurityException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                } catch (_: OutOfMemoryError) {
                    // 解码内存不足时保留消息并显示重试入口，避免整页崩溃。
                    null
                }
            }
        }
        loading = false
    }
    val image = bitmap
    val ratio = if (image != null) (image.width.toFloat() / image.height).coerceIn(.75f, 1.78f) else 1f
    Box(modifier.then(if (preview) Modifier else Modifier.aspectRatio(ratio)).clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        when {
            image != null -> Image(image.asImageBitmap(), contentDescription = "相册图片", modifier = Modifier.fillMaxSize(), contentScale = if (preview) ContentScale.Fit else ContentScale.Crop)
            loading -> CircularProgressIndicator(Modifier.size(28.dp))
            uri == null -> Text("图片展示示例", style = MaterialTheme.typography.labelMedium)
            else -> TextButton(onClick = { retry++ }) { Text("图片读取失败，点击重试") }
        }
    }
}
