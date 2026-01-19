package com.alex.tg.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.alex.tg.databinding.ItemMessageBubbleBinding

sealed class MessageItem {
    data class Text(val text: String) : MessageItem()
}

class MessageListAdapter : ListAdapter<MessageItem, MessageViewHolder>(Diff) {
    private val items = mutableListOf<MessageItem>()

    fun submitInitial(list: List<MessageItem>) {
        items.clear()
        items.addAll(list)
        submitList(items.toList())
    }

    fun addMessage(item: MessageItem) {
        items.add(item)
        submitList(items.toList())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemMessageBubbleBinding.inflate(inflater, parent, false)
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    object Diff : DiffUtil.ItemCallback<MessageItem>() {
        override fun areItemsTheSame(oldItem: MessageItem, newItem: MessageItem): Boolean = oldItem === newItem
        override fun areContentsTheSame(oldItem: MessageItem, newItem: MessageItem): Boolean = oldItem == newItem
    }
}

class MessageViewHolder(private val binding: ItemMessageBubbleBinding) : RecyclerView.ViewHolder(binding.root) {
    fun bind(item: MessageItem) {
        when (item) {
            is MessageItem.Text -> {
                binding.bubble.setText(item.text)
            }
        }
        binding.bubble.setOnClickListener {
            binding.bubble.toggleSelectedRipple()
        }
    }
}

