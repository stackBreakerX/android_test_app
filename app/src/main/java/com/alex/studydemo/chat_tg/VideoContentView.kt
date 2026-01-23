package com.alex.studydemo.chat_tg

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * 视频内容视图（占位示意）
 * - 以固定比例（16:9）测量内容尺寸，并绘制白色三角形作为播放按钮
 */
class VideoContentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs), TgContentView {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFCCCCCC.toInt() }
    private val playPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val playPath = Path()
    private var contentWidth = 0
    private var contentHeight = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec)
        contentWidth = maxWidth
        contentHeight = (maxWidth * 9f / 16f).toInt()
        setMeasuredDimension(contentWidth, contentHeight)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        val cx = width / 2f
        val cy = height / 2f
        val size = width * 0.18f
        playPath.reset()
        playPath.moveTo(cx - size * 0.3f, cy - size)
        playPath.lineTo(cx - size * 0.3f, cy + size)
        playPath.lineTo(cx + size, cy)
        playPath.close()
        canvas.drawPath(playPath, playPaint)
    }

    override fun getContentWidth(): Int = contentWidth
    override fun getLastLineBaseline(): Float? = null
    override fun getLastLineWidth(): Int = 0
}
