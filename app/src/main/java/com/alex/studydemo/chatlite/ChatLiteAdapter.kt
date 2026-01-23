package com.alex.studydemo.chatlite

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.alex.studydemo.databinding.ItemChatMessageLiteBinding

data class ChatLiteMessage(
    val id: Long,
    val text: String,
    val fromMe: Boolean,
    val time: String
)

class ChatLiteAdapter : ListAdapter<ChatLiteMessage, ChatLiteAdapter.VH>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemChatMessageLiteBinding.inflate(inflater, parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(private val binding: ItemChatMessageLiteBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ChatLiteMessage) {
            val maxWidth = (binding.root.resources.displayMetrics.widthPixels * 0.72f).toInt()
            binding.messageText.maxWidth = maxWidth
            binding.messageRow.gravity = if (item.fromMe) Gravity.END else Gravity.START
            binding.bubbleContainer.setBackgroundResource(
                if (item.fromMe) com.alex.tg.R.drawable.tg_bubble_outgoing else com.alex.tg.R.drawable.tg_bubble_incoming
            )
            binding.messageText.text = item.text
            binding.messageText.setTextColor(Color.parseColor("#121212"))
            binding.timeText.text = item.time
            binding.timeText.setTextColor(
                if (item.fromMe) Color.parseColor("#00B1BA") else Color.parseColor("#7A121212")
            )
            updateBubblePadding(item.fromMe)
        }

        private fun updateBubblePadding(fromMe: Boolean) {
            val density = binding.root.resources.displayMetrics.density
            val paddingTop = (8 * density).toInt()
            val paddingBottom = (8 * density).toInt()
            val paddingStart = (if (fromMe) 12 else 16) * density
            val paddingEnd = (if (fromMe) 16 else 12) * density
            binding.bubbleContent.setPadding(
                paddingStart.toInt(),
                paddingTop,
                paddingEnd.toInt(),
                paddingBottom
            )
        }
    }

    object Diff : DiffUtil.ItemCallback<ChatLiteMessage>() {
        override fun areItemsTheSame(oldItem: ChatLiteMessage, newItem: ChatLiteMessage): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ChatLiteMessage, newItem: ChatLiteMessage): Boolean =
            oldItem == newItem
    }
}

