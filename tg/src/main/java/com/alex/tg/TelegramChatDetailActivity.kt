package com.alex.tg

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alex.studydemo.base.BaseActivity
import com.alex.tg.chat.ChatDetailAdapter
import com.alex.tg.chat.ChatMessageItem
import com.alex.tg.chat.FlyTextEnterTransition
import com.alex.tg.chat.MessageEnterTransitionContainer
import com.alex.tg.chat.MessageListItemAnimator
import com.alex.tg.databinding.ActivityChatDetailBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TelegramChatDetailActivity : BaseActivity<ActivityChatDetailBinding>() {

    private lateinit var adapter: ChatDetailAdapter
    private lateinit var layoutManager: LinearLayoutManager
    private lateinit var enterContainer: MessageEnterTransitionContainer
    private val items = mutableListOf<ChatMessageItem>()
    private var nextId = 1L
    private var pendingSendMessageId: Long? = null
    private var loadingHistory = false
    private var selectionMode = false

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityChatDetailBinding =
        ActivityChatDetailBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        title = "Telegram 消息详情页"
        setupEnterContainer()
        setupRecycler()
        setupSelectionBar()
        setupInputBar()
        seedInitialMessages()
    }

    private fun setupEnterContainer() {
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
            initialPrefetchItemCount = 8
        }
        adapter = ChatDetailAdapter(
            onItemClick = { item ->
                if (selectionMode) {
                    toggleSelection(item.id)
                } else {
                    toggleExpanded(item.id)
                }
            },
            onItemLongClick = { item ->
                if (!selectionMode) {
                    setSelectionMode(true)
                }
                toggleSelection(item.id)
                true
            },
            onToggleType = { item ->
                toggleType(item.id)
            }
        )
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = adapter
        binding.recyclerView.itemAnimator = MessageListItemAnimator(enterContainer)
        binding.recyclerView.setItemViewCacheSize(12)
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                maybePreloadHistory()
                updateFloatingAvatar()
            }
        })
    }

    private fun setupSelectionBar() {
        binding.selectionClear.setOnClickListener {
            setSelectionMode(false)
        }
        updateSelectionCount()
    }

    private fun setupInputBar() {
        binding.sendButton.setOnClickListener {
            val text = binding.inputEdit.text?.toString().orEmpty().ifBlank { "空消息" }
            binding.inputEdit.setText("")
            sendMessage(text)
        }
        binding.typeButton.setOnClickListener {
            toggleLastMessageType()
        }
    }

    private fun seedInitialMessages() {
        val seed = listOf(
            "这个页面参考 Telegram ChatActivity 的 UI 架构",
            "包含动画、预加载、多选、头像悬浮与输入飞入",
            "长按消息进入多选模式，单击展开详情"
        )
        seed.forEachIndexed { index, text ->
            items.add(
                ChatMessageItem.Text(
                    id = nextId++,
                    text = text,
                    fromMe = index % 2 == 0,
                    sender = if (index % 2 == 0) "我" else "Alex",
                    expanded = false,
                    selected = false
                )
            )
        }
        items.add(
            ChatMessageItem.System(
                id = nextId++,
                text = "09/12"
            )
        )
        submitListAndScrollToEnd()
    }

    private fun submitListAndScrollToEnd() {
        adapter.submitList(items.toList()) {
            binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
            binding.recyclerView.post { runSendTransitionIfNeeded() }
            updateFloatingAvatar()
        }
    }

    private fun sendMessage(text: String) {
        val message = ChatMessageItem.Text(
            id = nextId++,
            text = text,
            fromMe = true,
            sender = "我",
            expanded = false,
            selected = false
        )
        items.add(message)
        pendingSendMessageId = message.id
        submitListAndScrollToEnd()
    }

    private fun runSendTransitionIfNeeded() {
        val id = pendingSendMessageId ?: return
        val index = adapter.currentList.indexOfFirst { it.id == id }
        if (index < 0) {
            pendingSendMessageId = null
            return
        }
        val vh = binding.recyclerView.findViewHolderForAdapterPosition(index)
        val bubble = vh?.itemView?.findViewById<android.view.View>(com.alex.tg.R.id.bubbleContainer)
        val item = adapter.currentList.getOrNull(index) as? ChatMessageItem.Text
        if (bubble != null && item != null) {
            val transition = FlyTextEnterTransition(binding.inputEdit, bubble, item.text)
            enterContainer.addTransition(transition)
            pendingSendMessageId = null
        } else {
            binding.recyclerView.post { runSendTransitionIfNeeded() }
        }
    }

    private fun toggleExpanded(id: Long) {
        val index = items.indexOfFirst { it.id == id }
        val item = items.getOrNull(index) as? ChatMessageItem.Text ?: return
        items[index] = item.copy(expanded = !item.expanded)
        adapter.submitList(items.toList())
    }

    private fun toggleSelection(id: Long) {
        val index = items.indexOfFirst { it.id == id }
        val item = items.getOrNull(index) as? ChatMessageItem.Text ?: return
        items[index] = item.copy(selected = !item.selected)
        adapter.submitList(items.toList()) {
            updateSelectionCount()
            if (selectionMode && items.none { it is ChatMessageItem.Text && it.selected }) {
                setSelectionMode(false)
            }
        }
    }

    private fun toggleType(id: Long) {
        val index = items.indexOfFirst { it.id == id }
        val item = items.getOrNull(index) ?: return
        items[index] = when (item) {
            is ChatMessageItem.Text -> ChatMessageItem.System(id = item.id, text = "类型切换：${item.text}")
            is ChatMessageItem.System -> ChatMessageItem.Text(
                id = item.id,
                text = item.text,
                fromMe = false,
                sender = "Alex",
                expanded = false,
                selected = false
            )
            is ChatMessageItem.Loading -> item
        }
        adapter.submitList(items.toList())
    }

    private fun toggleLastMessageType() {
        val index = items.indexOfLast { it is ChatMessageItem.Text || it is ChatMessageItem.System }
        val item = items.getOrNull(index) ?: return
        toggleType(item.id)
    }

    private fun setSelectionMode(enable: Boolean) {
        selectionMode = enable
        binding.selectionBar.isVisible = enable
        adapter.setSelectionMode(enable)
        if (!enable) {
            clearSelections()
        }
        updateSelectionCount()
    }

    private fun clearSelections() {
        var changed = false
        items.replaceAll { item ->
            if (item is ChatMessageItem.Text && item.selected) {
                changed = true
                item.copy(selected = false)
            } else {
                item
            }
        }
        if (changed) {
            adapter.submitList(items.toList())
        }
    }

    private fun updateSelectionCount() {
        val count = items.count { it is ChatMessageItem.Text && it.selected }
        binding.selectionTitle.text = "已选择 $count 条"
        binding.selectionBar.isVisible = selectionMode
    }

    private fun maybePreloadHistory() {
        if (loadingHistory) return
        val first = layoutManager.findFirstVisibleItemPosition()
        if (first <= 2) {
            loadingHistory = true
            val anchorView = binding.recyclerView.getChildAt(0)
            val anchorOffset = anchorView?.top ?: 0
            items.add(0, ChatMessageItem.Loading)
            adapter.submitList(items.toList()) {
                layoutManager.scrollToPositionWithOffset(first + 1, anchorOffset)
                lifecycleScope.launch {
                    delay(520)
                    prependHistory(first, anchorOffset)
                }
            }
        }
    }

    private fun prependHistory(firstVisible: Int, anchorOffset: Int) {
        items.removeAll { it is ChatMessageItem.Loading }
        val history = List(8) { index ->
            val id = nextId++
            ChatMessageItem.Text(
                id = id,
                text = "历史消息 #$id",
                fromMe = (index % 2 == 0),
                sender = if (index % 2 == 0) "我" else "Alex",
                expanded = false,
                selected = false
            )
        }
        items.addAll(0, history)
        adapter.submitList(items.toList()) {
            layoutManager.scrollToPositionWithOffset(firstVisible + history.size, anchorOffset)
            loadingHistory = false
        }
    }

    private fun updateFloatingAvatar() {
        val first = layoutManager.findFirstVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION) return
        val view = layoutManager.findViewByPosition(first) ?: return
        val item = adapter.currentList.getOrNull(first) as? ChatMessageItem.Text ?: return
        binding.floatingAvatar.setLabel(item.sender)
        val targetY = view.top.toFloat().coerceAtLeast(0f)
        binding.floatingAvatar.animateToY(targetY)
    }

    companion object {
        fun newInstance(context: Context) {
            context.startActivity(Intent(context, TelegramChatDetailActivity::class.java))
        }
    }
}

