package com.alex.studydemo.chat_tg

import android.text.Layout
import android.text.StaticLayout

/**
 * 文本排版块（按行切分的子布局）
 * - 记录子布局高度、最右边界与方向标记（RTL/非 RTL）
 * - 可计算自身在所有块中的 Y 偏移，用于逐块绘制
 */
data class TgTextLayoutBlock(
    val textLayout: StaticLayout,
    val padTop: Int = 0,
    val padBottom: Int = 0
) {
    val height: Int = textLayout.height
    val maxRight: Float = run {
        var mr = 0f
        for (i in 0 until textLayout.lineCount) {
            val r = textLayout.getLineRight(i)
            mr = kotlin.math.max(mr, r)
        }
        mr
    }
    val directionFlags: Int = if (isRtl(textLayout)) FLAG_RTL else FLAG_NOT_RTL

    fun textYOffset(blocks: List<TgTextLayoutBlock>): Int {
        var h = 0
        for (block in blocks) {
            if (block == this) break
            h += block.padTop + block.height + block.padBottom
        }
        return h
    }

    private fun isRtl(layout: Layout): Boolean {
        return (0 until layout.lineCount).any { layout.getLineLeft(it) > layout.getLineRight(it) }
    }

    companion object {
        const val FLAG_RTL = 1
        const val FLAG_NOT_RTL = 2
    }
}
