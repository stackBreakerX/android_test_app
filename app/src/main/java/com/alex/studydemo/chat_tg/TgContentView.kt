package com.alex.studydemo.chat_tg

import android.view.View

/**
 * TG 内容视图能力接口
 * - 为 BaseTgMessageCell 提供文本末行宽度/基线等信息，用于时间布局判断
 * - 非文本内容可返回默认值或 null
 */
interface TgContentView {
    // 内容真实宽度（不含气泡内边距）
    fun getContentWidth(): Int

    // 文本类内容可提供末行基线；图片类可返回 null
    fun getLastLineBaseline(): Float?

    // 文本类内容末行宽度，非文本可返回 0
    fun getLastLineWidth(): Int
}
