package com.alex.tg.chat.bubble

import android.graphics.*

class BubbleDrawable(
    private val color: Int,
    private val radius: Float,
    private val shadow: Boolean = true
) {
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = this@BubbleDrawable.color }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
        alpha = 45
    }
    private val rect = RectF()

    fun draw(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        rect.set(left, top, right, bottom)
        if (shadow) {
            canvas.save()
            canvas.translate(0f, 2f)
            canvas.drawRoundRect(rect, radius, radius, shadowPaint)
            canvas.restore()
        }
        canvas.drawRoundRect(rect, radius, radius, bgPaint)
    }
}

