package com.alex.studydemo.chat_tg

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alex.studydemo.base.BaseActivity
import com.alex.studydemo.databinding.ActivityTgTextChatBinding
import java.util.concurrent.Executors

/**
 * TG 文本聊天示例页面
 * - 仅演示文本消息的渲染、输入与发送流程
 * - 使用自定义的气泡布局与时间绘制策略，贴近 Telegram 的视觉与交互
 */
class TgTextChatActivity : BaseActivity<ActivityTgTextChatBinding>() {

    /** RecyclerView 适配器（负责将不同消息类型映射为对应的 Cell） */
    private lateinit var adapter: TgTextMessageAdapter
    /** 当前消息列表（演示使用内存数据，并通过 ListAdapter 的 Diff 提交） */
    private val items = mutableListOf<TgMessageItem>()
    /** 演示用的自增 id（简化唯一性判断） */
    private var nextId = 1L
    private var recyclerWidth: Int = 0
    private val precomputeExecutor = Executors.newSingleThreadExecutor()

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityTgTextChatBinding =
        ActivityTgTextChatBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        // 设置标题并初始化页面结构
        title = "TG 文本消息"
        setupRecycler()
        setupInput()
        setupFuncTest()
        seedMessages()
    }

    private fun setupFuncTest() {
        binding.funcTestButton.setOnClickListener { view: android.view.View ->
            val popup = android.widget.PopupMenu(this, view)
            popup.menu.add("文本消息编辑功能")
            popup.setOnMenuItemClickListener { item ->
                if (item.title == "文本消息编辑功能") {
                    editLastTextMessage()
                    true
                } else {
                    false
                }
            }
            popup.show()
        }
    }

    private fun editLastTextMessage() {
        val index = items.indexOfLast { it is TgMessageItem.Text }
        if (index == -1) return
        val item = items[index] as TgMessageItem.Text

        val shortText = "编辑后的短文本"
        val longText = "这是一段编辑后的长文本消息，用来测试气泡的大小变化动画效果。Telegram 的气泡在编辑时会平滑地改变大小，而不是突变。这段文本足够长，可以触发换行和气泡尺寸的显著变化。"

        val newText = if (item.text == longText) shortText else longText
        updateMessageText(item, newText)
    }

    private fun setupRecycler() {
        // 创建适配器并配置列表滚动方向为自底向上（最新消息在底部）
        adapter = TgTextMessageAdapter { item, view ->
            showEditMenu(item, view)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false).apply {
            stackFromEnd = true
        }
        binding.recyclerView.adapter = adapter
        // 使用 TG 风格的 ItemAnimator，统一入场/移动节奏
        binding.recyclerView.itemAnimator = TgTextItemAnimator()
        // 头像装饰器：非自己消息显示头像，同一分组只显示一个
        binding.recyclerView.addItemDecoration(AvatarGroupDecoration(adapter))
        val density = resources.displayMetrics.density
        binding.recyclerView.addItemDecoration(ChatVerticalSpaceDecoration((6f * density).toInt()))
        binding.recyclerView.post {
            recyclerWidth = binding.recyclerView.width
        }
    }

    private fun showEditMenu(item: TgMessageItem.Text, view: android.view.View) {
        val popup = android.widget.PopupMenu(this, view)
        popup.menu.add("编辑消息")
        popup.setOnMenuItemClickListener { menuItem ->
            if (menuItem.title == "编辑消息") {
                showEditDialog(item)
                true
            } else {
                false
            }
        }
        popup.show()
    }

    // 记录当前正在编辑的消息
    private var editingMessageId: Long? = null

    private fun showEditDialog(item: TgMessageItem.Text) {
        editingMessageId = item.id
        // 回显内容到输入框
        binding.inputEdit.setText(item.text)
        binding.inputEdit.setSelection(item.text.length)
        // 聚焦并弹出键盘
        binding.inputEdit.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(binding.inputEdit, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        
        // 更改发送按钮文案为“编辑”
        binding.sendButton.text = "编辑"
    }

    private fun updateMessageText(item: TgMessageItem.Text, newText: String) {
        val hasExtra = !item.quote.isNullOrBlank() || !item.translation.isNullOrBlank() || !item.reactions.isNullOrBlank() || !item.userName.isNullOrBlank()
        precomputeExecutor.execute {
            val pack = TgTextLayoutPrecomputer.precompute(
                text = newText,
                time = item.time,
                fromMe = item.fromMe,
                containerWidth = recyclerWidth,
                density = resources.displayMetrics.density,
                hasExtraBlock = hasExtra,
                inlineTimeWithText = true
            )
            runOnUiThread {
                val idx = items.indexOfFirst { it is TgMessageItem.Text && it.id == item.id }
                if (idx >= 0) {
                    // 移除 TransitionManager，改用我们自己的动画系统（BaseTgMessageCell.runTransition）
                    val old = items[idx] as TgMessageItem.Text
                    val updated = old.copy(text = newText, layoutPack = pack)
                    items[idx] = updated
                    adapter.submitList(items.toList())
                }
            }
        }
    }

    private fun setupInput() {
        // 发送按钮点击：区分是发送新消息还是提交编辑
        binding.sendButton.setOnClickListener {
            val raw = binding.inputEdit.text?.toString().orEmpty()
            val sanitized = raw.replace("\r\n", " ").replace("\n", " ")
            val text = if (sanitized.isBlank()) "空消息" else sanitized
            binding.inputEdit.setText("")
            
            val editId = editingMessageId
            if (editId != null) {
                // 提交编辑
                val index = items.indexOfFirst { it.id == editId && it is TgMessageItem.Text }
                if (index >= 0) {
                    val item = items[index] as TgMessageItem.Text
                    if (text != item.text) {
                        updateMessageText(item, text)
                    }
                }
                // 重置状态
                editingMessageId = null
                binding.sendButton.text = "发送"
                // 键盘不收起，方便用户继续输入
            } else {
                // 发送新消息
                addMessage(text, fromMe = true)
            }
        }
        binding.demoAllButton.setOnClickListener {
            val options = arrayOf(
                "全部元素（用户名+引用+正文+翻译+点赞）",
                "纯文本",
                "用户名 + 正文",
                "用户名 + 引用 + 正文",
                "用户名 + 正文 + 翻译",
                "用户名 + 引用 + 正文 + 翻译 + 点赞",
                "图片消息",
                "视频消息",
                "文件消息"
            )
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("选择要发送的内容")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            val text = "演示：Cell 中所有元素"
                            items.add(makeTextItem(text, true, formatTime(nextId), "Jane Cooper", "引用内容示例", "翻译内容示例", "👍 ❤️ 🎉"))
                        }
                        1 -> {
                            items.add(makeTextItem("纯文本消息", true, formatTime(nextId), null, null, null, null))
                        }
                        2 -> {
                            items.add(makeTextItem("用户名+正文", true, formatTime(nextId), "Jane Cooper", null, null, null))
                        }
                        3 -> {
                            items.add(makeTextItem("用户名+引用+正文", true, formatTime(nextId), "Jane Cooper", "引用内容示例", null, null))
                        }
                        4 -> {
                            items.add(makeTextItem("用户名+正文+翻译", true, formatTime(nextId), "Jane Cooper", null, "翻译内容示例", null))
                        }
                        5 -> {
                            items.add(makeTextItem("用户名+引用+正文+翻译+点赞", true, formatTime(nextId), "Jane Cooper", "引用内容示例", "翻译内容示例", "👍 ❤️ 🎉"))
                        }
                        6 -> {
                            items.add(TgMessageItem.Image(id = nextId++, fromMe = true, time = formatTime(nextId)))
                        }
                        7 -> {
                            items.add(TgMessageItem.Video(id = nextId++, fromMe = true, time = formatTime(nextId)))
                        }
                        8 -> {
                            items.add(TgMessageItem.File(id = nextId++, name = "demo.txt", size = "1 KB", fromMe = true, time = formatTime(nextId)))
                        }
                    }
                    adapter.submitList(items.toList()) {
                        binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
                    }
                }
                .show()
        }
    }

    private fun seedMessages() {
        // 预置若干条不同类型的消息，便于演示多种气泡与时间布局
        items.add(makeTextItem("这个页面只保留 TG 文本消息的渲染逻辑", false, "09:41", "Ralph Edwards", "引用文本示例", "这是翻译文本的示例", "👍 ❤️ 🎉"))
        items.add(
            TgMessageItem.Image(
                id = nextId++,
                fromMe = true,
                time = "09:42"
            )
        )
        items.add(
            TgMessageItem.Video(
                id = nextId++,
                fromMe = false,
                time = "09:43"
            )
        )
        items.add(
            TgMessageItem.File(
                id = nextId++,
                name = "design_spec.pdf",
                size = "2.4 MB",
                fromMe = true,
                time = "09:44"
            )
        )
        items.add(makeTextItem("发送消息在右侧显示", true, "09:45", null, null, null, null))
        // 使用 ListAdapter 的 submitList 提交不可变列表，触发 Diff 刷新
        adapter.submitList(items.toList())
        // 保持滚动在最新消息位置
        binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
    }

    private fun addMessage(text: String, fromMe: Boolean) {
        // 追加一条文本消息并触发列表刷新与滚动
        items.add(makeTextItem(text, fromMe, formatTime(nextId), null, null, null, null))
        adapter.submitList(items.toList()) {
            binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
        }
    }

    private fun makeTextItem(
        text: String,
        fromMe: Boolean,
        time: String,
        userName: String?,
        quote: String?,
        translation: String?,
        reactions: String?
    ): TgMessageItem.Text {
        val id = nextId++
        val hasExtra = !quote.isNullOrBlank() || !translation.isNullOrBlank() || !reactions.isNullOrBlank() || !userName.isNullOrBlank()
        if (recyclerWidth > 0) {
            schedulePrecompute(id, text, fromMe, time, hasExtra)
        }
        return TgMessageItem.Text(
            id = id,
            text = text,
            fromMe = fromMe,
            time = time,
            userName = userName,
            quote = quote,
            translation = translation,
            reactions = reactions,
            layoutPack = null
        )
    }

    private fun schedulePrecompute(id: Long, text: String, fromMe: Boolean, time: String, hasExtra: Boolean) {
        val containerWidth = recyclerWidth
        val density = resources.displayMetrics.density
        precomputeExecutor.execute {
            val pack = TgTextLayoutPrecomputer.precompute(
                text = text,
                time = time,
                fromMe = fromMe,
                containerWidth = containerWidth,
                density = density,
                hasExtraBlock = hasExtra,
                inlineTimeWithText = true
            )
            runOnUiThread {
                val idx = items.indexOfFirst { it is TgMessageItem.Text && it.id == id }
                if (idx >= 0) {
                    val old = items[idx] as TgMessageItem.Text
                    val updated = old.copy(layoutPack = pack)
                    items[idx] = updated
                    adapter.submitList(items.toList())
                }
            }
        }
    }

    private fun formatTime(seed: Long): String {
        // 简化的时间格式：以 seed 计算小时与分钟，形成 HH:mm
        val minute = (seed % 60).toInt().toString().padStart(2, '0')
        val hour = ((seed / 60) % 24).toInt().toString().padStart(2, '0')
        return "$hour:$minute"
    }

    companion object {
        /** 跳转到本页面的便捷方法 */
        fun newInstance(context: Context) {
            context.startActivity(Intent(context, TgTextChatActivity::class.java))
        }
    }
}
