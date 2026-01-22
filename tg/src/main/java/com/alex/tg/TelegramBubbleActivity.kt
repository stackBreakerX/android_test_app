package com.alex.tg

import android.os.Bundle
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alex.studydemo.base.BaseActivity
import com.alex.tg.chat.MessageEnterTransitionContainer
import com.alex.tg.chat.MessageItem
import com.alex.tg.chat.MessageListAdapter
import com.alex.tg.chat.MessageListItemAnimator
import com.alex.tg.chat.FlyTextEnterTransition
import com.alex.tg.databinding.ActivityMessageListBinding
import com.alex.tg.chat.MessageBubbleView

class TelegramBubbleActivity : BaseActivity<ActivityMessageListBinding>() {

    // 111
    private lateinit var adapter: MessageListAdapter
    private lateinit var enterContainer: MessageEnterTransitionContainer
    private var pendingSendText: String? = null

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityMessageListBinding =
        ActivityMessageListBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        title = "Telegram 气泡动画演示"

        enterContainer = MessageEnterTransitionContainer(this)
        val contentContainer = findViewById<ViewGroup>(com.alex.lib.R.id.content_container)
        contentContainer.addView(
            enterContainer,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        adapter = MessageListAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false).apply {
            stackFromEnd = true
        }
        binding.recyclerView.adapter = adapter
        binding.recyclerView.itemAnimator = MessageListItemAnimator(enterContainer)
        adapter.registerAdapterDataObserver(object : androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
                binding.recyclerView.post {
                    val pos = adapter.itemCount - 1
                    val vh = binding.recyclerView.findViewHolderForAdapterPosition(pos)
                    val bubble = vh?.itemView?.findViewById<MessageBubbleView>(com.alex.tg.R.id.bubble)
                    val sendText = pendingSendText
                    if (bubble != null && sendText != null && sendText.isNotEmpty()) {
                        val transition = FlyTextEnterTransition(binding.inputEdit, bubble, sendText)
                        enterContainer.addTransition(transition)
                        pendingSendText = null
                    }
                }
            }
        })

        adapter.submitInitial(
            listOf(
                MessageItem.Text("这是一个仿 Telegram 的消息气泡动画页面"),
                MessageItem.Text("底部输入后点击“发送”查看进入动画与气泡涟漪"),
                MessageItem.Text("点击气泡可触发选择涟漪效果")
            )
        )

        binding.sendButton.setOnClickListener {
            val text = binding.inputEdit.text?.toString().orEmpty().ifBlank { "空消息" }
            binding.inputEdit.setText("")
            pendingSendText = text
            adapter.addMessage(MessageItem.Text(text))
            binding.recyclerView.post {
                binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
            }
        }
    }
}

