package com.alex.studydemo.chat_tg

import android.content.Context
import android.util.AttributeSet

/**
 * 图片消息气泡 Cell
 * - 内容为 ImageContentView（示意占位矩形）
 * - 时间出现在气泡右下角
 */
class TgImageMessageCell @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : BaseTgMessageCell(context, attrs) {

    /** 图片内容视图 */
    private val imageContentView = ImageContentView(context)

    /** 时间锚点：右下角 */
    override val timeAnchor: TgTimeAnchor = TgTimeAnchorBottomRight
    /** 内容视图（用于 ViewGroup 布局） */
    override val contentView = imageContentView
    /** 内容视图的 TG 能力 */
    override val contentAsTg: TgContentView = imageContentView
}
