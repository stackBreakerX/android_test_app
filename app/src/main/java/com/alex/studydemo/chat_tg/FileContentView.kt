package com.alex.studydemo.chat_tg

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class FileContentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs), TgContentView {

    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TgAndroidUtilities.dp(15f, resources.displayMetrics.density).toFloat()
        color = 0xFF121212.toInt()
    }
    private val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TgAndroidUtilities.dp(13f, resources.displayMetrics.density).toFloat()
        color = 0x7A121212.toInt()
    }
    private var contentWidth = 0
    private var contentHeight = 0

    var fileName: String = "document.pdf"
    var fileSize: String = "2.4 MB"

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec)
        val textWidth = max(
            titlePaint.measureText(fileName).toInt(),
            subPaint.measureText(fileSize).toInt()
        )
        contentWidth = maxWidth.coerceAtMost(textWidth + dp(44f))
        contentHeight = dp(52f)
        setMeasuredDimension(contentWidth, contentHeight)
    }

    override fun onDraw(canvas: Canvas) {
        val iconSize = dp(32f)
        canvas.drawRect(0f, 0f, iconSize.toFloat(), iconSize.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFBBDDEE.toInt()
        })
        val textX = iconSize + dp(8f)
        val titleY = dp(18f).toFloat()
        val subY = dp(38f).toFloat()
        canvas.drawText(fileName, textX.toFloat(), titleY, titlePaint)
        canvas.drawText(fileSize, textX.toFloat(), subY, subPaint)
    }

    override fun getContentWidth(): Int = contentWidth
    override fun getLastLineBaseline(): Float? = null
    override fun getLastLineWidth(): Int = 0

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()
}

