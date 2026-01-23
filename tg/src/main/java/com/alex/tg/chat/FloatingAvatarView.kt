package com.alex.tg.chat

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.min

class FloatingAvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5C9BFF")
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 14f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }
    private var label: String = "TG"

    fun setLabel(value: String) {
        label = value.trim().take(2).ifBlank { "TG" }
        invalidate()
    }

    fun animateToY(targetY: Float) {
        animate()
            .translationY(targetY)
            .setInterpolator(DecelerateInterpolator())
            .setDuration(180)
            .start()
    }

    override fun onDraw(canvas: Canvas) {
        val radius = min(width, height) / 2f
        canvas.drawCircle(width / 2f, height / 2f, radius, bgPaint)
        val fm = textPaint.fontMetrics
        val centerY = height / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(label, width / 2f, centerY, textPaint)
    }
}

