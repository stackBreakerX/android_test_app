package com.alex.studydemo.chat_tg

import android.graphics.RectF
import android.text.TextPaint

interface TgTimeAnchor {
    fun getTimeX(bubble: RectF, timeWidth: Float, paddingEnd: Float): Float
    fun getTimeY(bubble: RectF, textBaselineY: Float?, paint: TextPaint, paddingBottom: Float): Float
}

object TgTimeAnchorInlineText : TgTimeAnchor {
    override fun getTimeX(bubble: RectF, timeWidth: Float, paddingEnd: Float): Float {
        return bubble.right - paddingEnd - timeWidth
    }

    override fun getTimeY(bubble: RectF, textBaselineY: Float?, paint: TextPaint, paddingBottom: Float): Float {
        // 文本内联：基线略低于末行
        val baseline = textBaselineY ?: bubble.bottom
        return baseline + TgAndroidUtilities.dpF(2f, TgTheme.density) - paint.fontMetrics.descent
    }
}

object TgTimeAnchorBottomRight : TgTimeAnchor {
    override fun getTimeX(bubble: RectF, timeWidth: Float, paddingEnd: Float): Float {
        return bubble.right - paddingEnd - timeWidth
    }

    override fun getTimeY(bubble: RectF, textBaselineY: Float?, paint: TextPaint, paddingBottom: Float): Float {
        return bubble.bottom - paddingBottom - paint.fontMetrics.descent
    }
}

