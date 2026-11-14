package com.alex.studydemo.chat_tg

import android.annotation.SuppressLint
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/**
 * 文本布局构建器
 * - 根据宽度与画笔生成 StaticLayout，并按行数切分为多个 TgTextLayoutBlock
 * - 兼容 API < 23 的旧版 StaticLayout 构造
 */
object TgTextLayoutBuilder {
    fun buildBlocks(text: CharSequence, paint: TextPaint, width: Int): List<TgTextLayoutBlock> {
        if (text.isEmpty()) {
            // 文本为空时返回一个空布局块，避免后续判空
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

    /** 按调用方提供的像素宽度构建布局；API 23 起使用高质量断行，旧系统保留构造方法回退。 */
    private fun buildLayout(text: CharSequence, paint: TextPaint, width: Int): StaticLayout {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Layout 常量从 API 23 可用，且与新注解列举的 LineBreaker 常量同值；仅抑制此调用的误报。
            @SuppressLint("WrongConstant")
            val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            builder.setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, paint, width, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false)
        }
    }
}
