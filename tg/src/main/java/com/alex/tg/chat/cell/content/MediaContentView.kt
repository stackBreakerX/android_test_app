package com.alex.tg.chat.cell.content

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class MediaContentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var mediaWidth: Int = 0
    var mediaHeight: Int = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY }
    private val rect = RectF()

    fun getContentBounds(): RectF = RectF(0f, 0f, width.toFloat(), height.toFloat())

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxW = (resources.displayMetrics.widthPixels * 0.65f).toInt()
        val scale = if (mediaWidth > 0 && mediaHeight > 0) {
            (maxW.toFloat() / mediaWidth).coerceAtMost(1.0f)
        } else 1f
        val w = (mediaWidth * scale).toInt().coerceAtLeast((maxW * 0.6f).toInt())
        val h = (mediaHeight * scale).toInt().coerceAtLeast((maxW * 0.4f).toInt())
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, 12f, 12f, paint)
    }
}

