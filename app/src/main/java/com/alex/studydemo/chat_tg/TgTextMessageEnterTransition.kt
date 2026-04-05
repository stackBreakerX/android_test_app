package com.alex.studydemo.chat_tg

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import android.view.animation.PathInterpolator
import com.alex.studydemo.telegram.Theme
import kotlin.math.max
import kotlin.math.min

/**
 * 对齐 Telegram TextMessageEnterTransition 的职责：
 * 采样起点/终点几何信息，在顶层容器中统一绘制飞行中的气泡与文本。
 */
class TgTextMessageEnterTransition(
    private val rootView: View,
    private val container: TgSendTextOverlayView,
    private val sourceTextRect: RectF,
    private val sourceBubbleRect: RectF,
    private val targetTextRect: RectF,
    private val targetBubbleRect: RectF,
    private val text: String,
    private val multiline: Boolean,
    private val onProgress: (Float) -> Unit,
    private val onFinished: () -> Unit,
) : TgSendTextOverlayView.Transition {

    private val textPaint = TextPaint(TgTheme.chatMsgTextPaint)
    private val bubbleDrawable = TgMessageDrawable(rootView.context, true)
    private val drawBounds = Rect()
    private val textRect = RectF()
    private val bubbleRect = RectF()
    private val moveInterpolator = PathInterpolator(0.199f, 0.0106f, 0.2792f, 0.9103f)
    private val bubbleInterpolator = PathInterpolator(0.17f, 0f, 0.16f, 1f)
    private val animator = ValueAnimator.ofFloat(0f, 1f)

    private var progress = 0f
    private var cachedText = ""
    private var cachedWidth = -1
    private var cachedLayout: StaticLayout? = null

    private val bubblePaddingStart = dp(12f)
    private val bubblePaddingEnd = dp(16f)
    private val bubblePaddingTop = dp(8f)
    private val bubblePaddingBottom = dp(8f)

    init {
        bubbleDrawable.setBubbleType(Theme.MessageDrawable.STYLE_TAIL)
        animator.duration = 360L
        animator.interpolator = TgChatListItemAnimator.DEFAULT_INTERPOLATOR
        animator.addUpdateListener {
            progress = it.animatedFraction
            onProgress(progress)
            container.invalidate()
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                finish()
            }

            override fun onAnimationCancel(animation: Animator) {
                finish()
            }
        })
    }

    fun start() {
        onProgress(0f)
        container.addTransition(this)
        animator.start()
    }

    fun cancel() {
        animator.cancel()
    }

    private fun finish() {
        onProgress(1f)
        container.removeTransition(this)
        onFinished()
    }

    override fun onDraw(canvas: Canvas) {
        val moveProgress = moveInterpolator.getInterpolation(progress)
        val bubbleProgress = bubbleInterpolator.getInterpolation(progress)
        val fadeOut = when {
            progress <= 0.68f -> 1f
            progress >= 1f -> 0f
            else -> 1f - (progress - 0.68f) / 0.32f
        }

        val textWidth = max(1, lerp(sourceTextRect.width(), targetTextRect.width(), moveProgress).toInt())
        val layout = obtainLayout(text, textWidth)

        val centerX = quadratic(
            sourceTextRect.centerX(),
            sourceTextRect.centerX() + (targetTextRect.centerX() - sourceTextRect.centerX()) * 0.38f,
            targetTextRect.centerX(),
            moveProgress
        )
        val lift = if (multiline) dp(18f) else dp(26f)
        val controlY = min(sourceTextRect.centerY(), targetTextRect.centerY()) - lift
        val centerY = quadratic(
            sourceTextRect.centerY(),
            controlY,
            targetTextRect.centerY(),
            moveProgress
        )

        val textHeight = layout.height.toFloat()
        textRect.set(
            centerX - layout.width / 2f,
            centerY - textHeight / 2f,
            centerX + layout.width / 2f,
            centerY + textHeight / 2f
        )

        val bubbleCenterX = quadratic(
            sourceBubbleRect.centerX(),
            sourceBubbleRect.centerX() + (targetBubbleRect.centerX() - sourceBubbleRect.centerX()) * 0.42f,
            targetBubbleRect.centerX(),
            moveProgress
        )
        val bubbleLift = if (multiline) dp(20f) else dp(30f)
        val bubbleControlY = min(sourceBubbleRect.centerY(), targetBubbleRect.centerY()) - bubbleLift
        val bubbleCenterY = quadratic(
            sourceBubbleRect.centerY(),
            bubbleControlY,
            targetBubbleRect.centerY(),
            moveProgress
        )
        val bubbleWidth = lerp(sourceBubbleRect.width(), targetBubbleRect.width(), bubbleProgress)
        val bubbleHeight = lerp(sourceBubbleRect.height(), targetBubbleRect.height(), bubbleProgress)
        bubbleRect.set(
            bubbleCenterX - bubbleWidth / 2f,
            bubbleCenterY - bubbleHeight / 2f,
            bubbleCenterX + bubbleWidth / 2f,
            bubbleCenterY + bubbleHeight / 2f
        )

        val bubbleAlpha = (fadeOut * 235).toInt().coerceIn(0, 255)
        val textAlpha = (fadeOut * 255).toInt().coerceIn(0, 255)

        bubbleDrawable.alpha = bubbleAlpha
        drawBounds.set(
            bubbleRect.left.toInt(),
            bubbleRect.top.toInt(),
            bubbleRect.right.toInt(),
            bubbleRect.bottom.toInt()
        )
        bubbleDrawable.bounds = drawBounds
        bubbleDrawable.draw(canvas)

        canvas.save()
        canvas.clipRect(
            bubbleRect.left + bubblePaddingStart * 0.4f,
            bubbleRect.top + bubblePaddingTop * 0.4f,
            bubbleRect.right - bubblePaddingEnd * 0.35f,
            bubbleRect.bottom - bubblePaddingBottom * 0.4f
        )
        canvas.saveLayerAlpha(textRect.left, textRect.top, textRect.right, textRect.bottom, textAlpha)
        canvas.save()
        canvas.translate(textRect.left, textRect.top)
        layout.draw(canvas)
        canvas.restore()
        canvas.restore()
        canvas.restore()
    }

    private fun obtainLayout(text: String, width: Int): StaticLayout {
        if (cachedLayout != null && cachedText == text && cachedWidth == width) {
            return cachedLayout!!
        }
        cachedText = text
        cachedWidth = width
        cachedLayout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()
        return cachedLayout!!
    }

    private fun quadratic(start: Float, control: Float, end: Float, progress: Float): Float {
        val inv = 1f - progress
        return inv * inv * start + 2f * inv * progress * control + progress * progress * end
    }

    private fun lerp(start: Float, end: Float, fraction: Float): Float = start + (end - start) * fraction

    private fun dp(value: Float): Float = value * rootView.resources.displayMetrics.density
}
