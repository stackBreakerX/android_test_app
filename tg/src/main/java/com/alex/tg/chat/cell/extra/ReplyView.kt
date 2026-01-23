package com.alex.tg.chat.cell.extra

import android.content.Context
import android.graphics.Canvas
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View

class ReplyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val textPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f * resources.displayMetrics.scaledDensity
        color = 0xFF3E7A2A.toInt()
    }
    var text: CharSequence = ""
        set(value) { field = value; buildLayout(); requestLayout(); invalidate() }
    private var layout: StaticLayout? = null
    private val paddingH = (10f * resources.displayMetrics.density).toInt()
    private val paddingV = (6f * resources.displayMetrics.density).toInt()

    private fun buildLayout() {
        val maxW = (resources.displayMetrics.widthPixels * 0.65f).toInt()
        layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, maxW)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val l = layout ?: run { buildLayout(); layout } ?: return super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(l.width + paddingH * 2, l.height + paddingV * 2)
    }

    override fun onDraw(canvas: Canvas) {
        val l = layout ?: return
        canvas.save()
        canvas.translate(paddingH.toFloat(), paddingV.toFloat())
        l.draw(canvas)
        canvas.restore()
    }
}

