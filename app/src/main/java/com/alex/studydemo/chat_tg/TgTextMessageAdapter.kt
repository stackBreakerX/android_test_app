package com.alex.studydemo.chat_tg

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.alex.studydemo.databinding.ItemTgTextCellBinding

/**
 * TG 消息数据模型（演示用）
 * - Text：文本消息，支持引用/翻译/点赞等额外信息
 * - Image/Video：媒体消息（简化为占位视图）
 * - File：文件消息，展示文件名与大小
 */
sealed class TgMessageItem(open val id: Long) {
    data class Text(
        override val id: Long,
        val text: String,
        val fromMe: Boolean,
        val time: String,
        val userName: String? = null,
        val quote: String? = null,
        val translation: String? = null,
        val reactions: String? = null,
        val layoutPack: TgTextLayoutPack? = null
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

/**
 * TG 文本消息适配器
 * - 通过不同 ViewType 映射到对应的 Cell（文本/图片/视频/文件）
 * - 使用 DiffUtil 比较 id 与内容，提升刷新效率
 */
class TgTextMessageAdapter(
    private val onTextMessageClick: ((TgMessageItem.Text, android.view.View) -> Unit)? = null
) : ListAdapter<TgMessageItem, RecyclerView.ViewHolder>(Diff) {

    init {
        setHasStableIds(true)
    }

    companion object {
        /** 四类消息类型常量 */
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
        // 根据类型创建相应的气泡 Cell
        return when (viewType) {
            TYPE_IMAGE -> MediaVH(TgImageMessageCell(parent.context))
            TYPE_VIDEO -> MediaVH(TgVideoMessageCell(parent.context))
            TYPE_FILE -> MediaVH(TgFileMessageCell(parent.context))
            else -> TextVH(TgTextMessageCell(parent.context), onTextMessageClick)
        }
    }

    override fun getItemId(position: Int): Long = getItem(position).id

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is TgMessageItem.Text -> (holder as TextVH).bind(item)
            is TgMessageItem.Image -> (holder as MediaVH).bindImage(item)
            is TgMessageItem.Video -> (holder as MediaVH).bindVideo(item)
            is TgMessageItem.File -> (holder as MediaVH).bindFile(item)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
            return
        }
        when (val item = getItem(position)) {
            is TgMessageItem.Text -> {
                val textHolder = holder as TextVH
                val payloadSet = mutableSetOf<String>()
                for (payload in payloads) {
                    when (payload) {
                        is Set<*> -> payload.filterIsInstance<String>().forEach { payloadSet.add(it) }
                        is String -> payloadSet.add(payload)
                    }
                }
                // 如果包含 TEXT payload，启动文本 crossfade 动画
                val shouldAnimateText = payloadSet.contains(TgMessagePayloads.TEXT)
                // 先准备动画（保存 startRect），再更新内容
                // 动画会在 layout 完成后自动启动（在 onLayout 中）
                if (payloadSet.isNotEmpty()) {
                    textHolder.cell.prepareTransition(payloadSet)
                }
                // 然后更新内容（这会触发 layout，layout 完成后会启动动画）
                textHolder.bind(item, animate = shouldAnimateText)
            }
            else -> onBindViewHolder(holder, position)
        }
    }

    class TextVH(
        val cell: TgTextMessageCell,
        private val onTextMessageClick: ((TgMessageItem.Text, android.view.View) -> Unit)?
    ) : RecyclerView.ViewHolder(cell) {
        fun bind(item: TgMessageItem.Text, animate: Boolean = false) {
            cell.bindMessage(item.text, item.time, item.fromMe, item.layoutPack, animate)
            // 用户名
            if (item.userName.isNullOrBlank()) {
                cell.setUserNameView(null)
            } else {
                val tv = android.widget.TextView(cell.context)
                tv.text = item.userName
                tv.textSize = 14f
                tv.setTextColor(0xFF2E7D32.toInt())
                tv.setTypeface(TgAndroidUtilities.bold())
                cell.setUserNameView(tv)
            }
            // 额外区块：引用/翻译/点赞
            // 引用（位于内容上方）
            if (item.quote.isNullOrBlank()) {
                cell.setReplyView(null)
            } else {
                val reply = ReplyView(cell.context)
                reply.setText(item.quote!!)
                cell.setReplyView(reply)
            }
            // 翻译（位于内容下方）
            if (item.translation.isNullOrBlank()) {
                cell.setTranslateView(null)
            } else {
                val translate = TranslateView(cell.context)
                translate.setText(item.translation!!)
                cell.setTranslateView(translate)
            }
            // 点赞/反应（锚定气泡底部左侧）
            if (item.reactions.isNullOrBlank()) {
                cell.setLiveView(null)
            } else {
                val live = LiveView(cell.context)
                // 用空格拆分为多个反应项
                live.reactions = item.reactions!!.split(' ').filter { it.isNotBlank() }
                cell.setLiveView(live)
            }
            // 设置点击监听器
            cell.setOnClickListener {
                onTextMessageClick?.invoke(item, cell)
            }
        }
    }

    class MediaVH(private val cell: BaseTgMessageCell) : RecyclerView.ViewHolder(cell) {
        /** 绑定图片消息的基础信息（时间/方向） */
        fun bindImage(item: TgMessageItem.Image) {
            cell.bindBase(item.time, item.fromMe)
        }
        /** 绑定视频消息的基础信息（时间/方向） */
        fun bindVideo(item: TgMessageItem.Video) {
            cell.bindBase(item.time, item.fromMe)
        }
        /** 绑定文件消息的基础信息，并设置文件名与大小 */
        fun bindFile(item: TgMessageItem.File) {
            if (cell is TgFileMessageCell) {
                cell.setFile(item.name, item.size)
            }
            cell.bindBase(item.time, item.fromMe)
        }
    }

    object Diff : DiffUtil.ItemCallback<TgMessageItem>() {
        /** 判断是否为同一条消息：依据唯一 id */
        override fun areItemsTheSame(oldItem: TgMessageItem, newItem: TgMessageItem): Boolean =
            oldItem.id == newItem.id

        /** 判断内容是否相同：数据类的 equals 即可 */
        override fun areContentsTheSame(oldItem: TgMessageItem, newItem: TgMessageItem): Boolean =
            oldItem == newItem

        override fun getChangePayload(oldItem: TgMessageItem, newItem: TgMessageItem): Any? {
            if (oldItem !is TgMessageItem.Text || newItem !is TgMessageItem.Text) return null
            val payloads = mutableSetOf<String>()
            if (oldItem.text != newItem.text) payloads.add(TgMessagePayloads.TEXT)
            if (oldItem.userName != newItem.userName) payloads.add(TgMessagePayloads.USER_NAME)
            if (oldItem.quote != newItem.quote) payloads.add(TgMessagePayloads.QUOTE)
            if (oldItem.translation != newItem.translation) payloads.add(TgMessagePayloads.TRANSLATION)
            if (oldItem.reactions != newItem.reactions) payloads.add(TgMessagePayloads.REACTIONS)
            if (oldItem.time != newItem.time) payloads.add(TgMessagePayloads.TIME)
            if (oldItem.fromMe != newItem.fromMe) payloads.add(TgMessagePayloads.LAYOUT)
            if (oldItem.layoutPack != newItem.layoutPack) payloads.add(TgMessagePayloads.LAYOUT)
            return if (payloads.isEmpty()) null else payloads
        }
    }
}
