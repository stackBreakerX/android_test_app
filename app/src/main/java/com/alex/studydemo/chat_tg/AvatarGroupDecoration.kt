package com.alex.studydemo.chat_tg

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.TextPaint
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max

/**
 * 群组头像装饰器
 * - 非自己消息显示头像
 * - 同一发送者连续消息仅在分组最后一条显示头像
 */
class AvatarGroupDecoration(
    private val adapter: TgTextMessageAdapter
) : RecyclerView.ItemDecoration() {

    private val avatarSize = dp(32f)
    private val avatarGap = dp(8f)
    private val avatarBottomInset = dp(4f)
    private val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dpF(14f)
        color = 0xFF121212.toInt()
        textAlign = Paint.Align.CENTER
    }
    private val textBounds = Rect()
    private val palette = intArrayOf(
        0xFFD6FECF.toInt(),
        0xFFC5F7FA.toInt(),
        0xFFFFD6CD.toInt(),
        0xFFE5E7FF.toInt(),
        0xFFFFF2C1.toInt()
    )

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val pos = parent.getChildAdapterPosition(view)
        if (pos == RecyclerView.NO_POSITION) return
        val item = adapter.currentList.getOrNull(pos) ?: return
        // 非自己消息预留头像宽度，使气泡对齐到头像右侧
        val needAvatarSpace = !isFromMe(item)
        outRect.left = if (needAvatarSpace) avatarSize + avatarGap else 0
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val count = parent.childCount
        for (i in 0 until count) {
            val child = parent.getChildAt(i)
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION) continue
            val item = adapter.currentList.getOrNull(position) ?: continue
            if (isFromMe(item)) continue
            if (!shouldShowAvatar(position)) continue

            val sender = getSenderName(item)
            val initials = getInitials(sender)
            val color = palette[kotlin.math.abs(sender.hashCode()) % palette.size]
            avatarPaint.color = color

            val left = child.left - avatarGap - avatarSize
            val bottom = child.bottom - avatarBottomInset
            val top = bottom - avatarSize
            val right = left + avatarSize
            val cx = (left + right) / 2f
            val cy = (top + bottom) / 2f

            c.drawCircle(cx, cy, avatarSize / 2f, avatarPaint)

            textPaint.getTextBounds(initials, 0, initials.length, textBounds)
            val textY = cy + textBounds.height() / 2f
            c.drawText(initials, cx, textY, textPaint)
        }
    }

    private fun shouldShowAvatar(position: Int): Boolean {
        val item = adapter.currentList.getOrNull(position) ?: return false
        if (isFromMe(item)) return false
        val currentKey = getSenderKey(item)
        val next = adapter.currentList.getOrNull(position + 1)
        return next == null || isFromMe(next) || getSenderKey(next) != currentKey
    }

    private fun isFromMe(item: TgMessageItem): Boolean = when (item) {
        is TgMessageItem.Text -> item.fromMe
        is TgMessageItem.Image -> item.fromMe
        is TgMessageItem.Video -> item.fromMe
        is TgMessageItem.File -> item.fromMe
    }

    private fun getSenderKey(item: TgMessageItem): String = when (item) {
        is TgMessageItem.Text -> item.userName ?: "unknown"
        is TgMessageItem.Image -> "unknown_media"
        is TgMessageItem.Video -> "unknown_media"
        is TgMessageItem.File -> "unknown_media"
    }

    private fun getSenderName(item: TgMessageItem): String = when (item) {
        is TgMessageItem.Text -> item.userName ?: "?"
        is TgMessageItem.Image -> "?"
        is TgMessageItem.Video -> "?"
        is TgMessageItem.File -> "?"
    }

    private fun getInitials(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "?"
        val parts = trimmed.split(" ").filter { it.isNotBlank() }
        return if (parts.size >= 2) {
            (parts[0].take(1) + parts[1].take(1)).uppercase()
        } else {
            trimmed.take(2).uppercase()
        }
    }

    private fun dp(value: Float): Int = (value * TgTheme.density).toInt()
    private fun dpF(value: Float): Float = TgAndroidUtilities.dpF(value, TgTheme.density)
}

