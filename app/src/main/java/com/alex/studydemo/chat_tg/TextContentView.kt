package com.alex.studydemo.chat_tg

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/**
 * 文本内容视图
 * - 负责将纯文本按宽度进行排版（StaticLayout），并逐块绘制
 * - 支持为“行内时间”预留右侧宽度，避免与末行时间文本重叠
 */
class TextContentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs), TgContentView {

    /** 聊天文本画笔（来自主题） */
    private val textPaint = TgTheme.chatMsgTextPaint
    /** 当前消息文本内容 */
    private var messageText: String = ""
    /** 文本排版块集合（按 10 行切分） */
    private var blocks: List<TgTextLayoutBlock> = emptyList()
    /** 内容实际宽度（用于气泡宽度计算） */
    private var contentWidth: Int = 0
    // 为时间预留的右侧宽度（仅在文本行内时间时使用）
    private var reservedRight: Int = 0

    /** 设置文本内容并请求重新布局与重绘 */
    fun setText(text: String) {
        messageText = text
        requestLayout()
        invalidate()
    }

    /** 设置为时间预留的右侧宽度（单位像素） */
    fun setReservedRight(value: Int) {
        if (reservedRight == value) return
        reservedRight = value
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        TgTheme.init(resources.displayMetrics.density)
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec)
        val available = max(1, maxWidth - reservedRight)
        blocks = TgTextLayoutBuilder.buildBlocks(messageText, textPaint, available)
        var cw = 0
        var totalH = 0
        for (b in blocks) {
            cw = kotlin.math.max(cw, b.maxRight.toInt())
            totalH += (b.height + b.padTop + b.padBottom)
        }
        contentWidth = cw
        val height = totalH
        setMeasuredDimension(contentWidth, height)
    }

    override fun onDraw(canvas: Canvas) {
        if (blocks.isEmpty()) return
        var y = 0f
        for (block in blocks) {
            canvas.save()
            canvas.translate(0f, y + block.padTop)
            block.textLayout.draw(canvas)
            canvas.restore()
            y += (block.padTop + block.height + block.padBottom).toFloat()
        }
    }

    override fun getContentWidth(): Int = contentWidth

    override fun getLastLineBaseline(): Float? {
        if (blocks.isEmpty()) return null
        var y = 0f
        for (i in 0 until blocks.size - 1) {
            val b = blocks[i]
            y += (b.padTop + b.height + b.padBottom).toFloat()
        }
        val last = blocks.last()
        val lastLine = last.textLayout.lineCount - 1
        val baseline = if (lastLine >= 0) last.textLayout.getLineBaseline(lastLine) else last.height
        return y + last.padTop + baseline
    }

    override fun getLastLineWidth(): Int {
        if (blocks.isEmpty()) return 0
        val last = blocks.last()
        val lastLine = last.textLayout.lineCount - 1
        return if (lastLine >= 0) last.textLayout.getLineRight(lastLine).toInt() else 0
    }
}
