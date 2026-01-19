package com.alex.tg.chat

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.DecelerateInterpolator

class TextMessageEnterTransition(private val targetView: View) : SimpleEnterTransition() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
    }
    private val rect = RectF()
    private val duration = 180L
    private val interpolator = DecelerateInterpolator()
    private var startTime = 0L
    private var finished = false

    override fun onStart() {
        startTime = System.currentTimeMillis()
    }

    override fun onDraw(canvas: Canvas) {
        if (finished) return
        val t = ((System.currentTimeMillis() - startTime).toFloat() / duration).coerceAtMost(1f)
        val p = 1f - interpolator.getInterpolation(t)
        val location = IntArray(2)
        targetView.getLocationOnScreen(location)
        val containerLocation = IntArray(2)
        container?.getLocationOnScreen(containerLocation)
        val left = (location[0] - containerLocation[0]).toFloat()
        val top = (location[1] - containerLocation[1]).toFloat()
        rect.set(left, top, left + targetView.width, top + targetView.height)
        paint.alpha = (50 * p).toInt()
        canvas.drawRoundRect(rect, 18f, 18f, paint)
        if (t >= 1f) {
            finished = true
        }
    }

    override fun isFinished(): Boolean = finished
}

