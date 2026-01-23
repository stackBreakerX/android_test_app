package com.alex.studydemo.chat_tg

import android.content.Context
import android.util.AttributeSet

class TgVideoMessageCell @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : BaseTgMessageCell(context, attrs) {

    private val videoContentView = VideoContentView(context)

    override val timeAnchor: TgTimeAnchor = TgTimeAnchorBottomRight
    override val contentView = videoContentView
    override val contentAsTg: TgContentView = videoContentView
}

