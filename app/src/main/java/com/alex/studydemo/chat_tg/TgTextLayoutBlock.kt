package com.alex.studydemo.chat_tg

import android.text.Layout
import android.text.StaticLayout

data class TgTextLayoutBlock(
    val textLayout: StaticLayout,
    val padTop: Int = 0,
    val padBottom: Int = 0
) {
    val height: Int = textLayout.height
    val maxRight: Float = (0 until textLayout.lineCount).maxOfOrNull { textLayout.getLineRight(it) } ?: 0f
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

