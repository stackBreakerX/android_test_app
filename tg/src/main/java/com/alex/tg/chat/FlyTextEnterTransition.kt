package com.alex.tg.chat

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import android.view.animation.DecelerateInterpolator
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
    private var layout: StaticLayout? = null

    private val interpolator = CubicBezierInterpolator.EaseOut
    private val duration = 320L
    private var startTime = 0L
    private var finished = false
    private val paddingH = 12f
    private val paddingV = 8f
    private val radius = 18f

    override fun onStart() {
        startTime = System.currentTimeMillis()
        val targetWidth = targetView.width.coerceAtLeast((sourceView.width * 0.8f).toInt())
        val maxTextWidth = (targetWidth - paddingH * 2).coerceAtLeast(1f).toInt()
        layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, maxTextWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()
    }

    override fun onDraw(canvas: Canvas) {
        if (finished) return
        val t = ((System.currentTimeMillis() - startTime).toFloat() / duration).coerceAtMost(1f)
        val p = interpolator.getInterpolation(t)

        val containerLoc = IntArray(2)
        container?.getLocationOnScreen(containerLoc)
        val srcLoc = IntArray(2)
        sourceView.getLocationOnScreen(srcLoc)
        val dstLoc = IntArray(2)
        targetView.getLocationOnScreen(dstLoc)

        val srcX = (srcLoc[0] - containerLoc[0]).toFloat() + sourceView.width * 0.5f
        val srcY = (srcLoc[1] - containerLoc[1]).toFloat() + sourceView.height * 0.5f
        val dstX = (dstLoc[0] - containerLoc[0]).toFloat() + targetView.width * 0.5f
        val dstY = (dstLoc[1] - containerLoc[1]).toFloat() + targetView.height * 0.5f

        val curX = srcX + (dstX - srcX) * p
        val curY = srcY + (dstY - srcY) * p

        val tl = layout ?: return
        val w = tl.width + paddingH * 2
        val h = tl.height + paddingV * 2
        rect.set(curX - w / 2f, curY - h / 2f, curX + w / 2f, curY + h / 2f)

        val alpha = (255 * (1f - 0.2f * p)).toInt()
        bgPaint.alpha = alpha
        textPaint.alpha = alpha
        val scale = 0.92f + 0.08f * p

        val cx = rect.centerX()
        val cy = rect.centerY()
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(scale, scale)
        canvas.translate(-cx, -cy)
        canvas.drawRoundRect(rect, radius, radius, bgPaint)
        canvas.save()
        canvas.translate(rect.left + paddingH, rect.top + paddingV)
        tl.draw(canvas)
        canvas.restore()
        canvas.restore()

        if (t >= 1f) {
            finished = true
        }
    }

    override fun isFinished(): Boolean = finished
}

