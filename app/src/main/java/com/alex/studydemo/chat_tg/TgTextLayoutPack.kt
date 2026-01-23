package com.alex.studydemo.chat_tg

data class TgTextLayoutPack(
    val blocks: List<TgTextLayoutBlock>,
    val contentWidth: Int,
    val height: Int,
    val lastLineWidth: Int,
    val lastLineBaseline: Float?,
    val reservedRight: Int,
    val inlineTimeWithText: Boolean,
    val computedForWidth: Int
)
