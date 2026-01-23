package com.alex.studydemo.chat_tg

import android.content.Context
import android.util.AttributeSet
import com.alex.studydemo.chat_tg.TgAndroidUtilities.dp

class TgTextMessageCell @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : BaseTgMessageCell(context, attrs) {

    private val textContentView = TextContentView(context)

    override val timeAnchor: TgTimeAnchor = TgTimeAnchorInlineText
    override val inlineTimeWithText: Boolean = true
    override val contentView = textContentView
    override val contentAsTg: TgContentView = textContentView

    fun bindMessage(text: String, time: String, out: Boolean) {
        textContentView.setText(text)
        bindBase(time, out)
    }
}