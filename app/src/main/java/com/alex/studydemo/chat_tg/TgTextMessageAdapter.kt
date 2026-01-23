package com.alex.studydemo.chat_tg

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.alex.studydemo.databinding.ItemTgTextCellBinding

sealed class TgMessageItem(open val id: Long) {
    data class Text(
        override val id: Long,
        val text: String,
        val fromMe: Boolean,
        val time: String,
        val quote: String? = null,
        val translation: String? = null,
        val reactions: String? = null
    ) : TgMessageItem(id)

    data class Image(
        override val id: Long,
        val fromMe: Boolean,
        val time: String
    ) : TgMessageItem(id)

    data class Video(
        override val id: Long,
        val fromMe: Boolean,
        val time: String
    ) : TgMessageItem(id)

    data class File(
        override val id: Long,
        val name: String,
        val size: String,
        val fromMe: Boolean,
        val time: String
    ) : TgMessageItem(id)
}

class TgTextMessageAdapter : ListAdapter<TgMessageItem, RecyclerView.ViewHolder>(Diff) {

    companion object {
        private const val TYPE_TEXT = 1
        private const val TYPE_IMAGE = 2
        private const val TYPE_VIDEO = 3
        private const val TYPE_FILE = 4
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is TgMessageItem.Text -> TYPE_TEXT
        is TgMessageItem.Image -> TYPE_IMAGE
        is TgMessageItem.Video -> TYPE_VIDEO
        is TgMessageItem.File -> TYPE_FILE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_IMAGE -> MediaVH(TgImageMessageCell(parent.context))
            TYPE_VIDEO -> MediaVH(TgVideoMessageCell(parent.context))
            TYPE_FILE -> MediaVH(TgFileMessageCell(parent.context))
            else -> TextVH(TgTextMessageCell(parent.context))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is TgMessageItem.Text -> (holder as TextVH).bind(item)
            is TgMessageItem.Image -> (holder as MediaVH).bindImage(item)
            is TgMessageItem.Video -> (holder as MediaVH).bindVideo(item)
            is TgMessageItem.File -> (holder as MediaVH).bindFile(item)
        }
    }

    class TextVH(private val cell: TgTextMessageCell) : RecyclerView.ViewHolder(cell) {
        fun bind(item: TgMessageItem.Text) {
            cell.bindMessage(item.text, item.time, item.fromMe)
            // 额外区块：引用/翻译/点赞（来自 XML view）
            val context = cell.context
            if (item.quote == null && item.translation == null && item.reactions == null) {
                cell.setExtraView(null)
            } else {
                val extra = LayoutInflater.from(context)
                    .inflate(com.alex.studydemo.R.layout.view_tg_text_extras, cell, false)
                val quoteContainer = extra.findViewById<android.view.View>(com.alex.studydemo.R.id.quoteContainer)
                val quoteText = extra.findViewById<android.widget.TextView>(com.alex.studydemo.R.id.quoteText)
                val translationText = extra.findViewById<android.widget.TextView>(com.alex.studydemo.R.id.translationText)
                val reactionsText = extra.findViewById<android.widget.TextView>(com.alex.studydemo.R.id.reactionsText)

                if (item.quote.isNullOrBlank()) {
                    quoteContainer.visibility = android.view.View.GONE
                } else {
                    quoteContainer.visibility = android.view.View.VISIBLE
                    quoteText.text = item.quote
                }
                if (item.translation.isNullOrBlank()) {
                    translationText.visibility = android.view.View.GONE
                } else {
                    translationText.visibility = android.view.View.VISIBLE
                    translationText.text = item.translation
                }
                if (item.reactions.isNullOrBlank()) {
                    reactionsText.visibility = android.view.View.GONE
                } else {
                    reactionsText.visibility = android.view.View.VISIBLE
                    reactionsText.text = item.reactions
                }
                cell.setExtraView(extra)
            }
        }
    }

    class MediaVH(private val cell: BaseTgMessageCell) : RecyclerView.ViewHolder(cell) {
        fun bindImage(item: TgMessageItem.Image) {
            cell.bindBase(item.time, item.fromMe)
        }
        fun bindVideo(item: TgMessageItem.Video) {
            cell.bindBase(item.time, item.fromMe)
        }
        fun bindFile(item: TgMessageItem.File) {
            if (cell is TgFileMessageCell) {
                cell.setFile(item.name, item.size)
            }
            cell.bindBase(item.time, item.fromMe)
        }
    }

    object Diff : DiffUtil.ItemCallback<TgMessageItem>() {
        override fun areItemsTheSame(oldItem: TgMessageItem, newItem: TgMessageItem): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: TgMessageItem, newItem: TgMessageItem): Boolean =
            oldItem == newItem
    }
}

