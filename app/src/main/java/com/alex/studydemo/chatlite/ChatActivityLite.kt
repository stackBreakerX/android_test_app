package com.alex.studydemo.chatlite

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alex.studydemo.base.BaseActivity
import com.alex.studydemo.databinding.ActivityChatLiteBinding
import com.alex.tg.chat.FlyTextEnterTransition
import com.alex.tg.chat.MessageEnterTransitionContainer
import com.alex.tg.chat.MessageListItemAnimator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ChatActivityLite : BaseActivity<ActivityChatLiteBinding>() {

    private lateinit var adapter: ChatLiteAdapter
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var enterContainer: MessageEnterTransitionContainer
    private val items = mutableListOf<ChatLiteMessage>()
    private var nextId = 1L
    private var pendingSendMessageId: Long? = null

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityChatLiteBinding =
        ActivityChatLiteBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        title = "ChatActivity Lite"
        setupEnterContainer()
        setupRecycler()
        setupInputBar()
        seedMessages()
    }

    private fun setupEnterContainer() {
        // 覆盖层用于执行“输入框飞入消息”过渡动画，结构对齐 ChatActivity。
        enterContainer = MessageEnterTransitionContainer(this)
        val contentContainer = findViewById<ViewGroup>(com.alex.lib.R.id.content_container)
        contentContainer.addView(
            enterContainer,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    private fun setupRecycler() {
        layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false).apply {
            stackFromEnd = true
        }
        adapter = ChatLiteAdapter()
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = adapter
        binding.recyclerView.itemAnimator = MessageListItemAnimator(enterContainer)
    }

    private fun setupInputBar() {
        binding.sendButton.setOnClickListener {
            val text = binding.inputEdit.text?.toString().orEmpty().ifBlank { "空消息" }
            binding.inputEdit.setText("")
            sendMessage(text)
        }
    }

    private fun seedMessages() {
        val seed = listOf(
            "这个页面基于 ChatActivity 的结构做简化",
            "只保留文本消息与列表逻辑",
            "点击发送可查看飞入动画"
        )
        seed.forEachIndexed { index, text ->
            items.add(
                ChatLiteMessage(
                    id = nextId++,
                    text = text,
                    fromMe = index % 2 == 0,
                    time = "09:4${index}"
                )
            )
        }
        submitListAndScrollToEnd()
    }

    private fun sendMessage(text: String) {
        val message = ChatLiteMessage(
            id = nextId++,
            text = text,
            fromMe = true,
            time = formatTime(nextId)
        )
        items.add(message)
        pendingSendMessageId = message.id
        submitListAndScrollToEnd()
    }

    private fun submitListAndScrollToEnd() {
        adapter.submitList(items.toList()) {
            binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
            binding.recyclerView.post { runSendTransitionIfNeeded() }
        }
    }

    private fun runSendTransitionIfNeeded() {
        val id = pendingSendMessageId ?: return
        val index = adapter.currentList.indexOfFirst { it.id == id }
        if (index < 0) {
            pendingSendMessageId = null
            return
        }
        val vh = binding.recyclerView.findViewHolderForAdapterPosition(index)
        val bubble = vh?.itemView?.findViewById<android.view.View>(com.alex.studydemo.R.id.bubbleContainer)
        val item = adapter.currentList.getOrNull(index)
        if (bubble != null && item != null) {
            val transition = FlyTextEnterTransition(binding.inputEdit, bubble, item.text)
            enterContainer.addTransition(transition)
            pendingSendMessageId = null
        } else {
            // 等待布局完成后再触发动画
            binding.recyclerView.post { runSendTransitionIfNeeded() }
        }
    }

    private fun formatTime(seed: Long): String {
        val minute = (seed % 60).toInt().toString().padStart(2, '0')
        val hour = ((seed / 60) % 24).toInt().toString().padStart(2, '0')
        return "$hour:$minute"
    }

    companion object {
        fun newInstance(context: Context) {
            context.startActivity(Intent(context, ChatActivityLite::class.java))
        }
    }
}

