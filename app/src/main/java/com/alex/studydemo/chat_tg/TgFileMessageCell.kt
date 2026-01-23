package com.alex.studydemo.chat_tg

import android.content.Context
import android.util.AttributeSet

/**
 * 文件消息气泡 Cell
 * - 内容为 FileContentView，展示文件名与大小
 * - 时间出现在气泡右下角
 */
class TgFileMessageCell @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : BaseTgMessageCell(context, attrs) {

    /** 文件内容视图 */
    private val fileContentView = FileContentView(context)

    /** 时间锚点：右下角 */
    override val timeAnchor: TgTimeAnchor = TgTimeAnchorBottomRight
    /** 内容视图（用于 ViewGroup 布局） */
    override val contentView = fileContentView
    /** 内容视图的 TG 能力 */
    override val contentAsTg: TgContentView = fileContentView

    /** 设置文件名与大小，并触发重新布局 */
    fun setFile(name: String, size: String) {
        fileContentView.fileName = name
        fileContentView.fileSize = size
        requestLayout()
    }
}
