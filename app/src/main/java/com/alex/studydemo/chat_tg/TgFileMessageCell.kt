package com.alex.studydemo.chat_tg

import android.content.Context
import android.util.AttributeSet

class TgFileMessageCell @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : BaseTgMessageCell(context, attrs) {

    private val fileContentView = FileContentView(context)

    override val timeAnchor: TgTimeAnchor = TgTimeAnchorBottomRight
    override val contentView = fileContentView
    override val contentAsTg: TgContentView = fileContentView

    fun setFile(name: String, size: String) {
        fileContentView.fileName = name
        fileContentView.fileSize = size
        requestLayout()
    }
}

