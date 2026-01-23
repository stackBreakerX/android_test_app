package com.alex.studydemo.chat_tg

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alex.studydemo.base.BaseActivity
import com.alex.studydemo.databinding.ActivityTgTextChatBinding

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

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityTgTextChatBinding =
        ActivityTgTextChatBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        // 设置标题并初始化页面结构
        title = "TG 文本消息"
        setupRecycler()
        setupInput()
        seedMessages()
    }

    private fun setupRecycler() {
        // 创建适配器并配置列表滚动方向为自底向上（最新消息在底部）
        adapter = TgTextMessageAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false).apply {
            stackFromEnd = true
        }
        binding.recyclerView.adapter = adapter
        // 使用 TG 风格的 ItemAnimator，统一入场/移动节奏
        binding.recyclerView.itemAnimator = TgTextItemAnimator()
    }

    private fun setupInput() {
        // 发送按钮点击：读取输入框文本，清空输入，并追加一条“我发送”的文本消息
        binding.sendButton.setOnClickListener {
            val text = binding.inputEdit.text?.toString().orEmpty().let { if (it.isBlank()) "空消息" else it }
            binding.inputEdit.setText("")
            addMessage(text, fromMe = true)
        }
    }

    private fun seedMessages() {
        // 预置若干条不同类型的消息，便于演示多种气泡与时间布局
        items.add(
            TgMessageItem.Text(
                id = nextId++,
                text = "这个页面只保留 TG 文本消息的渲染逻辑",
                fromMe = false,
                time = "09:41",
                quote = "Ralph Edwards",
                translation = "这是翻译文本的示例",
                reactions = "👍 3  ❤️ 1"
            )
        )
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
        items.add(
            TgMessageItem.Text(
                id = nextId++,
                text = "发送消息在右侧显示",
                fromMe = true,
                time = "09:45"
            )
        )
        // 使用 ListAdapter 的 submitList 提交不可变列表，触发 Diff 刷新
        adapter.submitList(items.toList())
        // 保持滚动在最新消息位置
        binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
    }

    private fun addMessage(text: String, fromMe: Boolean) {
        // 追加一条文本消息并触发列表刷新与滚动
        items.add(
            TgMessageItem.Text(
                id = nextId++,
                text = text,
                fromMe = fromMe,
                time = formatTime(nextId)
            )
        )
        adapter.submitList(items.toList()) {
            binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
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
