package com.alex.studydemo.chat_tg

import android.content.Context
import android.graphics.Canvas
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View

/**
 * 引用/转发区域
 * - 位于用户名下方、内容视图上方
 * - 宽度受气泡内容宽度限制，高度随文本排版
 */
class ReplyView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val textPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f * resources.displayMetrics.scaledDensity
        color = 0xFF2E7D32.toInt()
    }
    private var text: CharSequence = ""
    private var layout: StaticLayout? = null
    private val padH = TgAndroidUtilities.dp(10f, resources.displayMetrics.density)
    private val padV = TgAndroidUtilities.dp(6f, resources.displayMetrics.density)

    fun setText(value: CharSequence) {
        text = value
        requestLayout()
        invalidate()
    }

    private fun buildLayout(maxW: Int) {
        layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, maxW - padH * 2)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxW = MeasureSpec.getSize(widthMeasureSpec)
        buildLayout(maxW)
        val l = layout ?: return super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(maxW, l.height + padV * 2)
    }

    override fun onDraw(canvas: Canvas) {
        val l = layout ?: return
        canvas.save()
        canvas.translate(padH.toFloat(), padV.toFloat())
        l.draw(canvas)
        canvas.restore()
    }
}

