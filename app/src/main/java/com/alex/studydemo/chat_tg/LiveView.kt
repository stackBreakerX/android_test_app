package com.alex.studydemo.chat_tg

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * 点赞/反应列表
 * - 锚定到气泡底部左侧
 * - 高度固定，宽度依据内容计算
 */
class LiveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x223377EE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3377EE.toInt()
        textSize = 12f * resources.displayMetrics.scaledDensity
    }
    private val rect = RectF()
    var reactions: List<String> = emptyList()
        set(value) { field = value; requestLayout(); invalidate() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val baseText = reactions.joinToString(" ")
        val textW = textPaint.measureText(baseText)
        val padH = TgAndroidUtilities.dpF(8f, resources.displayMetrics.density)
        val w = (textW + padH * 2).toInt().coerceAtLeast(TgAndroidUtilities.dp(60f, resources.displayMetrics.density))
        val h = TgAndroidUtilities.dp(24f, resources.displayMetrics.density)
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, TgAndroidUtilities.dpF(12f, resources.displayMetrics.density), TgAndroidUtilities.dpF(12f, resources.displayMetrics.density), bgPaint)
        val txt = reactions.joinToString(" ")
        val y = height / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        val x = TgAndroidUtilities.dpF(8f, resources.displayMetrics.density)
        canvas.drawText(txt, x, y, textPaint)
    }
}

