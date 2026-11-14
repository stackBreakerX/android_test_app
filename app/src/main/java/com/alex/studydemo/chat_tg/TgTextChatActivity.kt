package com.alex.studydemo.chat_tg

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 独立聊天学习页；消息、草稿和编辑目标由 Activity 持有，Compose 只负责呈现与事件分发。
 * 保留原消息模型供自定义 View 示例使用，本页不再依赖其布局预计算或动画线程。
 */
class TgTextChatActivity : ComponentActivity() {
    private val messages = mutableStateListOf<TgMessageItem>()
    private var nextId = 1L
    private var draft by mutableStateOf("")
    private var editingId by mutableStateOf<Long?>(null)
    private var replyingTo by mutableStateOf<String?>(null)
    private var pickerError by mutableStateOf<String?>(null)

    /** 取消选择不生成消息；结果在主线程追加，保持快速连续发送时的顺序与唯一编号。 */
    private val pickImages = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(2)) { uris ->
        if (uris.isNotEmpty()) {
            // 尽量保留恢复页面后的读取权限；部分提供器仅授予临时权限，解码层会提示读取失败。
            uris.forEach { uri ->
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) {
                    // 临时授权仍能在当前会话显示，不因无法持久化而丢弃用户选择。
                }
            }
            messages.add(TgMessageItem.Image(nextId++, true, time(), uris[0], uris.getOrNull(1)))
        }
    }

    /** 恢复状态在安装内容前完成，避免旋转后重复添加演示消息或丢失编辑目标。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState?.containsKey("tg.messages") == true) restoreMessages(savedInstanceState)
        else seedMessages()
        setContent {
            TgComposeChat(
                messages = messages,
                draft = draft,
                editing = editingId != null,
                reply = replyingTo,
                error = pickerError,
                onBack = { finish() },
                onDraft = { draft = it },
                onSend = ::send,
                onCancel = { editingId = null; replyingTo = null; draft = "" },
                onEdit = ::edit,
                onReply = { replyingTo = it.text; editingId = null },
                onDemo = ::sendDemo,
                onBatch = { (0..5).forEach(::sendDemo); sendDemo(7); sendDemo(8) },
                onEditLast = ::editLast,
                onDismissError = { pickerError = null }
            )
        }
    }

    /** 仅保存消息内容与输入状态，不保存可重新计算的 View 布局数据。 */
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelableArrayList("tg.messages", ArrayList(messages.map(::encode)))
        outState.putLong("tg.next", nextId)
        outState.putString("tg.draft", draft)
        outState.putLong("tg.edit", editingId ?: -1)
        outState.putString("tg.reply", replyingTo)
        super.onSaveInstanceState(outState)
    }

    /** 编辑与发送共享输入框；只修改目标正文，保留用户名、引用、翻译、反应和消息编号。 */
    private fun send() {
        val normalized = draft.replace("\r\n", "\n").trimEnd('\n', '\r').ifBlank { "空消息" }
        val id = editingId
        if (id != null) {
            val index = messages.indexOfFirst { it.id == id && it is TgMessageItem.Text }
            if (index >= 0) messages[index] = (messages[index] as TgMessageItem.Text).copy(text = normalized, layoutPack = null)
        } else {
            messages.add(text(normalized, quote = replyingTo))
        }
        draft = ""
        editingId = null
        replyingTo = null
    }

    /** 点击消息后进入编辑；清除引用草稿，防止保存编辑时意外生成新消息。 */
    private fun edit(item: TgMessageItem.Text) {
        editingId = item.id
        replyingTo = null
        draft = item.text
    }

    /** 保留功能测试入口的长短文本切换；不可变替换使 Compose 同时更新正文和气泡尺寸。 */
    private fun editLast() {
        val index = messages.indexOfLast { it is TgMessageItem.Text }
        if (index < 0) return
        val item = messages[index] as TgMessageItem.Text
        val longText = "这是一段编辑后的长文本消息，用来测试气泡的大小变化动画效果。Telegram 的气泡在编辑时会平滑地改变大小，而不是突变。这段文本足够长，可以触发换行和气泡尺寸的显著变化。"
        messages[index] = item.copy(text = if (item.text == longText) "编辑后的短文本" else longText, layoutPack = null)
    }

    /** 九个原有示例保持相同索引；图片选择器无法打开时显示可关闭的错误，允许再次选择。 */
    private fun sendDemo(which: Int) {
        when (which) {
            0, 5 -> messages.add(text("演示：Cell 中所有元素", user = "Jane Cooper", quote = "引用内容示例", translation = "翻译内容示例", reactions = "👍 ❤️ 🎉"))
            1 -> messages.add(text("纯文本消息"))
            2 -> messages.add(text("用户名+正文", user = "Jane Cooper"))
            3 -> messages.add(text("用户名+引用+正文", user = "Jane Cooper", quote = "引用内容示例"))
            4 -> messages.add(text("用户名+正文+翻译", user = "Jane Cooper", translation = "翻译内容示例"))
            6 -> try {
                pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } catch (_: RuntimeException) {
                pickerError = "无法打开相册，请确认设备上有可用的图片选择器后重试。"
            }
            7 -> messages.add(TgMessageItem.Video(nextId++, true, time()))
            8 -> messages.add(TgMessageItem.File(nextId++, "demo.txt", "1 KB", true, time()))
        }
    }

    /** 生成无 View 缓存的文本数据；时间沿用演示编号，不依赖网络或系统时钟。 */
    private fun text(value: String, fromMe: Boolean = true, user: String? = null, quote: String? = null,
                     translation: String? = null, reactions: String? = null, timestamp: String = time()) =
        TgMessageItem.Text(nextId++, value, fromMe, timestamp, user, quote, translation, reactions)

    /** 初始化覆盖所有消息类型和收发方向；媒体无 URI 的样本明确标为展示示例。 */
    private fun seedMessages() {
        messages.add(text("聊天支持文本、相册、视频与文件消息。点击文本可编辑或回复。", false, "Ralph Edwards", "引用文本示例", "这是翻译文本的示例", "👍 ❤️ 🎉", "09:41"))
        messages.add(TgMessageItem.Image(nextId++, true, "09:42"))
        messages.add(TgMessageItem.Image(nextId++, false, "09:42"))
        messages.add(TgMessageItem.Image(nextId++, true, "11:53", albumDual = true))
        messages.add(TgMessageItem.Image(nextId++, false, "09:43"))
        messages.add(TgMessageItem.Image(nextId++, true, "09:44"))
        messages.add(TgMessageItem.Video(nextId++, false, "09:43"))
        messages.add(TgMessageItem.File(nextId++, "design_spec.pdf", "2.4 MB", true, "09:44"))
        messages.add(text("发送消息在右侧显示", timestamp = "09:45"))
    }

    /** 编号转换成演示时分；调用者在创建消息时获取，不影响消息排序。 */
    private fun time(): String = "%02d:%02d".format((nextId / 60) % 24, nextId % 60)

    /** 使用 Bundle 保存四类消息，URI 保存字符串；旧布局缓存不参与跨实例恢复。 */
    private fun encode(item: TgMessageItem): Bundle = Bundle().apply {
        putLong("id", item.id)
        when (item) {
            is TgMessageItem.Text -> {
                putString("type", "text"); putBoolean("me", item.fromMe); putString("time", item.time)
                putString("text", item.text); putString("user", item.userName); putString("quote", item.quote)
                putString("translation", item.translation); putString("reactions", item.reactions)
            }
            is TgMessageItem.Image -> {
                putString("type", "image"); putBoolean("me", item.fromMe); putString("time", item.time)
                putString("uri", item.imageUri?.toString()); putString("uri2", item.secondImageUri?.toString())
                putBoolean("dual", item.albumDual)
            }
            is TgMessageItem.Video -> {
                putString("type", "video"); putBoolean("me", item.fromMe); putString("time", item.time)
            }
            is TgMessageItem.File -> {
                putString("type", "file"); putBoolean("me", item.fromMe); putString("time", item.time)
                putString("name", item.name); putString("size", item.size)
            }
        }
    }

    /** 未识别的数据类型跳过，编号从已恢复的最大值续接，避免 LazyColumn 键冲突。 */
    @Suppress("DEPRECATION")
    private fun restoreMessages(state: Bundle) {
        state.getParcelableArrayList<Bundle>("tg.messages").orEmpty().forEach { b ->
            val id = b.getLong("id")
            val me = b.getBoolean("me")
            val time = b.getString("time").orEmpty()
            val item = when (b.getString("type")) {
                "text" -> TgMessageItem.Text(id, b.getString("text").orEmpty(), me, time, b.getString("user"), b.getString("quote"), b.getString("translation"), b.getString("reactions"))
                "image" -> TgMessageItem.Image(id, me, time, b.getString("uri")?.let(Uri::parse), b.getString("uri2")?.let(Uri::parse), b.getBoolean("dual"))
                "video" -> TgMessageItem.Video(id, me, time)
                "file" -> TgMessageItem.File(id, b.getString("name").orEmpty(), b.getString("size").orEmpty(), me, time)
                else -> null
            }
            if (item != null) messages.add(item)
        }
        nextId = maxOf(state.getLong("tg.next", 1), (messages.maxOfOrNull { it.id } ?: 0) + 1)
        draft = state.getString("tg.draft").orEmpty()
        editingId = state.getLong("tg.edit", -1).takeIf { id -> messages.any { it.id == id && it is TgMessageItem.Text } }
        replyingTo = state.getString("tg.reply")
    }

    companion object {
        /** 保留现有启动 API；非 Activity 的 Context 需要新任务标记。 */
        fun newInstance(context: Context) {
            context.startActivity(Intent(context, TgTextChatActivity::class.java).apply {
                if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
