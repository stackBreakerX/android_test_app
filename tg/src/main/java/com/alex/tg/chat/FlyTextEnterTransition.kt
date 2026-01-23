package com.alex.tg.chat

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import android.widget.EditText

class FlyTextEnterTransition(
    private val sourceView: EditText,
    private val targetView: View,
    private val text: String
) : SimpleEnterTransition() {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2B6EF7")
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sourceView.textSize
    }
    private val rect = RectF()
    private var srcLayout: StaticLayout? = null
    private var dstLayout: StaticLayout? = null

    private val interpolator = CubicBezierInterpolator(0.1992, 0.0106, 0.2792, 0.9102)
    private val duration = 250L
    private var startTime = 0L
    private var finished = false
    private val paddingH = 12f
    private val paddingV = 8f
    private val endRadius = 18f
    private val startRadius = 12f

    private val startColor = Color.parseColor("#4C8DFB")
    private val endColor = Color.parseColor("#2B6EF7")

    private var fromX = 0f
    private var fromY = 0f
    private var toX = 0f
    private var toY = 0f
    private var fromW = 0f
    private var fromH = 0f
    private var toW = 0f
    private var toH = 0f
    private var targetAlphaBackup = 1f

    override fun onStart() {
        startTime = System.currentTimeMillis()
        val srcLayoutObj = sourceView.layout
        val srcW = (srcLayoutObj?.width ?: sourceView.width).coerceAtLeast(1)
        srcLayout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, srcW)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()
        val targetWidth = targetView.width.coerceAtLeast((sourceView.width * 0.8f).toInt())
        val maxTextWidth = (targetWidth - paddingH * 2).coerceAtLeast(1f).toInt()
        dstLayout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, maxTextWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()

        val containerLoc = IntArray(2)
        container?.getLocationOnScreen(containerLoc)
        val srcLoc = IntArray(2)
        sourceView.getLocationOnScreen(srcLoc)
        val dstLoc = IntArray(2)
        targetView.getLocationOnScreen(dstLoc)
        val lastLine = (srcLayoutObj?.lineCount ?: 1) - 1
        val lineLeft = if (srcLayoutObj != null && lastLine >= 0) srcLayoutObj.getLineLeft(lastLine) else 0f
        val lineRight = if (srcLayoutObj != null && lastLine >= 0) srcLayoutObj.getLineRight(lastLine) else sourceView.width.toFloat()
        val lineTop = if (srcLayoutObj != null && lastLine >= 0) srcLayoutObj.getLineTop(lastLine) else 0
        val lineBottom = if (srcLayoutObj != null && lastLine >= 0) srcLayoutObj.getLineBottom(lastLine) else sourceView.height
        val lineWidth = (lineRight - lineLeft).coerceAtLeast(1f)
        val lineHeight = (lineBottom - lineTop).coerceAtLeast(1)

        fromX = (srcLoc[0] - containerLoc[0]).toFloat() + sourceView.paddingLeft + lineLeft
        fromY = (srcLoc[1] - containerLoc[1]).toFloat() + lineTop - sourceView.scrollY
        fromW = lineWidth + paddingH * 2
        fromH = lineHeight + paddingV * 2

        toX = (dstLoc[0] - containerLoc[0]).toFloat()
        toY = (dstLoc[1] - containerLoc[1]).toFloat()
        toW = targetView.width.toFloat().coerceAtLeast(1f)
        toH = targetView.height.toFloat().coerceAtLeast(1f)

        targetAlphaBackup = targetView.alpha
        targetView.alpha = 0f
        sourceView.alpha = 0f
    }

    override fun onDraw(canvas: Canvas) {
        if (finished) return
        val t = ((System.currentTimeMillis() - startTime).toFloat() / duration).coerceAtMost(1f)
        val p = interpolator.getInterpolation(t)

        val curLeft = fromX + (toX - fromX) * p
        val curTop = fromY + (toY - fromY) * p
        val curW = fromW + (toW - fromW) * p
        val curH = fromH + (toH - fromH) * p
        rect.set(curLeft, curTop, curLeft + curW, curTop + curH)

        val radius = startRadius + (endRadius - startRadius) * p
        bgPaint.color = blendArgb(startColor, endColor, p)
        bgPaint.alpha = (255 * (0.92f + 0.08f * p)).toInt()
        canvas.drawRoundRect(rect, radius, radius, bgPaint)

        val dst = dstLayout ?: return
        val textAlpha = (255 * p).toInt()
        val oldAlpha = textPaint.alpha
        textPaint.alpha = textAlpha
        canvas.save()
        canvas.translate(rect.left + paddingH, rect.top + paddingV)
        dst.draw(canvas)
        canvas.restore()
        textPaint.alpha = oldAlpha

        if (t >= 1f) {
            finished = true
        }
    }

    override fun isFinished(): Boolean = finished

    override fun onFinished() {
        sourceView.alpha = 1f
        targetView.alpha = targetAlphaBackup
    }

    private fun blendArgb(c1: Int, c2: Int, f: Float): Int {
        val rf = f.coerceIn(0f, 1f)
        val a1 = (c1 ushr 24) and 0xFF
        val r1 = (c1 ushr 16) and 0xFF
        val g1 = (c1 ushr 8) and 0xFF
        val b1 = c1 and 0xFF
        val a2 = (c2 ushr 24) and 0xFF
        val r2 = (c2 ushr 16) and 0xFF
        val g2 = (c2 ushr 8) and 0xFF
        val b2 = c2 and 0xFF
        val a = (a1 + ((a2 - a1) * rf)).toInt()
        val r = (r1 + ((r2 - r1) * rf)).toInt()
        val g = (g1 + ((g2 - g1) * rf)).toInt()
        val b = (b1 + ((b2 - b1) * rf)).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
