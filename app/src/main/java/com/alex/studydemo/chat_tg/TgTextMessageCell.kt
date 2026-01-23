package com.alex.studydemo.chat_tg

import android.content.Context
import android.util.AttributeSet
import com.alex.studydemo.chat_tg.TgAndroidUtilities.dp

/**
 * 文本消息气泡 Cell
 * - 内容为 TextContentView，支持“时间内联到末行”的策略
 * - 通过 BaseTgMessageCell 提供气泡绘制与时间布局能力
 */
class TgTextMessageCell @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : BaseTgMessageCell(context, attrs) {

    /** 文本内容视图（负责文本排版与绘制） */
    private val textContentView = TextContentView(context)

    /** 时间锚点：文本行内（靠近末行基线） */
    override val timeAnchor: TgTimeAnchor = TgTimeAnchorInlineText
    /** 文本类 Cell 采用“时间内联到末行”的布局规则 */
    override val inlineTimeWithText: Boolean = true
    /** 内容视图（用于 ViewGroup 布局） */
    override val contentView = textContentView
    /** 内容视图的 TG 能力（提供末行宽度/基线等信息） */
    override val contentAsTg: TgContentView = textContentView

    /** 绑定文本消息内容与时间/方向 */
    fun bindMessage(text: String, time: String, out: Boolean) {
        textContentView.setText(text)
        bindBase(time, out)
    }
}
