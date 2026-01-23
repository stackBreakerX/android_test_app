package com.alex.tg.chat.cell.extra

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class LiveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x223377EE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3377EE.toInt()
        textSize = 12f * resources.displayMetrics.scaledDensity
    }
    private val rect = RectF()
    var reactions: List<String> = emptyList()
        set(value) { field = value; requestLayout(); invalidate() }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val baseW = reactions.joinToString(" ").length * (textPaint.textSize * 0.6f)
        val w = (baseW + 16f * resources.displayMetrics.density).toInt().coerceAtLeast((60f * resources.displayMetrics.density).toInt())
        val h = (24f * resources.displayMetrics.density).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, 12f, 12f, paint)
        val txt = if (reactions.isEmpty()) "" else reactions.joinToString(" ")
        val y = height / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(txt, 8f * resources.displayMetrics.density, y, textPaint)
    }
}

