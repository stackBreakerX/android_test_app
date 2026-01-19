package com.alex.tg.chat

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.withTranslation
import kotlin.math.min

class MessageBubbleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2B6EF7")
    }
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 16f * resources.displayMetrics.scaledDensity
    }
    private var text: String = ""
    private var textLayout: android.text.StaticLayout? = null
    private val paddingH = (12f * resources.displayMetrics.density).toInt()
    private val paddingV = (8f * resources.displayMetrics.density).toInt()
    private val radius = 18f * resources.displayMetrics.density

    private var rippleAlpha = 0f
    private var rippleAnimRunning = false

    private var enterOffsetY = 0f
    private var enterAlpha = 1f

    fun setText(value: String) {
        text = value
        updateLayout()
        requestLayout()
        invalidate()
    }

    private fun updateLayout() {
        val width = (resources.displayMetrics.widthPixels * 0.7f).toInt()
        textLayout = android.text.StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val tl = textLayout ?: run {
            updateLayout(); textLayout
        } ?: return super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val maxLine = (0 until tl.lineCount).maxOfOrNull { i -> tl.getLineWidth(i) } ?: 0f
        val w = kotlin.math.ceil(maxLine.toDouble()).toInt() + paddingH * 2
        val h = tl.height + paddingV * 2
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val tl = textLayout ?: return
        canvas.save()
        canvas.withTranslation(0f, enterOffsetY) {
            bgPaint.alpha = (255 * enterAlpha).toInt()
            val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawRoundRect(rect, radius, radius, bgPaint)
            canvas.translate(paddingH.toFloat(), paddingV.toFloat())
            textPaint.alpha = (255 * enterAlpha).toInt()
            tl.draw(canvas)
            if (rippleAlpha > 0f) {
                ripplePaint.alpha = (80 * rippleAlpha).toInt()
                canvas.drawRoundRect(rect, radius, radius, ripplePaint)
            }
        }
        canvas.restore()
        if (rippleAnimRunning) {
            postInvalidateOnAnimation()
        }
    }

    fun toggleSelectedRipple() {
        if (rippleAnimRunning) return
        val start = if (rippleAlpha > 0.5f) 1f else 0f
        val end = if (start == 0f) 1f else 0f
        val duration = 180L
        rippleAnimRunning = true
        val startTime = System.currentTimeMillis()
        val interpolator = DecelerateInterpolator()
        post(object : Runnable {
            override fun run() {
                val t = min(1f, (System.currentTimeMillis() - startTime) / duration.toFloat())
                rippleAlpha = start + (end - start) * interpolator.getInterpolation(t)
                if (t < 1f) {
                    postInvalidateOnAnimation()
                    post(this)
                } else {
                    rippleAnimRunning = false
                    invalidate()
                }
            }
        })
    }

    fun setEnterOffsetAndAlpha(offsetY: Float, alpha: Float) {
        enterOffsetY = offsetY
        enterAlpha = alpha.coerceIn(0f, 1f)
        invalidate()
    }
}

