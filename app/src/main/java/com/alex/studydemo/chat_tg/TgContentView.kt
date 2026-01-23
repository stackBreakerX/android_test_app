package com.alex.studydemo.chat_tg

import android.view.View

interface TgContentView {
    // 内容真实宽度（不含气泡内边距）
    fun getContentWidth(): Int

    // 文本类内容可提供末行基线；图片类可返回 null
    fun getLastLineBaseline(): Float?

    // 文本类内容末行宽度，非文本可返回 0
    fun getLastLineWidth(): Int
}

