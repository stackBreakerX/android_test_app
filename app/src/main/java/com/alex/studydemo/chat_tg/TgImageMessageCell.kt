package com.alex.studydemo.chat_tg

import android.content.Context
import android.util.AttributeSet

class TgImageMessageCell @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : BaseTgMessageCell(context, attrs) {

    private val imageContentView = ImageContentView(context)

    override val timeAnchor: TgTimeAnchor = TgTimeAnchorBottomRight
    override val contentView = imageContentView
    override val contentAsTg: TgContentView = imageContentView
}

