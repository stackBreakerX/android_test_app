package com.alex.studydemo.module_view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import kotlin.jvm.JvmOverloads
import androidx.appcompat.widget.AppCompatTextView

/**
 * @description
 */
class ConstructorClass @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val mOriginPaint: Paint

    private val mChangePaint: Paint

    private val bounds = Rect()

    init {
        mOriginPaint = getPaint(textColors.defaultColor)
        mChangePaint = getPaint(textColors.defaultColor)
    }


    private fun getPaint(color: Int): Paint {
        val paint = Paint()
        paint.color = color
        paint.isAntiAlias = true
        paint.isDither = true
        paint.textSize = textSize
        return paint
    }

    override fun onDraw(canvas: Canvas) {
        val textContent = text.toString()
        if (textContent.isBlank()) {
            return
        }
        mOriginPaint.getTextBounds(textContent, 0, textContent.length, bounds)

        val dx = width / 2 - bounds.width() / 2
        //获取基线 baseLine
        val fontMetrics = mOriginPaint.fontMetrics
        val dy = (fontMetrics.bottom - fontMetrics.top) / 2 - fontMetrics.bottom

        val baseLine = height / 2 + dy
        //绘制文字
        canvas?.drawText(textContent, dx.toFloat(), baseLine, mOriginPaint)
    }
}