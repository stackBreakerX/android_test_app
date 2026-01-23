package com.alex.studydemo.chat_tg

import android.graphics.Paint
import android.text.TextPaint

object TgTheme {
    private var initialized = false

    val chatMsgTextPaint: TextPaint by lazy {
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TgAndroidUtilities.dp(TgSharedConfig.fontSize.toFloat(), density).toFloat()
            color = 0xFF121212.toInt()
        }
    }

    val chatTimePaintOut: TextPaint by lazy {
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TgAndroidUtilities.dp(13f, density).toFloat()
            color = 0xFF00B1BA.toInt()
        }
    }

    val chatTimePaintIn: TextPaint by lazy {
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TgAndroidUtilities.dp(13f, density).toFloat()
            color = 0x7A121212.toInt()
        }
    }

    var density: Float = 1f

    fun init(density: Float) {
        this.density = density
        if (!initialized) {
            createCommonMessageResources()
            initialized = true
        }
    }

    private fun createCommonMessageResources() {
        // 只保留文本消息需要的画笔配置，尺寸与 TG 默认一致
        chatMsgTextPaint.textSize = TgAndroidUtilities.dp(TgSharedConfig.fontSize.toFloat(), density).toFloat()
        chatTimePaintOut.textSize = TgAndroidUtilities.dp(13f, density).toFloat()
        chatTimePaintIn.textSize = TgAndroidUtilities.dp(13f, density).toFloat()
        chatMsgTextPaint.color = 0xFF121212.toInt()
        chatTimePaintOut.color = 0xFF00B1BA.toInt()
        chatTimePaintIn.color = 0x7A121212.toInt()
    }
}

