package com.alex.tg

import android.os.Bundle
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alex.studydemo.base.BaseActivity
import com.alex.tg.chat.MessageEnterTransitionContainer
import com.alex.tg.chat.MessageListAdapter
import com.alex.tg.chat.MessageListItemAnimator
import com.alex.tg.databinding.ActivityMessageListBinding
import com.alex.tg.chat.model.MessageModel

class TelegramBubbleActivity : BaseActivity<ActivityMessageListBinding>() {

    // 111
    private lateinit var adapter: MessageListAdapter
    private lateinit var enterContainer: MessageEnterTransitionContainer

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
            }
        })

        adapter.submitInitial(
            listOf(
                MessageModel(1, MessageModel.Type.TEXT, text = "这是一个仿 Telegram 的消息气泡动画页面", time = "09:43"),
                MessageModel(2, MessageModel.Type.TEXT, text = "底部输入后点击“发送”查看进入动画与气泡涟漪", time = "09:43"),
                MessageModel(3, MessageModel.Type.TEXT, text = "点击气泡可触发选择涟漪效果", time = "09:43")
            )
        )

        binding.sendButton.setOnClickListener {
            val input = binding.inputEdit.text?.toString() ?: ""
            val text = if (input.isBlank()) "空消息" else input
            binding.inputEdit.setText("")
            val id = System.currentTimeMillis()
            adapter.addMessage(MessageModel(id, MessageModel.Type.TEXT, text = text, time = "09:43"))
            binding.recyclerView.post {
                binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
            }
        }
    }
}
