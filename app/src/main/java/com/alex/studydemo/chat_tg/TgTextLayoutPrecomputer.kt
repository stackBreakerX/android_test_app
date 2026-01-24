package com.alex.studydemo.chat_tg

import android.text.TextPaint

object TgTextLayoutPrecomputer {
    private const val MAX_RATIO = 0.72f
    private fun dp(value: Float, density: Float): Int = (value * density).toInt()
    private fun timePaint(fromMe: Boolean): TextPaint = if (fromMe) TgTheme.chatTimePaintOut else TgTheme.chatTimePaintIn
    fun precompute(
        text: String,
        time: String,
        fromMe: Boolean,
        containerWidth: Int,
        density: Float,
        hasExtraBlock: Boolean,
        inlineTimeWithText: Boolean = true,
        showStatus: Boolean = true,
        statusText: String = "✓✓"
    ): TgTextLayoutPack {
        TgTheme.init(density)
        val paddingStartOut = dp(12f, density)
        val paddingEndOut = dp(16f, density)
        val paddingStartIn = dp(16f, density)
        val paddingEndIn = dp(12f, density)
        val timeExtraWidth = dp(10f, density)
        val statusGap = dp(6f, density)
        val maxBubbleWidth = (containerWidth * MAX_RATIO).toInt()
        val paddingStart = if (fromMe) paddingStartOut else paddingStartIn
        val paddingEnd = if (fromMe) paddingEndOut else paddingEndIn
        val contentMaxWidth = maxBubbleWidth - (paddingStart + paddingEnd)
        val tPaint = timePaint(fromMe)
        val tWidth = tPaint.measureText(time).toInt()
        val statusPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = dp(13f, density).toFloat()
        }
        val statusWidth = if (showStatus) statusPaint.measureText(statusText).toInt() else 0
        val statusReserve = if (showStatus && statusWidth > 0) statusWidth + statusGap else 0
        var reservedRight = 0
        var doInline = inlineTimeWithText
        if (hasExtraBlock && doInline) {
            doInline = false
        }
        val initialLayout = TgTextLayoutBuilder.buildBlocks(text, TgTheme.chatMsgTextPaint, contentMaxWidth)
        var lastLineWidth = 0
        if (initialLayout.isNotEmpty()) {
            val last = initialLayout.last()
            val lastLine = last.textLayout.lineCount - 1
            lastLineWidth = if (lastLine >= 0) last.textLayout.getLineRight(lastLine).toInt() else 0
        }
        val canInline = doInline && lastLineWidth + timeExtraWidth + tWidth + statusReserve <= contentMaxWidth
        if (canInline) {
            reservedRight = tWidth + timeExtraWidth + statusReserve
        }
        val available = (contentMaxWidth - reservedRight).coerceAtLeast(1)
        val blocks = TgTextLayoutBuilder.buildBlocks(text, TgTheme.chatMsgTextPaint, available)
        var contentWidth = 0
        var totalH = 0
        for (b in blocks) {
            val w = b.maxRight.toInt()
            if (w > contentWidth) contentWidth = w
            totalH += (b.height + b.padTop + b.padBottom)
        }
        var y = 0f
        for (i in 0 until blocks.size - 1) {
            val b = blocks[i]
            y += (b.padTop + b.height + b.padBottom).toFloat()
        }
        val last = blocks.lastOrNull()
        val lastLine = if (last != null) last.textLayout.lineCount - 1 else -1
        val baseline = if (last != null && lastLine >= 0) last.textLayout.getLineBaseline(lastLine).toFloat() else last?.height?.toFloat()
        val lastBaseline = if (baseline != null && last != null) y + last.padTop + baseline else null
        var lastWidth = 0
        if (last != null && lastLine >= 0) {
            lastWidth = last.textLayout.getLineRight(lastLine).toInt()
        }
        return TgTextLayoutPack(
            blocks = blocks,
            contentWidth = contentWidth,
            height = totalH,
            lastLineWidth = lastWidth,
            lastLineBaseline = lastBaseline,
            reservedRight = reservedRight,
            inlineTimeWithText = inlineTimeWithText,
            computedForWidth = available
        )
    }
}
