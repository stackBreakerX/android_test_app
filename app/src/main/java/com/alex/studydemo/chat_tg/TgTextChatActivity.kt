package com.alex.studydemo.chat_tg

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alex.studydemo.base.BaseActivity
import com.alex.studydemo.databinding.ActivityTgTextChatBinding

class TgTextChatActivity : BaseActivity<ActivityTgTextChatBinding>() {

    private lateinit var adapter: TgTextMessageAdapter
    private val items = mutableListOf<TgMessageItem>()
    private var nextId = 1L

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityTgTextChatBinding =
        ActivityTgTextChatBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        title = "TG 文本消息"
        setupRecycler()
        setupInput()
        seedMessages()
    }

    private fun setupRecycler() {
        adapter = TgTextMessageAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false).apply {
            stackFromEnd = true
        }
        binding.recyclerView.adapter = adapter
        // 使用 TG 风格的 ItemAnimator，统一入场/移动节奏
        binding.recyclerView.itemAnimator = TgTextItemAnimator()
    }

    private fun setupInput() {
        binding.sendButton.setOnClickListener {
            val text = binding.inputEdit.text?.toString().orEmpty().ifBlank { "空消息" }
            binding.inputEdit.setText("")
            addMessage(text, fromMe = true)
        }
    }

    private fun seedMessages() {
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
        adapter.submitList(items.toList())
        binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
    }

    private fun addMessage(text: String, fromMe: Boolean) {
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
        val minute = (seed % 60).toInt().toString().padStart(2, '0')
        val hour = ((seed / 60) % 24).toInt().toString().padStart(2, '0')
        return "$hour:$minute"
    }

    companion object {
        fun newInstance(context: Context) {
            context.startActivity(Intent(context, TgTextChatActivity::class.java))
        }
    }
}

