package com.alex.studydemo.chat_tg

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class ImageContentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs), TgContentView {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFDDDDDD.toInt() }
    private var contentWidth = 0
    private var contentHeight = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec)
        contentWidth = maxWidth
        contentHeight = (maxWidth * 3f / 4f).toInt()
        setMeasuredDimension(contentWidth, contentHeight)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    override fun getContentWidth(): Int = contentWidth
    override fun getLastLineBaseline(): Float? = null
    override fun getLastLineWidth(): Int = 0
}

