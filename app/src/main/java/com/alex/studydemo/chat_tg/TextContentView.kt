package com.alex.studydemo.chat_tg

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class TextContentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs), TgContentView {

    private val textPaint = TgTheme.chatMsgTextPaint
    private var messageText: String = ""
    private var blocks: List<TgTextLayoutBlock> = emptyList()
    private var contentWidth: Int = 0
    // 为时间预留的右侧宽度（仅在文本行内时间时使用）
    private var reservedRight: Int = 0

    fun setText(text: String) {
        messageText = text
        requestLayout()
        invalidate()
    }

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
        contentWidth = blocks.maxOfOrNull { it.maxRight.toInt() } ?: 0
        val height = blocks.sumOf { it.height + it.padTop + it.padBottom }
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

