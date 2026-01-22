package com.alex.tg.chat

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import com.alex.tg.chat.CubicBezierInterpolator
import com.alex.tg.chat.SimpleEnterTransition

class FlyTextEnterTransition(
    private val sourceView: EditText,
    private val targetView: View,
    private val text: String
) : SimpleEnterTransition() {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sourceView.textSize
    }
    private val bubbleTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 16f * (targetView.resources.displayMetrics.scaledDensity)
    }
    private val rect = RectF()
    private var srcLayout: StaticLayout? = null
    private var dstLayout: StaticLayout? = null

    private val interpolator = CubicBezierInterpolator.EaseOut
    private val duration = 320L
    private var startTime = 0L
    private var finished = false
    private val paddingH = 12f
    private val paddingV = 8f
    private val endRadius = 18f
    private val startRadius = 12f

    private val startTop = Color.parseColor("#5C9BFF")
    private val startBottom = Color.parseColor("#3D7EF9")
    private val endTop = Color.parseColor("#3C86FF")
    private val endBottom = Color.parseColor("#2B6EF7")

    private var toXOffset = 0f
    private var toYOffset = 0f
    private var fromX = 0f
    private var fromY = 0f
    private var toX = 0f
    private var toY = 0f

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
        dstLayout = StaticLayout.Builder.obtain(text, 0, text.length, bubbleTextPaint, maxTextWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()

        val containerLoc = IntArray(2)
        container?.getLocationOnScreen(containerLoc)
        val srcLoc = IntArray(2)
        sourceView.getLocationOnScreen(srcLoc)
        val dstLoc = IntArray(2)
        targetView.getLocationOnScreen(dstLoc)
        fromX = (srcLoc[0] - containerLoc[0]).toFloat() + sourceView.paddingLeft
        val lastLine = (srcLayoutObj?.lineCount ?: 1) - 1
        val srcLineBottom = if (srcLayoutObj != null && lastLine >= 0) {
//            srcLayoutObj.lineBottom(lastLine)
            srcLayout?.getLineBottom(lastLine) ?: 0
        } else {
            sourceView.height
        }
        val srcLineTop = if (srcLayoutObj != null && lastLine >= 0) {
            srcLayoutObj.getLineTop(lastLine)
        } else {
            (srcLineBottom.toFloat() - textPaint.textSize).toInt()
        }
        fromY = (srcLoc[1] - containerLoc[1]).toFloat() + srcLineTop - sourceView.scrollY

        toXOffset = paddingH
        toYOffset = paddingV
        toX = (dstLoc[0] - containerLoc[0]).toFloat() + toXOffset
        toY = (dstLoc[1] - containerLoc[1]).toFloat() + toYOffset
    }

    override fun onDraw(canvas: Canvas) {
        if (finished) return
        val t = ((System.currentTimeMillis() - startTime).toFloat() / duration).coerceAtMost(1f)
        val p = interpolator.getInterpolation(t)

        val curLeft = fromX + (toX - fromX) * p
        val curTop = fromY + (toY - fromY) * p

        val tl = dstLayout ?: return
        val w = tl.width + paddingH * 2
        val h = tl.height + paddingV * 2
        rect.set(curLeft, curTop, curLeft + w, curTop + h)

        val alpha = (255 * (1f - 0.2f * p)).toInt()
        textPaint.alpha = alpha
        val scale = 0.90f + 0.10f * p
        val radius = startRadius + (endRadius - startRadius) * p

        val topColor = blendArgb(startTop, endTop, p)
        val bottomColor = blendArgb(startBottom, endBottom, p)
        bgPaint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            topColor, bottomColor, Shader.TileMode.CLAMP
        ).also {
            bgPaint.alpha = alpha
        }

        val cx = rect.centerX()
        val cy = rect.centerY()
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(scale, scale)
        canvas.translate(-cx, -cy)
        canvas.drawRoundRect(rect, radius, radius, bgPaint)
        canvas.save()
        val src = srcLayout
        val dst = dstLayout
        val alphaProgress = p.coerceIn(0f, 1f)
        if (src != null) {
            canvas.save()
            canvas.translate(rect.left + paddingH, rect.top + paddingV)
            val oldAlpha = textPaint.alpha
            textPaint.alpha = (oldAlpha * (1f - alphaProgress)).toInt()
            src.draw(canvas)
            textPaint.alpha = oldAlpha
            canvas.restore()
        }
        if (dst != null) {
            canvas.save()
            canvas.translate(rect.left + paddingH, rect.top + paddingV)
            val oldAlpha = bubbleTextPaint.alpha
            bubbleTextPaint.alpha = (255 * alphaProgress).toInt()
            dst.draw(canvas)
            bubbleTextPaint.alpha = oldAlpha
            canvas.restore()
        }
        canvas.restore()
        canvas.restore()

        if (t >= 1f) {
            bgPaint.shader = null
            finished = true
        }
    }

    override fun isFinished(): Boolean = finished

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
