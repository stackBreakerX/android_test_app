package com.alex.tg.chat

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.alex.tg.chat.cell.BaseTgMessageCell
import com.alex.tg.chat.model.MessageModel

class MessageListAdapter : ListAdapter<MessageModel, MessageViewHolder>(Diff) {
    private val items = mutableListOf<MessageModel>()

    fun submitInitial(list: List<MessageModel>) {
        items.clear()
        items.addAll(list)
        submitList(items.toList())
    }

    fun addMessage(item: MessageModel) {
        items.add(item)
        submitList(items.toList())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val cell = BaseTgMessageCell(parent.context)
        cell.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return MessageViewHolder(cell)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    object Diff : DiffUtil.ItemCallback<MessageModel>() {
        override fun areItemsTheSame(oldItem: MessageModel, newItem: MessageModel): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: MessageModel, newItem: MessageModel): Boolean = oldItem == newItem
    }
}

class MessageViewHolder(private val cell: BaseTgMessageCell) : RecyclerView.ViewHolder(cell) {
    fun bind(item: MessageModel) {
        cell.bind(item)
    }
}
