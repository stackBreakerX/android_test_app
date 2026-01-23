package com.alex.studydemo.chat_tg

import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

object TgTextLayoutBuilder {
    fun buildBlocks(text: CharSequence, paint: TextPaint, width: Int): List<TgTextLayoutBlock> {
        if (text.isEmpty()) {
            val empty = StaticLayout.Builder.obtain("", 0, 0, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build()
            return listOf(TgTextLayoutBlock(empty))
        }
        // 先按 TG 的排版策略生成完整 StaticLayout
        val layout = buildLayout(text, paint, width)
        // TG 会按行数切块，这里按 10 行切分以贴近原逻辑
        val maxLinesPerBlock = 10
        if (layout.lineCount <= maxLinesPerBlock) {
            return listOf(TgTextLayoutBlock(layout))
        }
        val blocks = mutableListOf<TgTextLayoutBlock>()
        var line = 0
        while (line < layout.lineCount) {
            val endLine = (line + maxLinesPerBlock - 1).coerceAtMost(layout.lineCount - 1)
            val start = layout.getLineStart(line)
            val end = layout.getLineEnd(endLine)
            val subText = text.subSequence(start, end)
            val subLayout = buildLayout(subText, paint, width)
            blocks.add(TgTextLayoutBlock(subLayout))
            line = endLine + 1
        }
        return blocks
    }

    private fun buildLayout(text: CharSequence, paint: TextPaint, width: Int): StaticLayout {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, paint, width, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
        }
    }
}

