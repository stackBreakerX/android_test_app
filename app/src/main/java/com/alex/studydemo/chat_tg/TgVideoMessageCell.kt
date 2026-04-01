package com.alex.studydemo.chat_tg

import android.content.Context
import android.util.AttributeSet
import com.alex.studydemo.telegram.Theme

/**
 * 视频消息气泡 Cell
 * - 内容为 VideoContentView（示意播放按钮）
 * - 时间出现在气泡右下角
 */
class TgVideoMessageCell @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : BaseTgMessageCell(context, attrs) {

    override val bubbleDrawableType: Int = Theme.MessageDrawable.TYPE_MEDIA

    /** 视频内容视图 */
    private val videoContentView = VideoContentView(context)

    /** 时间锚点：右下角 */
    override val timeAnchor: TgTimeAnchor = TgTimeAnchorBottomRight
    /** 内容视图（用于 ViewGroup 布局） */
    override val contentView = videoContentView
    /** 内容视图的 TG 能力 */
    override val contentAsTg: TgContentView = videoContentView
}
