package com.alex.tg.chat

import android.animation.ValueAnimator
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.animation.addListener
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.alex.tg.R
import com.alex.tg.databinding.ItemMessageLoadingBinding
import com.alex.tg.databinding.ItemMessageSystemBinding
import com.alex.tg.databinding.ItemMessageTextBinding

sealed class ChatMessageItem(open val id: Long) {
    data class Text(
        override val id: Long,
        val text: String,
        val fromMe: Boolean,
        val sender: String,
        val expanded: Boolean,
        val selected: Boolean
    ) : ChatMessageItem(id)

    data class System(
        override val id: Long,
        val text: String
    ) : ChatMessageItem(id)

    data object Loading : ChatMessageItem(-1L)
}

class ChatDetailAdapter(
    private val onItemClick: (ChatMessageItem.Text) -> Unit,
    private val onItemLongClick: (ChatMessageItem.Text) -> Boolean,
    private val onToggleType: (ChatMessageItem.Text) -> Unit
) : ListAdapter<ChatMessageItem, RecyclerView.ViewHolder>(Diff) {

    private var selectionMode = false

    companion object {
        private const val TYPE_TEXT = 1
        private const val TYPE_SYSTEM = 2
        private const val TYPE_LOADING = 3
        private const val PAYLOAD_SELECTION = "payload_selection"
        private const val PAYLOAD_EXPANDED = "payload_expanded"
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ChatMessageItem.Text -> TYPE_TEXT
        is ChatMessageItem.System -> TYPE_SYSTEM
        is ChatMessageItem.Loading -> TYPE_LOADING
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_TEXT -> TextMessageViewHolder(ItemMessageTextBinding.inflate(inflater, parent, false))
            TYPE_SYSTEM -> SystemMessageViewHolder(ItemMessageSystemBinding.inflate(inflater, parent, false))
            else -> LoadingViewHolder(ItemMessageLoadingBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        onBindViewHolder(holder, position, mutableListOf())
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        val item = getItem(position)
        when (holder) {
            is TextMessageViewHolder -> {
                val payloadSet = payloads.filterIsInstance<Set<*>>().flatten().mapNotNull { it as? String }.toSet()
                holder.bind(item as ChatMessageItem.Text, payloadSet)
            }
            is SystemMessageViewHolder -> holder.bind(item as ChatMessageItem.System)
            is LoadingViewHolder -> holder.bind()
        }
    }

    override fun getItemId(position: Int): Long = getItem(position).id

    init {
        setHasStableIds(true)
    }

    fun setSelectionMode(enabled: Boolean) {
        if (selectionMode == enabled) return
        selectionMode = enabled
        notifyDataSetChanged()
    }

    inner class TextMessageViewHolder(private val binding: ItemMessageTextBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var detailAnimator: ValueAnimator? = null

        fun bind(item: ChatMessageItem.Text, payloads: Set<String>) {
            val maxWidth = (binding.root.resources.displayMetrics.widthPixels * 0.72f).toInt()
            binding.messageText.maxWidth = maxWidth
            binding.messageRow.gravity = if (item.fromMe) Gravity.END else Gravity.START
            binding.avatarStub.isVisible = !item.fromMe
            binding.messageText.text = item.text
            binding.bubbleContainer.setBackgroundResource(
                if (item.fromMe) R.drawable.tg_bubble_outgoing else R.drawable.tg_bubble_incoming
            )
            binding.messageText.setTextColor(Color.parseColor("#121212"))
            binding.senderText.isVisible = !item.fromMe
            if (!item.fromMe) {
                binding.senderText.text = item.sender
                binding.senderText.setTextColor(getSenderColor(item.sender))
            }
            binding.timeText.text = formatTime(item.id)
            binding.statusIcon.isVisible = item.fromMe
            binding.timeText.setTextColor(
                if (item.fromMe) Color.parseColor("#00B1BA") else Color.parseColor("#7A121212")
            )
            updateBubblePadding(item.fromMe)
            binding.detailText.text = "详细信息：支持类型变化与视图显隐动画"
            binding.typeToggle.isVisible = item.expanded

            binding.root.setOnClickListener { onItemClick(item) }
            binding.root.setOnLongClickListener { onItemLongClick(item) }
            binding.typeToggle.setOnClickListener { onToggleType(item) }

            if (payloads.isEmpty()) {
                applySelection(item.selected, animate = false)
                applyExpanded(item.expanded, animate = false)
            } else {
                if (PAYLOAD_SELECTION in payloads) {
                    applySelection(item.selected, animate = true)
                }
                if (PAYLOAD_EXPANDED in payloads) {
                    applyExpanded(item.expanded, animate = true)
                    binding.typeToggle.isVisible = item.expanded
                }
            }
        }

        private fun applySelection(selected: Boolean, animate: Boolean) {
            val show = selectionMode || selected
            val targetAlpha = if (selected) 1f else 0.35f
            val targetScale = if (selected) 1f else 0.85f
            val startView = binding.selectionCheckStart
            val endView = binding.selectionCheckEnd
            if (!show) {
                startView.isVisible = false
                endView.isVisible = false
                return
            }
            val activeView = if (binding.avatarStub.isVisible) startView else endView
            startView.isVisible = activeView === startView
            endView.isVisible = activeView === endView
            if (animate) {
                activeView.animate().cancel()
                activeView.animate().alpha(targetAlpha).scaleX(targetScale).scaleY(targetScale)
                    .setDuration(180)
                    .start()
            } else {
                activeView.alpha = targetAlpha
                activeView.scaleX = targetScale
                activeView.scaleY = targetScale
            }
        }

        private fun applyExpanded(expanded: Boolean, animate: Boolean) {
            detailAnimator?.cancel()
            if (!animate) {
                binding.detailText.isVisible = expanded
                binding.detailText.alpha = if (expanded) 1f else 0f
                binding.detailText.layoutParams = binding.detailText.layoutParams.apply {
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
                return
            }
            val view = binding.detailText
            if (expanded) {
                view.isVisible = true
                view.alpha = 0f
                val width = if (binding.root.width > 0) binding.root.width else binding.root.measuredWidth
                val widthSpec = View.MeasureSpec.makeMeasureSpec(
                    if (width > 0) width else view.resources.displayMetrics.widthPixels,
                    View.MeasureSpec.AT_MOST
                )
                view.measure(
                    widthSpec,
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                val targetHeight = view.measuredHeight.coerceAtLeast(1)
                view.layoutParams = view.layoutParams.apply { height = 0 }
                detailAnimator = ValueAnimator.ofInt(0, targetHeight).apply {
                    duration = 200
                    addUpdateListener {
                        view.layoutParams = view.layoutParams.apply { height = it.animatedValue as Int }
                        view.requestLayout()
                    }
                    start()
                }
                view.animate().alpha(1f).setDuration(200).start()
            } else {
                val startHeight = view.height
                detailAnimator = ValueAnimator.ofInt(startHeight, 0).apply {
                    duration = 160
                    addUpdateListener {
                        view.layoutParams = view.layoutParams.apply { height = it.animatedValue as Int }
                        view.requestLayout()
                    }
                    addListener(onEnd = { view.isVisible = false })
                    start()
                }
                view.animate().alpha(0f).setDuration(120).start()
            }
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

    class SystemMessageViewHolder(private val binding: ItemMessageSystemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ChatMessageItem.System) {
            binding.systemText.text = item.text
        }
    }

    class LoadingViewHolder(private val binding: ItemMessageLoadingBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            binding.loadingText.text = "加载中..."
        }
    }

    object Diff : DiffUtil.ItemCallback<ChatMessageItem>() {
        override fun areItemsTheSame(oldItem: ChatMessageItem, newItem: ChatMessageItem): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ChatMessageItem, newItem: ChatMessageItem): Boolean =
            oldItem == newItem

        override fun getChangePayload(oldItem: ChatMessageItem, newItem: ChatMessageItem): Any? {
            if (oldItem is ChatMessageItem.Text && newItem is ChatMessageItem.Text) {
                val changes = mutableSetOf<String>()
                if (oldItem.selected != newItem.selected) {
                    changes.add(PAYLOAD_SELECTION)
                }
                if (oldItem.expanded != newItem.expanded) {
                    changes.add(PAYLOAD_EXPANDED)
                }
                return if (changes.isEmpty()) null else changes
            }
            return null
        }
    }

    private fun formatTime(seed: Long): String {
        val minute = (seed % 60).toInt().toString().padStart(2, '0')
        val hour = ((seed / 60) % 24).toInt().toString().padStart(2, '0')
        return "$hour:$minute"
    }

    private fun getSenderColor(name: String): Int {
        val palette = listOf(
            Color.parseColor("#84581E"),
            Color.parseColor("#27731A"),
            Color.parseColor("#305899"),
            Color.parseColor("#8A3D91"),
            Color.parseColor("#D15C00")
        )
        val idx = kotlin.math.abs(name.hashCode()) % palette.size
        return palette[idx]
    }
}

